import { randomUUID } from 'node:crypto'
import { existsSync } from 'node:fs'
import { MinecraftFolder, Version } from '@xmcl/core'
import {
  createFabricInstallWorkflow,
  createModernForgeInstallWorkflow,
  createQuiltInstallWorkflow,
  resolveAssetMetadataInstallManifest,
  resolveAssetObjectInstallFiles,
  resolveForgeArtifactVersion,
  resolveForgeInstallerFile,
  resolveLibraryInstallFiles,
  resolveMinecraftJarInstallFile,
  resolveNeoForgedInstallerFile
} from '@xmcl/installer'
import { AppError } from '@shared/errors'
import type { InstallTask } from '@shared/types'
import type { EventSink } from '../../utils/events'
import { JsonStore } from '../../utils/json-store'
import { DownloadController, filesPlan, runManifest, runWorkflow } from '../minecraft/downloader'
import { gameDirs } from '../minecraft/paths'
import { parseVersionKey, versionKey, type VersionSpec } from '../minecraft/version-key'
import type { VanillaVersionInfo } from '../versions/remote-version-service'
import type { InstallService, JavaRequirement, JavaService, ProgressCallback } from '../types'

const TICK_MS = 250

/** Kurulum hattının ihtiyaç duyduğu sürüm metadata'sı (RemoteVersionService sağlar). */
export interface VersionMetadataSource {
  getVanillaInfo(minecraftVersion: string): Promise<VanillaVersionInfo>
  getJavaRequirement(minecraftVersion: string): Promise<JavaRequirement>
}

interface InstalledEntry {
  /** versions/<versionId>/<versionId>.json */
  versionId: string
  /** Son tam doğrulamanın zamanı; sonraki doğrulamalarda daha eski mtime'lı dosyalar SHA1'siz geçer. */
  validatedAt: number
}

type InstalledMap = Record<string, InstalledEntry>

interface Job {
  ctl: DownloadController
  promise: Promise<string>
}

function sanitizeInstalled(raw: unknown): InstalledMap {
  // Faz 1 mock'u düz anahtar listesi tutuyordu; o kayıtlar gerçek kurulum değildir.
  if (typeof raw !== 'object' || raw === null || Array.isArray(raw)) return {}
  const out: InstalledMap = {}
  for (const [key, value] of Object.entries(raw as Record<string, unknown>)) {
    const v = value as Partial<InstalledEntry> | null
    if (v && typeof v.versionId === 'string' && typeof v.validatedAt === 'number') {
      out[key] = { versionId: v.versionId, validatedAt: v.validatedAt }
    }
  }
  return out
}

/**
 * Gerçek kurulum: @xmcl/installer manifest'leri ile dosya bazlı devam edebilen,
 * SHA1 doğrulamalı indirme (bkz. minecraft/downloader.ts).
 *
 * Akış: vanilla version JSON (manifest v2 SHA1'i ile) → loader profili (Fabric/Quilt
 * JSON; Forge/NeoForge installer + işlemciler) → asset index → istemci jar'ı,
 * kütüphaneler ve asset nesneleri.
 */
export class MinecraftInstallService implements InstallService {
  private readonly tasks = new Map<string, InstallTask>()
  private readonly jobs = new Map<string, Job>()
  private readonly installed = new JsonStore<InstalledMap>('installed-versions.json', {}, sanitizeInstalled)

  constructor(
    private readonly events: EventSink,
    private readonly meta: VersionMetadataSource,
    private readonly java: JavaService
  ) {}

  async start(versionKey: string): Promise<InstallTask> {
    const spec = parseVersionKey(versionKey)
    const existing = this.activeTaskFor(versionKey)
    if (existing) return existing

    const task: InstallTask = {
      id: randomUUID(),
      versionKey,
      state: 'downloading',
      downloadedBytes: 0,
      totalBytes: 0,
      bytesPerSecond: 0
    }
    this.tasks.set(task.id, task)
    this.emit(task)

    const ctl = new DownloadController()
    const promise = this.runVisible(task, spec, ctl)
    // Hata durumu görev üzerinden bildirilir; burada yakalanmazsa işlenmemiş reddetme olur.
    promise.catch(() => undefined)
    this.jobs.set(task.id, { ctl, promise })
    return task
  }

  async pause(taskId: string): Promise<InstallTask> {
    const task = this.getTask(taskId)
    if (task.state === 'downloading') {
      this.jobs.get(taskId)?.ctl.pause()
      this.update(task, { state: 'paused', bytesPerSecond: 0 })
    }
    return task
  }

  async resume(taskId: string): Promise<InstallTask> {
    const task = this.getTask(taskId)
    if (task.state === 'paused') {
      this.jobs.get(taskId)?.ctl.resume()
      this.update(task, { state: 'downloading' })
    }
    return task
  }

