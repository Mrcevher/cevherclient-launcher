/**
 * Küçük Modrinth API istemcisi (Performans Paketi ve mod araması ortak kullanır).
 * Modrinth, istemciyi tanımlayan bir User-Agent ister.
 */

import { basename } from 'node:path'
import { app } from 'electron'
import { AppError } from '@shared/errors'
import type { ModKind } from '@shared/types'
import { DownloadController, filesPlan, runManifest } from '../minecraft/downloader'
import type { ProgressCallback } from '../types'

const API = 'https://api.modrinth.com/v2'

export interface ModrinthFile {
  url: string
  filename: string
  primary: boolean
  size: number
  hashes: { sha1: string }
}

export interface ModrinthVersion {
  id: string
  project_id: string
  version_number: string
  version_type?: 'release' | 'beta' | 'alpha'
  loaders?: string[]
  game_versions?: string[]
  files: ModrinthFile[]
  dependencies: Array<{ project_id: string | null; version_id: string | null; dependency_type: string }>
}

export interface ModrinthProject {
  id: string
  slug: string
  title: string
  icon_url: string | null
}

export async function modrinthGet<T>(path: string): Promise<T | null> {
  let response: Response
  try {
    response = await fetch(API + path, {
      headers: { 'User-Agent': `Cevherclient/${app.getVersion()} (cevhercraft.com)` },
      signal: AbortSignal.timeout(15_000)
    })
  } catch (err) {
    throw new AppError('NO_INTERNET', `Modrinth: ${(err as Error).message}`)
  }
  if (response.status === 404) return null
  if (!response.ok) throw new AppError('DOWNLOAD_FAILED', `Modrinth ${path}: HTTP ${response.status}`)
  return (await response.json()) as T
}

const json = (value: unknown): string => encodeURIComponent(JSON.stringify(value))

/**
 * Projenin (id veya slug) bu Minecraft sürümüne uygun en yeni Fabric sürümü; yoksa null.
 * Kararlı (release) sürüm beta/alpha'ya tercih edilir; yalnızca beta varsa o seçilir.
 * `skip` true döndüren sürümler (bilinen bozuk sürümler) hiç düşünülmez.
 */
export async function latestFabricVersion(
  project: string,
  mcVersion: string,
  skip?: (version: ModrinthVersion) => boolean
): Promise<ModrinthVersion | null> {
  const all = await modrinthGet<ModrinthVersion[]>(
    `/project/${encodeURIComponent(project)}/version?game_versions=${json([mcVersion])}&loaders=${json(['fabric'])}`
  )
  const versions = skip ? all?.filter((v) => !skip(v)) : all
  return versions?.find((v) => v.version_type === 'release') ?? versions?.[0] ?? null
}

/** Belirli bir sürüm (başka bir modun bağımlılık olarak sabitlediği sürüm için). */
export function versionById(id: string): Promise<ModrinthVersion | null> {
  return modrinthGet<ModrinthVersion>(`/version/${encodeURIComponent(id)}`)
}

/**
 * Shader paketinin bu Minecraft sürümüne uygun en yeni sürümü. Shader'ların yükleyicisi Fabric değil
 * Iris/OptiFine'dır; Iris uyumlusu tercih edilir, yoksa OptiFine uyumlusu (Iris onu da okur). Yoksa null.
 */
export async function latestShaderVersion(
  project: string,
  mcVersion: string,
  anyVersion = false
): Promise<ModrinthVersion | null> {
  // anyVersion: yazar bu Minecraft sürümünü etiketlememiş olabilir (shader'lar çoğunlukla sürümden bağımsızdır);
  // o durumda projenin en yeni sürümü alınır.
  const versions = await modrinthGet<ModrinthVersion[]>(
    `/project/${encodeURIComponent(project)}/version${anyVersion ? '' : `?game_versions=${json([mcVersion])}`}`
  )
  return (
    versions?.find((v) => v.loaders?.includes('iris')) ?? versions?.find((v) => v.loaders?.includes('optifine')) ?? null
  )
}

