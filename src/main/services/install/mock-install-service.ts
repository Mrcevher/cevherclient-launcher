import { randomUUID } from 'node:crypto'
import { AppError } from '@shared/errors'
import type { InstallTask } from '@shared/types'
import type { EventSink } from '../../utils/events'
import { JsonStore } from '../../utils/json-store'
import { randomInt, simulateWork } from '../mock/mock-utils'
import type { InstallService, ProgressCallback } from '../types'

const TICK_MS = 250
const MB = 1024 * 1024

/**
 * Faz 1 mock'u: indirmeleri zamanlayıcıyla taklit eder (duraklat / devam / iptal).
 * Faz 2'de @xmcl/installer ile dosya bazlı devam edebilen, SHA1 doğrulamalı
 * gerçek indirmeye geçilecek; arayüz aynı kalacak.
 */
export class MockInstallService implements InstallService {
  private readonly tasks = new Map<string, InstallTask>()
  private readonly installed = new JsonStore<string[]>('mock-installed-versions.json', [], (raw) =>
    Array.isArray(raw) ? raw.filter((x): x is string => typeof x === 'string') : []
  )
  private timer: NodeJS.Timeout | null = null

  constructor(private readonly events: EventSink) {}

  async start(versionKey: string): Promise<InstallTask> {
    // Aynı sürüm için aktif bir görev varsa onu döndür.
    const existing = [...this.tasks.values()].find(
      (t) => t.versionKey === versionKey && (t.state === 'downloading' || t.state === 'paused' || t.state === 'queued')
    )
    if (existing) return existing

    const task: InstallTask = {
      id: randomUUID(),
      versionKey,
      state: 'downloading',
      downloadedBytes: 0,
      totalBytes: randomInt(90, 420) * MB,
      bytesPerSecond: 0
    }
    this.tasks.set(task.id, task)
    this.emit(task)
    this.ensureTimer()
    return task
  }

  async pause(taskId: string): Promise<InstallTask> {
    const task = this.getTask(taskId)
    if (task.state === 'downloading') this.update(task, { state: 'paused', bytesPerSecond: 0 })
    return task
  }

  async resume(taskId: string): Promise<InstallTask> {
    const task = this.getTask(taskId)
    if (task.state === 'paused') {
      this.update(task, { state: 'downloading' })
      this.ensureTimer()
    }
    return task
  }

  async cancel(taskId: string): Promise<InstallTask> {
    const task = this.getTask(taskId)
    if (task.state === 'downloading' || task.state === 'paused' || task.state === 'queued') {
      this.update(task, { state: 'cancelled', bytesPerSecond: 0 })
    }
    return task
  }

  async listTasks(): Promise<InstallTask[]> {
    return [...this.tasks.values()]
  }

  listInstalled(): Promise<string[]> {
    return this.installed.read()
  }

  async ensureInstalled(versionKey: string, onProgress: ProgressCallback): Promise<string> {
    const installed = await this.installed.read()
    if (!installed.includes(versionKey)) {
      // Başlatma sırasında kısa, kesintisiz bir kurulum taklidi.
      await simulateWork(1600, onProgress, 16)
      await this.markInstalled(versionKey)
    }
    onProgress(1)
    return versionKey
  }

  // ── iç işleyiş ────────────────────────────────────────────────────────────

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

  private async markInstalled(versionKey: string): Promise<void> {
    const installed = await this.installed.read()
    if (!installed.includes(versionKey)) await this.installed.write([...installed, versionKey])
  }

  private ensureTimer(): void {
    if (this.timer) return
    this.timer = setInterval(() => this.tick(), TICK_MS)
  }

  private tick(): void {
    const active = [...this.tasks.values()].filter((t) => t.state === 'downloading')
    if (active.length === 0) {
      if (this.timer) clearInterval(this.timer)
      this.timer = null
      return
    }

    const failOn = (process.env.CEVHER_MOCK_FAIL ?? '').toUpperCase().includes('DOWNLOAD_FAILED')
    for (const task of active) {
      const speed = randomInt(6, 18) * MB
      const downloaded = Math.min(task.totalBytes, task.downloadedBytes + (speed * TICK_MS) / 1000)

      if (failOn && downloaded / task.totalBytes > 0.4) {
        this.update(task, {
          state: 'failed',
          bytesPerSecond: 0,
          error: new AppError('DOWNLOAD_FAILED', 'CEVHER_MOCK_FAIL ile tetiklendi').toJSON()
        })
        continue
      }

      if (downloaded >= task.totalBytes) {
        this.update(task, { downloadedBytes: task.totalBytes, bytesPerSecond: 0, state: 'completed' })
        void this.markInstalled(task.versionKey)
      } else {
        this.update(task, { downloadedBytes: downloaded, bytesPerSecond: speed })
      }
    }
  }
}
