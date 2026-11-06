import { createHash } from 'node:crypto'
import { existsSync } from 'node:fs'
import { mkdir, readFile, rename, rm, writeFile } from 'node:fs/promises'
import { dirname, join, posix } from 'node:path'
import { app } from 'electron'
import { checksum } from '@xmcl/core'
import type { InstallFile } from '@xmcl/installer'
import { open, readEntry, walkEntriesGenerator } from '@xmcl/unzip'
import { AppError } from '@shared/errors'
import type { ModpackSyncResult, ServerProfile } from '@shared/types'
import { DownloadController, filesPlan, runManifest } from '../minecraft/downloader'
import { instanceDir } from '../minecraft/paths'
import type { ModpackService, ProgressCallback } from '../types'

/** Modrinth .mrpack biçimi (modrinth.index.json, formatVersion 1). */
interface MrpackFile {
  path: string
  hashes: { sha1?: string; sha512?: string }
  env?: { client?: 'required' | 'optional' | 'unsupported'; server?: string }
  downloads: string[]
  fileSize?: number
}

interface MrpackIndex {
  formatVersion: number
  game: string
  versionId?: string
  name?: string
  files: MrpackFile[]
  dependencies?: Record<string, string>
}

/** Launcher'ın bu instance'a yerleştirdiği dosyalar (yalnızca bunlar kaldırılabilir). */
interface SyncState {
  pack?: string
  files: Record<string, string>
}

const STATE_FILE = join('.cevher', 'modpack.json')

/**
 * Yolu dosya sisteminin göreceği biçime indirger: Windows/macOS büyük-küçük harf ayırmaz ve NTFS
 * bölüm sonundaki nokta/boşlukları atar (".CEVHER." ile ".cevher" aynı klasördür).
 */
function canonical(rel: string): string {
  return rel
    .split('/')
    .map((seg) => seg.toLowerCase().replace(/[. ]+$/, ''))
    .join('/')
}

/** Kullanıcı ayarları: ilk kurulumda override'dan gelir, sonra asla ezilmez veya silinmez. */
function isProtected(rel: string): boolean {
  const c = canonical(rel)
  return c === 'options.txt' || c === 'config' || c.startsWith('config/')
}

/** Paket içinden gelen yolu doğrular; instance dışına çıkan yolları reddeder. */
function safeRelative(path: string): string | undefined {
  const normalized = posix.normalize(path.replaceAll('\\', '/'))
  if (
    normalized === '.' ||
    normalized.startsWith('/') ||
    normalized.startsWith('../') ||
    normalized === '..' ||
    /^[A-Za-z]:/.test(normalized) ||
    canonical(normalized).split('/').includes('.cevher')
  ) {
    return undefined
  }
  return normalized
}

const sha1Of = (data: Buffer): string => createHash('sha1').update(data).digest('hex')

async function fileSha1(path: string): Promise<string | undefined> {
  return existsSync(path) ? ((await checksum(path, 'sha1').catch(() => undefined)) as string | undefined) : undefined
}

/**
 * Faz 3: sunucu profilindeki Modrinth .mrpack'i sunucunun instance klasörüne
 * senkronlar.
 * - Paketteki dosyalar SHA1 doğrulamalı indirilir (ortak, devam edebilen indirici).
 * - `overrides/` ve `client-overrides/` çıkarılır; `options.txt` ve `config/` yalnızca
 *   yoksa yazılır (oyuncunun ayarları korunur).
 * - Daha önce launcher'ın koyduğu ama artık pakette olmayan dosyalar kaldırılır;
 *   oyuncunun kendi eklediği dosyalara dokunulmaz.
 * - Ağ yoksa son indirilen paket kullanılır.
 */
export class MrpackModpackService implements ModpackService {
  async sync(server: ServerProfile, onProgress: ProgressCallback): Promise<ModpackSyncResult> {
    try {
      return await this.doSync(server, onProgress)
    } catch (err) {
      if (err instanceof AppError && err.code === 'MODPACK_SYNC_FAILED') throw err
      throw new AppError('MODPACK_SYNC_FAILED', (err as Error)?.message ?? String(err), { server: server.id })
    }
  }

