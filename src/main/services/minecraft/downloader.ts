/**
 * Kurulum dosyalarının indiricisi ve @xmcl/installer manifest yürütücüsüyle bağlantısı.
 *
 * - executeInstallManifest her dosyayı önce SHA1 (yoksa boyut) ile doğrular; yalnızca
 *   eksik/bozuk olanları buradaki `download` adaptörüne verir, indirdikten sonra yeniden
 *   doğrular, bozukları silip tekrar dener. Yani devam etme dosya bazlıdır.
 * - Ek olarak yarım kalan dosya `<dosya>.part` olarak saklanır ve HTTP Range ile kaldığı
 *   yerden sürdürülür (duraklat/devam, iptal sonrası yeniden başlatma, uygulama kapanması).
 * - Duraklatma uçuştaki istekleri keser, `.part` dosyaları kalır; devamda aynı noktadan
 *   devam edilir. İptal AbortError fırlatır (yürütücü bunu kalıcı hata sayar).
 */

import { spawn } from 'node:child_process'
import { createWriteStream } from 'node:fs'
import { mkdir, rename, rm, stat } from 'node:fs/promises'
import { dirname } from 'node:path'
import { Readable, Transform } from 'node:stream'
import { pipeline } from 'node:stream/promises'
import type { ReadableStream as NodeReadableStream } from 'node:stream/web'
import {
  createNodeInstallRuntime,
  executeInstallManifest,
  executeInstallWorkflow,
  type InstallFile,
  type InstallManifest,
  type InstallRuntime,
  type InstallWorkflow,
  type JavaCommand
} from '@xmcl/installer'

/** Aynı anda indirilen dosya sayısı. */
const CONCURRENCY = 16
/** Bu kadar süre veri gelmezse istek kesilip yeniden denenir. */
const IDLE_TIMEOUT_MS = 30_000

function abortError(): Error {
  const err = new Error('İndirme iptal edildi')
  err.name = 'AbortError'
  return err
}

/**
 * Bir kurulum işinin duraklat / devam / iptal durumu ve bayt sayaçları.
 * Bir InstallTask (veya Java indirmesi) başına bir tane.
 */
export class DownloadController {
  private pausedFlag = false
  private cancelledFlag = false
  private readonly cancelCtrl = new AbortController()
  /** Duraklatmada iptal edilip yenilenen, uçuştaki isteklerin sinyali. */
  private epoch = new AbortController()
  private waiters: Array<() => void> = []

  private readonly sizes = new Map<string, number>()
  private readonly done = new Map<string, number>()
  /** Bu oturumda ağdan gerçekten aktarılan bayt (hız hesabı için). */
  transferred = 0

  get paused(): boolean {
    return this.pausedFlag
  }

  get cancelled(): boolean {
    return this.cancelledFlag
  }

  /** Yürütücüye verilen, yalnızca iptalde tetiklenen sinyal. */
  get cancelSignal(): AbortSignal {
    return this.cancelCtrl.signal
  }

  /** Uçuştaki istekler için sinyal (duraklatma veya iptal). */
  get requestSignal(): AbortSignal {
    return this.epoch.signal
  }

  pause(): void {
    if (this.pausedFlag || this.cancelledFlag) return
    this.pausedFlag = true
    this.epoch.abort()
  }

  resume(): void {
    if (!this.pausedFlag) return
    this.pausedFlag = false
    this.epoch = new AbortController()
    const waiters = this.waiters
    this.waiters = []
    for (const w of waiters) w()
  }

  cancel(): void {
    if (this.cancelledFlag) return
    this.cancelledFlag = true
    this.epoch.abort()
    this.cancelCtrl.abort(abortError())
    const waiters = this.waiters
    this.waiters = []
    for (const w of waiters) w()
  }

  throwIfCancelled(): void {
    if (this.cancelledFlag) throw abortError()
  }

  /** Duraklatılmışsa devam edilene kadar bekler; iptalde fırlatır. */
  async waitIfPaused(): Promise<void> {
    this.throwIfCancelled()
    while (this.pausedFlag) {
      await new Promise<void>((resolve) => this.waiters.push(resolve))
      this.throwIfCancelled()
    }
  }

  /** İndirilecek dosyayı toplam boyuta ekler; tekrar denemede sayaç sıfırlanır. */
  register(file: InstallFile): void {
    if (!this.sizes.has(file.path)) this.sizes.set(file.path, file.size && file.size > 0 ? file.size : 0)
    this.done.set(file.path, 0)
  }

  /** Boyutu bilinmeyen dosya için Content-Length geldiğinde. */
  setSize(path: string, size: number): void {
    if ((this.sizes.get(path) ?? 0) === 0) this.sizes.set(path, size)
  }

  setDone(path: string, bytes: number): void {
    this.done.set(path, bytes)
  }

  addDone(path: string, bytes: number): void {
    this.done.set(path, (this.done.get(path) ?? 0) + bytes)
    this.transferred += bytes
  }

  get downloadedBytes(): number {
    let sum = 0
    for (const v of this.done.values()) sum += v
    return sum
  }

  get totalBytes(): number {
    let sum = 0
    for (const v of this.sizes.values()) sum += v
    return Math.max(sum, this.downloadedBytes)
  }
}

/** Birden fazla görev aynı dosyayı (ortak kütüphane/asset) aynı anda yazmasın diye. */
const inflight = new Map<string, Promise<void>>()

