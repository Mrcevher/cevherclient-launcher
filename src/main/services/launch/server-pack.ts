import { createHash } from 'node:crypto'
import { createWriteStream, existsSync } from 'node:fs'
import { mkdir, readFile, rename, rm, stat, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { Readable, Transform } from 'node:stream'
import { pipeline } from 'node:stream/promises'
import type { ReadableStream as NodeReadableStream } from 'node:stream/web'

/**
 * Sunucu kaynak paketinin önceden kurulması.
 *
 * Sunucu (Nexo) girişte 20+ MB'lık bir kaynak paketi gönderir; vanilla oyun önce onay ister,
 * sonra paketi indirip tüm kaynakları yeniden yükler ve oyun kasar. Bunun yerine launcher,
 * CevherAuth'un GET /api/pack yanıtındaki paketi Oyna'da indirir, instance'ın resourcepacks/
 * klasörüne koyar ve options.txt'de etkinleştirir; paket oyunun açılışında zaten yüklenir.
 * SHA-1'i .cevher/server-pack.json'a yazılır: oturum modu (game-mods/cevher-session, ServerPack)
 * sunucu aynı SHA-1'le paket gönderince indirmeden ve yeniden yüklemeden "yüklendi" yanıtı verir.
 *
 * Her hata sessizce geçilir: paket kurulamazsa oyun yine açılır, mod da onay ekranını
 * atlayıp paketi vanilla yoldan indirir.
 */

export const SERVER_PACK_FILE = 'cevherclient-server.zip'
/** options.txt'deki paket kimliği. */
const PACK_ID = `file/${SERVER_PACK_FILE}`
/** Oturum moduyla paylaşılan kayıt; biçim ServerPack.java ile aynı kalmalı. */
const MARKER = join('.cevher', 'server-pack.json')
/** Bu kadar süre veri gelmezse indirme bırakılır. */
const IDLE_TIMEOUT_MS = 30_000
/** Beklenmedik büyüklükte bir yanıta karşı üst sınır. */
const MAX_SIZE = 512 * 1024 * 1024

interface PackInfo {
  sha1: string
  size: number
  url: string
}

/** CevherAuth'tan paket bilgisi; paket yoksa null, ulaşılamazsa undefined (mevcut durum korunur). */
async function fetchInfo(authUrl: string): Promise<PackInfo | null | undefined> {
  let json: Record<string, unknown>
  try {
    const res = await fetch(`${authUrl}/api/pack`, { signal: AbortSignal.timeout(10_000) })
    // Eski eklenti bu ucu bilmez (404/405): paket yönetimi yok sayılır, mevcut durum korunur.
    if (!res.ok) return undefined
    json = (await res.json()) as Record<string, unknown>
  } catch {
    return undefined
  }
  if (json.ok !== true) return undefined
  if (json.enabled !== true) return null
  const { sha1, size, url } = json
  if (typeof sha1 !== 'string' || !/^[0-9a-f]{40}$/.test(sha1)) return undefined
  if (typeof size !== 'number' || !Number.isSafeInteger(size) || size <= 0 || size > MAX_SIZE) return undefined
  if (typeof url !== 'string' || !url.startsWith('https://')) return undefined
  return { sha1, size, url }
}

async function readMarker(gamePath: string): Promise<string | null> {
  try {
    const raw = JSON.parse(await readFile(join(gamePath, MARKER), 'utf8')) as { sha1?: unknown }
    return typeof raw.sha1 === 'string' ? raw.sha1 : null
  } catch {
    return null
  }
}

/** Paketi indirir, boyut ve SHA-1'i doğrular; doğruysa hedefe taşır. */
async function download(info: PackInfo, target: string, onProgress: (p: number) => void): Promise<void> {
  const part = `${target}.part`
  const idle = new AbortController()
  let timer = setTimeout(() => idle.abort(), IDLE_TIMEOUT_MS)
  const hash = createHash('sha1')
  let done = 0
  try {
    const res = await fetch(info.url, { signal: idle.signal })
    if (!res.ok || !res.body) throw new Error(`HTTP ${res.status}`)
    const counter = new Transform({
      transform(chunk: Buffer, _enc, cb) {
        clearTimeout(timer)
        timer = setTimeout(() => idle.abort(), IDLE_TIMEOUT_MS)
        done += chunk.length
        if (done > info.size) {
          cb(new Error('paket beklenenden büyük'))
          return
        }
        hash.update(chunk)
        onProgress(done / info.size)
        cb(null, chunk)
      }
    })
    await pipeline(Readable.fromWeb(res.body as unknown as NodeReadableStream<Uint8Array>), counter, createWriteStream(part), {
      signal: idle.signal
    })
  } catch (err) {
    await rm(part, { force: true })
    throw err
  } finally {
    clearTimeout(timer)
  }
  const actual = hash.digest('hex')
  if (done !== info.size || actual !== info.sha1) {
    await rm(part, { force: true })
    throw new Error(`doğrulanamadı (${done} bayt, sha1 ${actual})`)
  }
  await rm(target, { force: true })
  await rename(part, target)
}

/** options.txt'de bir JSON dizisi anahtarını okur (yoksa null). */
function readList(lines: string[], key: string): string[] | null {
  const line = lines.find((l) => l.startsWith(`${key}:`))
  if (!line) return null
  try {
    const value = JSON.parse(line.slice(key.length + 1)) as unknown
    return Array.isArray(value) ? value.filter((v): v is string => typeof v === 'string') : null
  } catch {
    return null
  }
}

/**
 * Paketi options.txt'deki seçili listeye en üste (listenin sonuna) ekler ya da çıkarır;
 * diğer paketlere ve satırlara dokunmaz.
 */
async function setEnabled(gamePath: string, enabled: boolean): Promise<void> {
  const path = join(gamePath, 'options.txt')
  let text = ''
  try {
    text = await readFile(path, 'utf8')
  } catch {
    if (!enabled) return
  }
  const eol = text.includes('\r\n') ? '\r\n' : '\n'
  const lines = text === '' ? [] : text.split(/\r?\n/)
  if (lines.at(-1) === '') lines.pop()

  const update = (key: string, fallback: string[], want: boolean): void => {
    const current = readList(lines, key) ?? fallback
    const next = current.filter((id) => id !== PACK_ID)
    if (want) next.push(PACK_ID)
    const line = `${key}:${JSON.stringify(next)}`
    const index = lines.findIndex((l) => l.startsWith(`${key}:`))
    if (index >= 0) lines[index] = line
    else if (want) lines.push(line)
  }
  update('resourcePacks', ['vanilla'], enabled)
  // Paket biçimi sürümle tam eşleşmese bile oyun "uyumsuz" deyip listeden atmasın.
  update('incompatibleResourcePacks', [], enabled)

  const out = lines.join(eol) + eol
  if (out === text) return
  await writeFile(path, out, 'utf8')
}

/**
 * Sunucu paketini instance'a kurar/günceller ve etkinleştirir. Oyunu hiçbir durumda engellemez.
 * @param authUrl CevherAuth adresi (sonunda / olmadan); yoksa hiçbir şey yapılmaz.
 */
export async function prepareServerPack(
  authUrl: string | undefined,
  gamePath: string,
  onProgress: (p: number) => void
): Promise<void> {
  if (!authUrl) return
  const target = join(gamePath, 'resourcepacks', SERVER_PACK_FILE)
  try {
    const info = await fetchInfo(authUrl)
    if (info === undefined) return
    if (info === null) {
      // Sunucu artık paket göndermiyor: önceden kurulanı kaldır.
      await rm(join(gamePath, MARKER), { force: true })
      await rm(target, { force: true })
      await setEnabled(gamePath, false)
      return
    }

    const have = existsSync(target) && (await readMarker(gamePath)) === info.sha1 && (await stat(target)).size === info.size
    if (!have) {
      // Eski kayıt yeni dosyayla eşleşmesin diye önce silinir.
      await rm(join(gamePath, MARKER), { force: true })
      await mkdir(join(gamePath, 'resourcepacks'), { recursive: true })
      await download(info, target, onProgress)
      await mkdir(join(gamePath, '.cevher'), { recursive: true })
      await writeFile(join(gamePath, MARKER), JSON.stringify({ v: 1, sha1: info.sha1 }), 'utf8')
    }
    await setEnabled(gamePath, true)
    onProgress(1)
  } catch (err) {
    console.warn('[server-pack] sunucu paketi önceden kurulamadı; oyun paketi girişte indirecek:', (err as Error).message)
  }
}
