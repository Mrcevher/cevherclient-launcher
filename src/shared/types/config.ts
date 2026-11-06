/**
 * Uzaktan config (remote config) tipleri.
 * Launcher açılışta tek bir JSON dosyası okur; yeni sunucu/mod eklemek için yalnızca
 * bu dosya güncellenir, launcher koduna dokunulmaz.
 */

import type { Language } from './settings'

/** Düz metin veya dile göre metin. Ör: "Survival" ya da { "tr": "Hayatta Kalma", "en": "Survival" } */
export type LocalizedText = string | Partial<Record<Language, string>>

export const LOADER_TYPES = ['vanilla', 'fabric', 'forge', 'neoforge', 'quilt'] as const
export type LoaderType = (typeof LOADER_TYPES)[number]

export interface LoaderSpec {
  type: LoaderType
  /** Vanilla için boş bırakılır. */
  version?: string
}

export interface ServerProfile {
  /** Kalıcı, benzersiz kimlik (instance klasör adı olarak da kullanılacak). */
  id: string
  name: LocalizedText
  description: LocalizedText
  address: string
  port: number
  minecraftVersion: string
  loader: LoaderSpec
  /** Faz 3: Modrinth .mrpack manifest adresi. */
  modpackManifestUrl?: string
  /** İsteğe bağlı: .mrpack dosyasının beklenen SHA-256 özeti; eşleşmezse paket kullanılmaz. */
  modpackSha256?: string
  /** Sunucu kartının vurgu rengi (#rrggbb). */
  themeColor?: string
}

export interface NewsItem {
  id: string
  title: LocalizedText
  /** ISO 8601 tarih. */
  date: string
  /** Markdown içerik. */
  content: LocalizedText
}

export interface LauncherLinks {
  website?: string
  store?: string
  credits?: string
  orders?: string
  discord?: string
}

export interface RemoteConfig {
  minLauncherVersion: string
  /**
   * Sunucudaki CevherAuth eklentisinin adresi (ör. "http://oyun.ornek.com:25580").
   * Verilirse oyuncu launcher'da kullanıcı adı + şifreyle kayıt olur / giriş yapar ve oyunda
   * şifre sorulmaz. Verilmezse launcher yalnızca kullanıcı adı ister.
   */
  authServerUrl?: string
  /**
   * Oyuncu kafası (skin) görseli şablonu; {username} ve {size} doldurulur.
   * Varsayılan: https://mc-heads.net/avatar/{username}/{size}
   */
  avatarUrl?: string
  /** Launcher menüsündeki dış bağlantılar (Mağaza, Kredi Al, Siparişlerim, Discord, site). */
  links?: LauncherLinks
  servers: ServerProfile[]
  news: NewsItem[]
}

/** Config servisinin renderer'a döndürdüğü paket. */
export interface RemoteConfigSnapshot {
  config: RemoteConfig
  /** Config'in okunduğu kaynak (dosya yolu veya URL). */
  source: string
  loadedAt: string
  /** Launcher sürümü minLauncherVersion'ı karşılıyor mu? */
  launcherUpToDate: boolean
}
