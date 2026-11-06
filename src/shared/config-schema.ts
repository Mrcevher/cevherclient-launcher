/**
 * Uzaktan config doğrulayıcı. Dış bağımlılık kullanmadan JSON'u RemoteConfig'e
 * dönüştürür; hatalı alanlarda CONFIG_INVALID fırlatır ve hangi alanın bozuk olduğunu
 * `detail` içinde belirtir.
 */

import { AppError } from './errors'
import { LOADER_TYPES } from './types'
import type { LoaderType, LocalizedText, NewsItem, RemoteConfig, ServerProfile } from './types'

type Obj = Record<string, unknown>

/** Sunucu kimliği instance klasör adı olarak kullanılır: yalnızca güvenli, kısa bir ad. */
const SERVER_ID_PATTERN = /^[A-Za-z0-9_-]{1,64}$/

function isLoopbackHost(hostname: string): boolean {
  const h = hostname.toLowerCase().replace(/^\[|\]$/g, '')
  return h === 'localhost' || h === '::1' || /^127(\.\d{1,3}){3}$/.test(h)
}

/** https adresi ya da (yerel geliştirme için) loopback üzerinde http; başka düz http reddedilir. */
export function isSecureOrLoopbackUrl(value: string): boolean {
  try {
    const u = new URL(value)
    return u.protocol === 'https:' || (u.protocol === 'http:' && isLoopbackHost(u.hostname))
  } catch {
    return false
  }
}

function fail(path: string, reason: string): never {
  throw new AppError('CONFIG_INVALID', `${path}: ${reason}`)
}

function asObject(value: unknown, path: string): Obj {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) fail(path, 'nesne bekleniyordu')
  return value as Obj
}

function asString(value: unknown, path: string): string {
  if (typeof value !== 'string' || value.trim() === '') fail(path, 'boş olmayan metin bekleniyordu')
  return value
}

function asOptionalString(value: unknown, path: string): string | undefined {
  return value === undefined || value === null ? undefined : asString(value, path)
}

function asLocalized(value: unknown, path: string): LocalizedText {
  if (typeof value === 'string') return value
  const obj = asObject(value, path)
  const out: Record<string, string> = {}
  for (const [lang, text] of Object.entries(obj)) out[lang] = asString(text, `${path}.${lang}`)
  if (Object.keys(out).length === 0) fail(path, 'en az bir dil gerekli')
  return out
}

function serverId(value: unknown, path: string): string {
  const id = asString(value, path)
  if (!SERVER_ID_PATTERN.test(id)) fail(path, 'yalnızca harf, rakam, _ ve - (en çok 64 karakter) olabilir')
  return id
}

function secureUrl(value: unknown, path: string): string | undefined {
  const url = asOptionalString(value, path)
  if (url && !isSecureOrLoopbackUrl(url)) fail(path, 'https adresi bekleniyordu (http yalnızca yerel makine için)')
  return url
}

function sha256(value: unknown, path: string): string | undefined {
  const hash = asOptionalString(value, path)
  if (hash && !/^[0-9a-fA-F]{64}$/.test(hash)) fail(path, '64 haneli SHA-256 bekleniyordu')
  return hash?.toLowerCase()
}

function parseServer(value: unknown, index: number): ServerProfile {
  const p = `servers[${index}]`
  const o = asObject(value, p)
  const loader = asObject(o.loader ?? { type: 'vanilla' }, `${p}.loader`)
  const loaderType = asString(loader.type, `${p}.loader.type`)
  if (!(LOADER_TYPES as readonly string[]).includes(loaderType)) {
    fail(`${p}.loader.type`, `şunlardan biri olmalı: ${LOADER_TYPES.join(', ')}`)
  }
  const port = o.port === undefined ? 25565 : Number(o.port)
  if (!Number.isInteger(port) || port < 1 || port > 65535) fail(`${p}.port`, 'geçersiz port')

  const themeColor = asOptionalString(o.themeColor, `${p}.themeColor`)
  if (themeColor && !/^#[0-9a-fA-F]{6}$/.test(themeColor)) fail(`${p}.themeColor`, '#rrggbb biçimi bekleniyordu')

  return {
    id: serverId(o.id, `${p}.id`),
    name: asLocalized(o.name, `${p}.name`),
    description: asLocalized(o.description ?? '', `${p}.description`),
    address: asString(o.address, `${p}.address`),
    port,
    minecraftVersion: asString(o.minecraftVersion, `${p}.minecraftVersion`),
    loader: {
      type: loaderType as LoaderType,
      version: asOptionalString(loader.version, `${p}.loader.version`)
    },
    modpackManifestUrl: secureUrl(o.modpackManifestUrl, `${p}.modpackManifestUrl`),
    modpackSha256: sha256(o.modpackSha256, `${p}.modpackSha256`),
    themeColor
  }
}

function parseNews(value: unknown, index: number): NewsItem {
  const p = `news[${index}]`
  const o = asObject(value, p)
  const date = asString(o.date, `${p}.date`)
  if (Number.isNaN(Date.parse(date))) fail(`${p}.date`, 'ISO tarih bekleniyordu')
  return {
    id: asString(o.id, `${p}.id`),
    title: asLocalized(o.title, `${p}.title`),
    date,
    content: asLocalized(o.content, `${p}.content`)
  }
}

export function parseRemoteConfig(raw: unknown): RemoteConfig {
  const root = asObject(raw, 'config')
  if (!Array.isArray(root.servers)) fail('servers', 'dizi bekleniyordu')
  const servers = root.servers.map(parseServer)

  const ids = new Set<string>()
  for (const s of servers) {
    if (ids.has(s.id)) fail('servers', `tekrarlanan id: ${s.id}`)
    ids.add(s.id)
  }

  const news = Array.isArray(root.news) ? root.news.map(parseNews) : []
  // Haberler her zaman yeniden eskiye sıralı tutulur.
  news.sort((a, b) => Date.parse(b.date) - Date.parse(a.date))

  // Şifre ve oturum belirteci bu adrese gider: düz http yalnızca yerel makinede kabul edilir.
  const authServerUrl = secureUrl(root.authServerUrl, 'authServerUrl')

  const links: Record<string, string> = {}
  if (root.links !== undefined) {
    const l = asObject(root.links, 'links')
    for (const k of ['website', 'store', 'credits', 'orders', 'discord']) {
      const v = asOptionalString(l[k], `links.${k}`)
      if (v && !/^https:\/\//i.test(v)) fail(`links.${k}`, 'https adresi bekleniyordu')
      if (v) links[k] = v
    }
  }

  // Oyuncu kafası görseli: {username} ve {size} yer tutucularıyla https şablonu.
  const avatarUrl = asOptionalString(root.avatarUrl, 'avatarUrl')
  if (avatarUrl && (!/^https:\/\//i.test(avatarUrl) || !avatarUrl.includes('{username}'))) {
    fail('avatarUrl', '{username} içeren https adresi bekleniyordu')
  }

  return {
    minLauncherVersion: asOptionalString(root.minLauncherVersion, 'minLauncherVersion') ?? '0.0.0',
    authServerUrl,
    avatarUrl,
    links,
    servers,
    news
  }
}