  async cancel(taskId: string): Promise<InstallTask> {
    const task = this.getTask(taskId)
    if (task.state === 'downloading' || task.state === 'paused' || task.state === 'queued') {
      this.jobs.get(taskId)?.ctl.cancel()
      this.update(task, { state: 'cancelled', bytesPerSecond: 0 })
    }
    return task
  }

  async listTasks(): Promise<InstallTask[]> {
    return [...this.tasks.values()]
  }

  async listInstalled(): Promise<string[]> {
    const installed = await this.installed.read()
    const folder = MinecraftFolder.from(gameDirs.root)
    return Object.entries(installed)
      .filter(([, entry]) => existsSync(folder.getVersionJson(entry.versionId)))
      .map(([key]) => key)
  }

  async ensureInstalled(versionKey: string, onProgress: ProgressCallback): Promise<string> {
    const spec = parseVersionKey(versionKey)

    // Sürümler ekranında zaten indiriliyorsa onu bekle (duraklatılmışsa devam ettir).
    const active = this.activeTaskFor(versionKey)
    const job = active && this.jobs.get(active.id)
    if (active && job) {
      if (active.state === 'paused') await this.resume(active.id)
      const poll = setInterval(() => {
        if (active.totalBytes > 0) onProgress(Math.min(1, active.downloadedBytes / active.totalBytes))
      }, TICK_MS)
      try {
        return await job.promise
      } catch (err) {
        throw toDownloadError(err, versionKey)
      } finally {
        clearInterval(poll)
      }
    }

    // Değilse görünmez bir doğrulama/kurulum işi: kuruluysa yalnızca doğrular, eksikleri tamamlar.
    const ctl = new DownloadController()
    let best = 0
    const poll = setInterval(() => {
      const total = ctl.totalBytes
      if (total > 0) best = Math.max(best, ctl.downloadedBytes / total)
      onProgress(Math.min(0.99, best))
    }, TICK_MS)
    try {
      const previous = (await this.installed.read())[versionKey]
      const versionId = await this.install(spec, ctl, previous?.validatedAt)
      await this.markInstalled(versionKey, versionId)
      onProgress(1)
      return versionId
    } catch (err) {
      throw toDownloadError(err, versionKey)
    } finally {
      clearInterval(poll)
    }
  }

  // ── iç işleyiş ────────────────────────────────────────────────────────────

  private activeTaskFor(versionKey: string): InstallTask | undefined {
    return [...this.tasks.values()].find(
      (t) => t.versionKey === versionKey && (t.state === 'downloading' || t.state === 'paused' || t.state === 'queued')
    )
  }

  private async runVisible(task: InstallTask, spec: VersionSpec, ctl: DownloadController): Promise<string> {
    let lastTransferred = 0
    let lastAt = Date.now()
    let speed = 0
    const ticker = setInterval(() => {
      if (task.state !== 'downloading') return
      const now = Date.now()
      const instant = ((ctl.transferred - lastTransferred) * 1000) / Math.max(1, now - lastAt)
      lastTransferred = ctl.transferred
      lastAt = now
      speed = speed === 0 ? instant : speed * 0.7 + instant * 0.3
      this.update(task, {
        downloadedBytes: ctl.downloadedBytes,
        totalBytes: ctl.totalBytes,
        bytesPerSecond: Math.round(speed)
      })
    }, TICK_MS)

    try {
      const previous = (await this.installed.read())[task.versionKey]
      const versionId = await this.install(spec, ctl, previous?.validatedAt)
      await this.markInstalled(task.versionKey, versionId)
      const total = Math.max(ctl.totalBytes, ctl.downloadedBytes)
      this.update(task, { state: 'completed', downloadedBytes: total, totalBytes: total, bytesPerSecond: 0 })
      return versionId
    } catch (err) {
      if (!ctl.cancelled && task.state !== 'cancelled') {
        this.update(task, { state: 'failed', bytesPerSecond: 0, error: toDownloadError(err, task.versionKey).toJSON() })
      }
      throw err
    } finally {
      clearInterval(ticker)
      this.jobs.delete(task.id)
    }
  }