async function downloadAll(files: InstallFile[], ctl: DownloadController): Promise<void> {
  for (const file of files) ctl.register(file)
  const queue = [...files]
  const errors: unknown[] = []

  const worker = async (): Promise<void> => {
    for (let file = queue.shift(); file; file = queue.shift()) {
      try {
        await downloadOne(file, ctl)
      } catch (err) {
        if (ctl.cancelled) throw abortError()
        errors.push(err)
      }
    }
  }
  await Promise.all(Array.from({ length: Math.min(CONCURRENCY, files.length) }, worker))
  ctl.throwIfCancelled()
  // Yürütücü hangi dosyaların bozuk kaldığını kendisi doğrulayıp yeniden dener.
  if (errors.length > 0) throw new AggregateError(errors, `${errors.length} dosya indirilemedi`)
}

async function downloadOne(file: InstallFile, ctl: DownloadController): Promise<void> {
  const other = inflight.get(file.path)
  if (other) {
    await other.catch(() => undefined)
    if (await stat(file.path).then((s) => s.isFile(), () => false)) {
      ctl.setDone(file.path, file.size && file.size > 0 ? file.size : 0)
      return
    }
  }
  const job = downloadWithUrls(file, ctl)
  inflight.set(file.path, job)
  try {
    await job
  } finally {
    if (inflight.get(file.path) === job) inflight.delete(file.path)
  }
}

async function downloadWithUrls(file: InstallFile, ctl: DownloadController): Promise<void> {
  const part = `${file.path}.part`
  let lastError: unknown = new Error(`İndirme adresi yok: ${file.path}`)
  for (const url of file.urls) {
    for (;;) {
      await ctl.waitIfPaused()
      try {
        await fetchToPart(url, file, part, ctl)
        await rename(part, file.path)
        return
      } catch (err) {
        if (ctl.cancelled) throw abortError()
        // Duraklatma uçuştaki isteği kesti: devamda aynı adresten, kaldığı yerden sürdür.
        if (ctl.paused) continue
        lastError = err
        break
      }
    }
  }
  throw lastError
}

async function fetchToPart(url: string, file: InstallFile, part: string, ctl: DownloadController): Promise<void> {
  await mkdir(dirname(part), { recursive: true })
  const expected = file.size && file.size > 0 ? file.size : undefined
  let have = await stat(part).then((s) => s.size, () => 0)

  if (expected !== undefined && have === expected) {
    ctl.setDone(file.path, have)
    return
  }
  if (expected !== undefined && have > expected) {
    await rm(part, { force: true })
    have = 0
  }

  const idle = new AbortController()
  const signal = AbortSignal.any([ctl.requestSignal, idle.signal])
  let timer = setTimeout(() => idle.abort(), IDLE_TIMEOUT_MS)
  const bump = (): void => {
    clearTimeout(timer)
    timer = setTimeout(() => idle.abort(), IDLE_TIMEOUT_MS)
  }

  try {
    const res = await fetch(url, { signal, headers: have > 0 ? { Range: `bytes=${have}-` } : undefined })
    if (res.status === 416) {
      // Sunucu aralığı kabul etmedi: yarım dosyayı at, sonraki denemede baştan.
      await rm(part, { force: true })
      throw new Error(`HTTP 416: ${url}`)
    }
    if (!res.ok || !res.body) throw new Error(`HTTP ${res.status}: ${url}`)

    const append = have > 0 && res.status === 206
    if (!append) have = 0
    const length = Number(res.headers.get('content-length'))
    if (expected === undefined && length > 0) ctl.setSize(file.path, have + length)
    ctl.setDone(file.path, have)

    const counter = new Transform({
      transform(chunk: Buffer, _enc, cb) {
        bump()
        ctl.addDone(file.path, chunk.length)
        cb(null, chunk)
      }
    })
    await pipeline(
      Readable.fromWeb(res.body as unknown as NodeReadableStream<Uint8Array>),
      counter,
      createWriteStream(part, { flags: append ? 'a' : 'w' }),
      { signal }
    )
  } finally {
    clearTimeout(timer)
  }
}

/** Forge/NeoForge işlemcileri için: konsol penceresi açmadan Java çalıştırır. */
function runJava(command: JavaCommand, signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    const child = spawn(command.executable, command.args, {
      cwd: command.cwd,
      env: command.env ? { ...process.env, ...command.env } : process.env,
      signal,
      windowsHide: true
    })
    let tail = ''
    const keep = (data: Buffer): void => {
      tail = (tail + data.toString()).slice(-4000)
    }
    child.stdout?.on('data', keep)
    child.stderr?.on('data', keep)
    child.once('error', reject)
    child.once('exit', (code) => {
      if (code === 0) resolve()
      else reject(new Error(`Java işlemi ${code} koduyla çıktı: ${tail.trim().slice(-1500)}`))
    })
  })
}

export function createInstallRuntime(ctl: DownloadController): InstallRuntime {
  return createNodeInstallRuntime({
    signal: ctl.cancelSignal,
    download: (files) => downloadAll(files, ctl),
    runJava: (command) => runJava(command, ctl.cancelSignal)
  })
}

export async function runManifest(plan: InstallManifest, ctl: DownloadController): Promise<void> {
  await executeInstallManifest(plan, createInstallRuntime(ctl), { signal: ctl.cancelSignal })
}

export function runWorkflow<T>(workflow: InstallWorkflow<T>, ctl: DownloadController): Promise<T> {
  return executeInstallWorkflow(workflow, createInstallRuntime(ctl), { signal: ctl.cancelSignal })
}

/** Tek görevli "dosyaları indir" manifest'i. */
export function filesPlan(id: string, files: Array<InstallFile | undefined>): InstallManifest {
  return {
    schemaVersion: 1,
    tasks: [{ id, type: 'files', files: files.filter((f): f is InstallFile => f !== undefined) }]
  }
}
