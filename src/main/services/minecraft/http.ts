import { mkdir, readFile, rename, writeFile } from 'node:fs/promises'
import { dirname } from 'node:path'
import { AppError } from '@shared/errors'

const DEFAULT_TIMEOUT_MS = 15_000

/** Ağ hatalarını NO_INTERNET'e, HTTP hatalarını DOWNLOAD_FAILED'a çevirir. */
export async function fetchJson<T>(url: string, timeoutMs = DEFAULT_TIMEOUT_MS): Promise<T> {
  let res: Response
  try {
    res = await fetch(url, { signal: AbortSignal.timeout(timeoutMs), headers: { Accept: 'application/json' } })
  } catch (err) {
    throw new AppError('NO_INTERNET', `${url}: ${(err as Error).message}`)
  }
  if (!res.ok) throw new AppError('DOWNLOAD_FAILED', `${url}: HTTP ${res.status}`)
  try {
    return (await res.json()) as T
  } catch (err) {
    throw new AppError('DOWNLOAD_FAILED', `${url}: geçersiz JSON (${(err as Error).message})`)
  }
}

interface CacheEntry {
  at: number
  value: unknown
}

const memory = new Map<string, CacheEntry>()

/**
 * Bellekte maxAgeMs boyunca tutulan, diske de yazılan JSON. Ağ yoksa diskteki son
 * kopya kullanılır; böylece launcher çevrimdışıyken de kurulu sürümleri listeleyebilir.
 */
export async function cachedJson<T>(url: string, cacheFile: string, maxAgeMs: number): Promise<T> {
  const hit = memory.get(url)
  if (hit && Date.now() - hit.at < maxAgeMs) return hit.value as T

  try {
    const value = await fetchJson<T>(url)
    memory.set(url, { at: Date.now(), value })
    void writeCache(cacheFile, value)
    return value
  } catch (err) {
    if (hit) return hit.value as T
    const disk = await readFile(cacheFile, 'utf8')
      .then((text) => JSON.parse(text) as T)
      .catch(() => undefined)
    if (disk === undefined) throw err
    // Kısa süre bellekte tut ki her çağrı yeniden ağa çıkmasın.
    memory.set(url, { at: Date.now() - maxAgeMs + 60_000, value: disk })
    return disk
  }
}

async function writeCache(file: string, value: unknown): Promise<void> {
  try {
    await mkdir(dirname(file), { recursive: true })
    await writeFile(`${file}.tmp`, JSON.stringify(value), 'utf8')
    await rename(`${file}.tmp`, file)
  } catch {
    // Önbellek yazılamazsa sorun değil.
  }
}
