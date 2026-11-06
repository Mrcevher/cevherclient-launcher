/**
 * Servislerin paylaştığı alan tipleri: hesap, sunucu durumu, sürümler, kurulum,
 * modpack, Java, başlatma ve sistem bilgisi.
 */

import type { SerializedError } from '../errors'
import type { LoaderType } from './config'
import type { DeviceTier } from './settings'

// ── Hesap ─────────────────────────────────────────────────────────────────────

/**
 * Sunucu lisanssız (online-mode=false) oyunculara açık olduğu için launcher yalnızca
 * kullanıcı adıyla giriş yapar; Microsoft hesabı gerekmez.
 */
export type AccountType = 'offline'

export interface Account {
  id: string
  username: string
  /** Tireli UUID: Minecraft'ın offline türetmesi, MD5("OfflinePlayer:<isim>"). */
  uuid: string
  type: AccountType
  createdAt: string
}

export interface AuthState {
  account: Account | null
  /** Son giriş yapılan kullanıcı adı (çıkıştan sonra giriş alanını doldurmak için). */
  lastUsername: string | null
  /**
   * Config'de hesap sunucusu (authServerUrl) varsa true: giriş ekranı şifre ve
   * "Kayıt ol" seçeneği gösterir. Yoksa yalnızca kullanıcı adı istenir.
   */
  passwordRequired: boolean
}

// ── Performans Paketi ─────────────────────────────────────────────────────────

export interface PerformanceModStatus {
  /** Modrinth proje kimliği (ör. "sodium"). */
  slug: string
  name: string
  /** Kurulu sürüm; kurulu değilse null. */
  installedVersion: string | null
}

export interface PerformancePackState {
  serverId: string
  minecraftVersion: string
  mods: PerformanceModStatus[]
  /** Paketteki (bu sürüm için bulunan) tüm modlar kurulu mu? */
  installed: boolean
  /** Son kurulumda bu Minecraft sürümü için Modrinth'te bulunamayan modlar. */
  unavailable: string[]
  /** Paket modlarının bağımlılığı olarak kurulan kütüphanelerin Modrinth proje kimlikleri. */
  dependencies: string[]
}

// ── Modrinth araması ve oyuncunun kurduğu modlar ─────────────────────────────

export type ModSide = 'required' | 'optional' | 'unsupported' | 'unknown'

/** Modrinth'te aranan / kurulan şey: mods/ klasörüne giden mod ya da shaderpacks/ klasörüne giden shader paketi. */
export type ModKind = 'mod' | 'shader'

export interface ModSearchHit {
  projectId: string
  slug: string
  title: string
  description: string
  author: string
  iconUrl: string | null
  downloads: number
  /** Sunucuda da kurulu olması gerekiyorsa "required" (Paper sunucusunda çalışmaz). */
  serverSide: ModSide
}

export interface ModSearchResult {
  hits: ModSearchHit[]
  total: number
  offset: number
}

export interface InstalledMod {
  projectId: string
  slug: string
  title: string
  version: string
  file: string
  iconUrl: string | null
  kind: ModKind
  /** Başka bir modun zorunlu bağımlılığı olarak otomatik kurulduysa true. */
  dependency: boolean
  /** Bağımlılıksa: onu gerektiren (oyuncunun kurduğu) modlar. Hepsi kaldırılınca bu da kaldırılır. */
  requiredBy?: string[]
}

export interface UserModsState {
  serverId: string
  minecraftVersion: string
  mods: InstalledMod[]
  /** Oyunda kurulu bir shader modu (Iris, Oculus, OptiFine …) varsa adı; yoksa null. Shader kurulurken Iris yalnızca bu null ise eklenir. */
  shaderMod: string | null
  /** Kurulu Performans Paketi modlarının slug'ları (aramada "Pakette" göstermek için). */
  packSlugs: string[]
}

/** Cihaz kalitesi seçiminin sonucu: hangi shader'ın kurulduğu. */
export interface QualityResult {
  tier: DeviceTier
  shaderTitle: string
}

export interface PerformancePackProgress {
  serverId: string
  progress: number
}

// ── Launcher güncellemesi ─────────────────────────────────────────────────────

export type UpdateState = 'checking' | 'available' | 'downloading' | 'downloaded' | 'none' | 'error'

export interface UpdateStatus {
  state: UpdateState
  /** Bulunan / indirilen sürüm. */
  version?: string
  /** İndirme ilerlemesi (0–1). */
  progress?: number
}

/** Açılış penceresinin (logo + çubuk) gösterdiği aşama. */
export type SplashStage = 'starting' | 'checking' | 'upToDate' | 'downloading' | 'ready' | 'updated'

export interface SplashStatus {
  stage: SplashStage
  /** Çubuğun hedef doluluğu (0–1). */
  progress: number
  /** true: çubuk doldu, açılış penceresi sönüp kapanıyor. */
  closing?: boolean
}

// ── Sunucu durumu ─────────────────────────────────────────────────────────────

export interface ServerStatus {
  serverId: string
  online: boolean
  players: { online: number; max: number }
  /** ms; çevrimdışıysa null. */
  pingMs: number | null
  checkedAt: string
}

// ── Sürümler ──────────────────────────────────────────────────────────────────

export type VersionChannel = 'release' | 'snapshot'

export interface GameVersion {
  /** Benzersiz anahtar: "<loader>:<mcVersion>[:<loaderVersion>]" */
  key: string
  loader: LoaderType
  minecraftVersion: string
  loaderVersion?: string
  channel: VersionChannel
  /** ISO tarih; listeler bu alana göre sıralanır (sürüm numarasına göre değil). */
  releaseTime: string
}

// ── Kurulum / indirme ─────────────────────────────────────────────────────────

export type InstallState = 'queued' | 'downloading' | 'paused' | 'completed' | 'cancelled' | 'failed'

export interface InstallTask {
  id: string
  versionKey: string
  state: InstallState
  downloadedBytes: number
  totalBytes: number
  bytesPerSecond: number
  error?: SerializedError
}

// ── Modpack ───────────────────────────────────────────────────────────────────

export interface ModpackSyncResult {
  serverId: string
  added: number
  updated: number
  removed: number
  unchanged: number
}

// ── Java ──────────────────────────────────────────────────────────────────────

export interface JavaInstallation {
  path: string
  majorVersion: number
  vendor: string
  source: 'auto' | 'custom'
}

// ── Başlatma ──────────────────────────────────────────────────────────────────

export type LaunchTarget =
  | { kind: 'server'; serverId: string }
  | { kind: 'free'; versionKey: string }

export const LAUNCH_STAGES = ['preparing', 'java', 'installing', 'modpack', 'starting'] as const
export type LaunchStage = (typeof LAUNCH_STAGES)[number]

export interface LaunchProgress {
  stage: LaunchStage
  /** Tüm sürecin ilerlemesi (0–1). */
  progress: number
}

export interface LaunchSession {
  id: string
  target: LaunchTarget
  startedAt: string
  pid: number
}

export interface GameExitEvent {
  sessionId: string
  exitCode: number
}

// ── Sistem ────────────────────────────────────────────────────────────────────

export interface SystemInfo {
  platform: string
  arch: string
  totalMemoryMb: number
  launcherVersion: string
  isDev: boolean
}