/** Birden fazla projenin (id veya slug) bilgisi tek istekte. */
export async function getProjects(ids: string[]): Promise<ModrinthProject[]> {
  if (ids.length === 0) return []
  return (await modrinthGet<ModrinthProject[]>(`/projects?ids=${json(ids)}`)) ?? []
}

/** Dosya adı tek bir ad olmalı: ayırıcı, `..`, sürücü harfi veya kontrol karakteri içeremez. */
export function isSafeFilename(name: unknown): name is string {
  return (
    typeof name === 'string' &&
    name.length > 0 &&
    name.length <= 200 &&
    basename(name) === name &&
    // eslint-disable-next-line no-control-regex
    !/[\\/:*?"<>|\u0000-\u001f]/.test(name) &&
    name !== '.' &&
    name !== '..' &&
    !/^(con|prn|aux|nul|com[1-9]|lpt[1-9])(\.|$)/i.test(name) &&
    !name.endsWith('.') &&
    !name.endsWith(' ')
  )
}

/** İndirme adresi Modrinth CDN'inde bir https adresi olmalı. */
function isModrinthCdn(url: unknown): boolean {
  if (typeof url !== 'string') return false
  try {
    const u = new URL(url)
    return u.protocol === 'https:' && (u.hostname === 'modrinth.com' || u.hostname.endsWith('.modrinth.com'))
  } catch {
    return false
  }
}

const isUsableFile = (f: ModrinthFile): boolean =>
  isSafeFilename(f?.filename) && isModrinthCdn(f.url) && typeof f.hashes?.sha1 === 'string' && /^[0-9a-f]{40}$/i.test(f.hashes.sha1)

/** Birincil (yoksa ilk) dosya; adı, adresi veya özeti güvenli değilse undefined. */
export function primaryFile(version: ModrinthVersion): ModrinthFile | undefined {
  const file = version.files.find((f) => f.primary) ?? version.files[0]
  return file && isUsableFile(file) ? file : undefined
}

/** Dosyaları SHA1 doğrulamalı indirir (eksik/bozuk olanlar; kesilirse kaldığı yerden). */
export async function downloadModFiles(
  files: Array<{ path: string; file: ModrinthFile }>,
  onProgress?: ProgressCallback
): Promise<void> {
  if (files.length === 0) return
  for (const { file } of files) {
    if (!isUsableFile(file)) throw new AppError('DOWNLOAD_FAILED', `Modrinth dosyası güvenli değil: ${String(file?.filename)}`)
  }
  const ctl = new DownloadController()
  const poll = setInterval(() => {
    const total = ctl.totalBytes
    if (total > 0) onProgress?.(Math.min(1, ctl.downloadedBytes / total))
  }, 250)
  try {
    await runManifest(
      filesPlan(
        'mods',
        files.map(({ path, file }) => ({
          path,
          urls: [file.url],
          size: file.size,
          checksum: { algorithm: 'sha1', value: file.hashes.sha1 }
        }))
      ),
      ctl
    )
  } catch (err) {
    throw err instanceof AppError ? err : new AppError('DOWNLOAD_FAILED', `Mod indirilemedi: ${(err as Error).message}`)
  } finally {
    clearInterval(poll)
  }
}

/** Modrinth arama: sunucu sürümüne uygun, istemcide çalışan Fabric modları ya da Iris/OptiFine shader paketleri. */
export async function searchMods(query: string, mcVersion: string, offset: number, kind: ModKind = 'mod') {
  const facets =
    kind === 'shader'
      ? [['project_type:shader'], ['categories:iris', 'categories:optifine'], [`versions:${mcVersion}`]]
      : [
          ['project_type:mod'],
          ['categories:fabric'],
          [`versions:${mcVersion}`],
          ['client_side:required', 'client_side:optional']
        ]
  const index = query.trim() ? 'relevance' : 'downloads'
  return modrinthGet<{
    hits: Array<{
      project_id: string
      slug: string
      title: string
      description: string
      author: string
      icon_url: string | null
      downloads: number
      server_side: string
    }>
    total_hits: number
    offset: number
  }>(`/search?query=${encodeURIComponent(query.trim())}&facets=${json(facets)}&index=${index}&limit=20&offset=${Math.max(0, offset)}`)
}