  /** Sürümü kurar/doğrular; kurulu sürümün id'sini döndürür. */
  private async install(spec: VersionSpec, ctl: DownloadController, validatedAt?: number): Promise<string> {
    const folder = MinecraftFolder.from(gameDirs.root)
    const mc = spec.minecraftVersion
    const lv = spec.loaderVersion ?? ''
    const vanilla = await this.meta.getVanillaInfo(mc)

    // 1) Vanilla version JSON — manifest v2'deki SHA1 ile doğrulanır.
    await runManifest(
      filesPlan('version-json', [
        {
          path: folder.getVersionJson(vanilla.id),
          urls: [vanilla.url],
          checksum: vanilla.sha1 ? { algorithm: 'sha1', value: vanilla.sha1 } : undefined,
          validator: 'json'
        }
      ]),
      ctl
    )

    // 2) Loader profili
    let versionId = vanilla.id
    switch (spec.loader) {
      case 'vanilla':
        break
      case 'fabric': {
        versionId = `${mc}-fabric${lv}`
        if (!existsSync(folder.getVersionJson(versionId))) {
          versionId = await runWorkflow(createFabricInstallWorkflow({ minecraft: folder, minecraftVersion: mc, version: lv }), ctl)
        }
        break
      }
      case 'quilt': {
        versionId = `${mc}-quilt${lv}`
        if (!existsSync(folder.getVersionJson(versionId))) {
          versionId = await runWorkflow(createQuiltInstallWorkflow({ minecraft: folder, minecraftVersion: mc, version: lv }), ctl)
        }
        break
      }
      case 'forge':
      case 'neoforge': {
        // İşlemciler vanilla jar'ı ve kütüphanelerini girdi olarak kullanır.
        await this.installGameFiles(vanilla.id, folder, ctl, validatedAt)
        const previous = (await this.installed.read())[versionKey(spec.loader, mc, spec.loaderVersion)]
        versionId =
          previous && existsSync(folder.getVersionJson(previous.versionId))
            ? previous.versionId
            : await this.installForge(spec, folder, ctl)
        break
      }
    }

    // 3) Asset index, istemci jar'ı, kütüphaneler (native'ler dahil) ve asset nesneleri
    await this.installGameFiles(versionId, folder, ctl, validatedAt)
    return versionId
  }

  private async installGameFiles(
    versionId: string,
    folder: MinecraftFolder,
    ctl: DownloadController,
    validatedAt?: number
  ): Promise<void> {
    const options = { timestamp: validatedAt }
    let version
    try {
      version = await Version.parse(folder, versionId)
    } catch (err) {
      throw new AppError('DOWNLOAD_FAILED', `${versionId} sürüm dosyası okunamadı: ${(err as Error).message}`)
    }
    await runManifest(resolveAssetMetadataInstallManifest(version, folder, options), ctl)
    const assets = await resolveAssetObjectInstallFiles(version, folder, options)
    await runManifest(
      filesPlan('game-files', [
        resolveMinecraftJarInstallFile(version, options),
        ...resolveLibraryInstallFiles(version.libraries, folder, options),
        ...assets
      ]),
      ctl
    )
  }

  private async installForge(spec: VersionSpec, folder: MinecraftFolder, ctl: DownloadController): Promise<string> {
    const mc = spec.minecraftVersion
    const lv = spec.loaderVersion ?? ''
    // Forge/NeoForge installer işlemcileri Java gerektirir: sürümün kendi Java'sı kullanılır.
    const java = await this.java.ensure(await this.meta.getJavaRequirement(mc), () => undefined)
    const neo = spec.loader === 'neoforge'
    const artifactVersion = neo ? lv : resolveForgeArtifactVersion(mc, lv)
    const installer = neo
      ? (await resolveNeoForgedInstallerFile('neoforge', lv, folder, {})).file
      : resolveForgeInstallerFile(artifactVersion, undefined, folder, {}).file
    const result = await runWorkflow(
      createModernForgeInstallWorkflow({
        id: spec.loader,
        minecraft: folder,
        minecraftVersion: mc,
        installer,
        artifactVersion,
        java: java.path,
        installOptions: {}
      }),
      ctl
    )
    return result.version
  }

  private getTask(taskId: string): InstallTask {
    const task = this.tasks.get(taskId)
    if (!task) throw new AppError('DOWNLOAD_FAILED', `Görev bulunamadı: ${taskId}`)
    return task
  }

  private update(task: InstallTask, patch: Partial<InstallTask>): void {
    Object.assign(task, patch)
    this.emit(task)
  }

  private emit(task: InstallTask): void {
    this.events.emit('install:progress', { ...task })
  }

  private async markInstalled(key: string, versionId: string): Promise<void> {
    const installed = await this.installed.read()
    await this.installed.write({ ...installed, [key]: { versionId, validatedAt: Date.now() } })
  }
}

function toDownloadError(err: unknown, key: string): AppError {
  if (err instanceof AppError) return err
  const detail =
    err instanceof AggregateError
      ? `${err.message}: ${err.errors
          .slice(0, 3)
          .map((e) => (e as Error).message)
          .join('; ')}`
      : (err as Error)?.message
  return new AppError('DOWNLOAD_FAILED', detail, { version: key })
}