  private async doSync(server: ServerProfile, onProgress: ProgressCallback): Promise<ModpackSyncResult> {
    const root = instanceDir(server.id)
    const previous = await this.readState(root)
    const result: ModpackSyncResult = { serverId: server.id, added: 0, updated: 0, removed: 0, unchanged: 0 }

    if (!server.modpackManifestUrl) {
      // Paket kaldırıldıysa launcher'ın koyduğu dosyaları temizle.
      result.removed = await this.removeStale(root, previous, new Set())
      if (result.removed > 0) await this.writeState(root, { files: {} })
      onProgress(1)
      return result
    }

    // 1) Paketi indir (her seferinde güncel sürüm; çevrimdışıysa önbellek)
    onProgress(0)
    const packPath = await this.fetchPack(server)
    onProgress(0.1)

    const zip = await open(packPath, { lazyEntries: true, autoClose: false })
    try {
      const entries = []
      for await (const entry of walkEntriesGenerator(zip)) entries.push(entry)
      const indexEntry = entries.find((e) => e.fileName === 'modrinth.index.json')
      if (!indexEntry) throw new AppError('MODPACK_SYNC_FAILED', 'modrinth.index.json yok', { server: server.id })
      const index = JSON.parse((await readEntry(zip, indexEntry)).toString('utf8')) as MrpackIndex
      this.validateIndex(index, server)

      // 2) Paket dosyaları (modlar, resource pack'ler…)
      const next: Record<string, string> = {}
      const downloads: InstallFile[] = []
      for (const file of index.files) {
        if (file.env?.client === 'unsupported') continue
        const rel = safeRelative(file.path)
        const sha1 = file.hashes.sha1?.toLowerCase()
        if (!rel || !sha1) throw new AppError('MODPACK_SYNC_FAILED', `Geçersiz dosya girdisi: ${file.path}`, { server: server.id })
        const urls = file.downloads.filter((u) => /^https:\/\//i.test(u))
        if (urls.length === 0) throw new AppError('MODPACK_SYNC_FAILED', `İndirme adresi yok: ${rel}`, { server: server.id })

        next[rel] = sha1
        const target = join(root, rel)
        if ((await fileSha1(target)) === sha1) {
          result.unchanged++
          continue
        }
        if (previous.files[rel]) result.updated++
        else result.added++
        downloads.push({ path: target, urls, size: file.fileSize, checksum: { algorithm: 'sha1', value: sha1 } })
      }

      if (downloads.length > 0) {
        const ctl = new DownloadController()
        const poll = setInterval(() => {
          if (ctl.totalBytes > 0) onProgress(0.1 + 0.75 * Math.min(1, ctl.downloadedBytes / ctl.totalBytes))
        }, 250)
        try {
          await runManifest(filesPlan('modpack-files', downloads), ctl)
        } finally {
          clearInterval(poll)
        }
      }
      onProgress(0.85)

      // 3) Override'lar (client-overrides, overrides'ın üzerine yazar)
      const overrides = new Map<string, (typeof entries)[number]>()
      for (const prefix of ['overrides/', 'client-overrides/']) {
        for (const entry of entries) {
          if (!entry.fileName.startsWith(prefix) || entry.fileName.endsWith('/')) continue
          const rel = safeRelative(entry.fileName.slice(prefix.length))
          if (rel) overrides.set(rel, entry)
        }
      }
      for (const [rel, entry] of overrides) {
        const target = join(root, rel)
        if (isProtected(rel)) {
          if (!existsSync(target)) await this.writeFileAtomic(target, await readEntry(zip, entry))
          continue
        }
        const data = await readEntry(zip, entry)
        const sha1 = sha1Of(data)
        next[rel] = sha1
        const current = await fileSha1(target)
        if (current === sha1) {
          result.unchanged++
          continue
        }
        if (previous.files[rel]) result.updated++
        else result.added++
        await this.writeFileAtomic(target, data)
      }

      // 4) Artık pakette olmayan, launcher'ın koyduğu dosyaları kaldır
      result.removed = await this.removeStale(root, previous, new Set(Object.keys(next)))
      await this.writeState(root, { pack: index.versionId, files: next })
      onProgress(1)
      return result
    } finally {
      zip.close()
    }
  }

  private validateIndex(index: MrpackIndex, server: ServerProfile): void {
    if (index.formatVersion !== 1 || index.game !== 'minecraft' || !Array.isArray(index.files)) {
      throw new AppError('MODPACK_SYNC_FAILED', 'Desteklenmeyen .mrpack biçimi', { server: server.id })
    }
    const mc = index.dependencies?.minecraft
    if (mc && mc !== server.minecraftVersion) {
      throw new AppError(
        'MODPACK_SYNC_FAILED',
        `Paket Minecraft ${mc} için, sunucu profili ${server.minecraftVersion}`,
        { server: server.id }
      )
    }
  }

  /** Paketi userData/modpacks/<id>.mrpack olarak indirir; ağ yoksa önceki kopyayı kullanır. */
  private async fetchPack(server: ServerProfile): Promise<string> {
    const path = join(app.getPath('userData'), 'modpacks', `${server.id}.mrpack`)
    const ctl = new DownloadController()
    try {
      await runManifest(
        filesPlan('modpack-archive', [{ path, urls: [server.modpackManifestUrl!], validator: 'zip', replace: true }]),
        ctl
      )
    } catch (err) {
      if (!existsSync(path)) {
        throw new AppError('MODPACK_SYNC_FAILED', `Paket indirilemedi: ${(err as Error).message}`, { server: server.id })
      }
    }
    // Config'te özet sabitlenmişse (modpackSha256) indirilen ya da önbellekteki paket onunla eşleşmeli.
    if (server.modpackSha256) {
      const actual = createHash('sha256').update(await readFile(path)).digest('hex')
      if (actual !== server.modpackSha256) {
        await rm(path, { force: true })
        throw new AppError('MODPACK_SYNC_FAILED', 'Paket özeti (SHA-256) config ile eşleşmiyor', { server: server.id })
      }
    }
    return path
  }

  private async removeStale(root: string, previous: SyncState, keep: Set<string>): Promise<number> {
    let removed = 0
    for (const rel of Object.keys(previous.files)) {
      if (keep.has(rel) || isProtected(rel) || !safeRelative(rel)) continue
      const target = join(root, rel)
      if (existsSync(target)) {
        await rm(target, { force: true })
        removed++
      }
    }
    return removed
  }

  private async readState(root: string): Promise<SyncState> {
    try {
      const raw = JSON.parse(await readFile(join(root, STATE_FILE), 'utf8')) as Partial<SyncState>
      return { pack: raw.pack, files: typeof raw.files === 'object' && raw.files ? raw.files : {} }
    } catch {
      return { files: {} }
    }
  }

  private async writeState(root: string, state: SyncState): Promise<void> {
    await this.writeFileAtomic(join(root, STATE_FILE), Buffer.from(JSON.stringify(state, null, 2)))
  }

  private async writeFileAtomic(path: string, data: Buffer): Promise<void> {
    await mkdir(dirname(path), { recursive: true })
    const tmp = `${path}.cevher-tmp`
    await writeFile(tmp, data)
    await rename(tmp, path)
  }
}
