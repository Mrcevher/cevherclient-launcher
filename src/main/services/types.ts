/**
 * Servis arayüzleri. IPC handler'ları ve diğer servisler yalnızca bu arayüzlere
 * bağımlıdır; Faz 1'deki mock implementasyonlar gerçekleriyle değiştirildiğinde
 * ne IPC katmanı ne de UI kodu değişir (bkz. container.ts).
 */

import type {
  AuthState,
  DeviceTier,
  Account,
  GameVersion,
  InstallTask,
  JavaInstallation,
  LaunchSession,
  LaunchTarget,
  LoaderType,
  ModKind,
  ModpackSyncResult,
  ModSearchResult,
  PerformancePackState,
  QualityResult,
  UserModsState,
  RemoteConfigSnapshot,
  ServerProfile,
  ServerStatus,
  Settings,
  SettingsPatch
} from '@shared/types'
import type { SiteService } from './site/site-service'

/** 0–1 arası ilerleme bildirimi. */
export type ProgressCallback = (progress: number) => void

/** Sürüm metadata'sındaki javaVersion alanı (ör. { component: "java-runtime-epsilon", majorVersion: 25 }). */
export interface JavaRequirement {
  /** Mojang Java runtime bileşeni; indirme için kullanılır. */
  component: string
  majorVersion: number
}

/** Oyunu başlatmak için gereken kimlik bilgileri (yalnızca main process'te kalır). */
export interface LaunchCredentials {
  account: Account
  /** Lisanssız oyunda anlamsız ama zorunlu argüman; rastgele üretilir. */
  accessToken: string
  userType: 'legacy'
}

/** Hesap sunucusundan alınan giriş bileti (yalnızca main process'te ve oyunun kendisinde kalır). */
export interface JoinTicket {
  /** Hesabın sunucudaki yazılışı. */
  username: string
  /** Bilete özel sır; eski sunucu sürümleri göndermez. */
  secret: string | null
}

export interface AuthService {
  getState(): Promise<AuthState>
  /** Oturum açmış hesap; yoksa AUTH_FAILED fırlatır. */
  requireAccount(): Promise<Account>
  /** Başlatma için hesap + belirteç. */
  getLaunchCredentials(): Promise<LaunchCredentials>
  /**
   * Sunucuya bağlanmadan hemen önce: hesap sunucusundan tek kullanımlık giriş bileti alır,
   * böylece oyuncu oyunda şifre yazmadan içeri girer. Bilete özel sır, oyunun bileti
   * kendisinin aldığını kanıtlaması için oyuna verilir. Hesap sunucusu yoksa null döner.
   */
  prepareServerJoin(): Promise<JoinTicket | null>
  /** Giriş: hesap sunucusu varsa şifre zorunlu; ad Minecraft kuralına (3–16, harf/rakam/_) uymalı. */
  login(username: string, password?: string): Promise<AuthState>
  /** Hesap sunucusunda yeni hesap açar ve giriş yapar. */
  register(username: string, password: string): Promise<AuthState>
  logout(): Promise<AuthState>
  /** Şifre değiştirir; diğer cihazlardaki oturumlar kapanır, bu cihaz girişli kalır. */
  changePassword(currentPassword: string, newPassword: string): Promise<AuthState>
  /**
   * Oturum belirteciyle CevherAuth'un site köprüsüne istek (Mağaza / Profil). Hatalar STORE_FAILED;
   * oturum düşmüşse AUTH_FAILED (sessionExpired) ve yerel oturum kapanır.
   */
  siteRequest(path: string, body?: Record<string, string>): Promise<Record<string, unknown>>
}

export interface RemoteConfigService {
  /** refresh=false iken önbellekteki config döner. */
  get(refresh?: boolean): Promise<RemoteConfigSnapshot>
  /** Bilinmeyen id için CONFIG_INVALID fırlatır. */
  getServer(serverId: string): Promise<ServerProfile>
}

export interface ServerStatusService {
  /** Faz 2: main process'te gerçek server list ping. */
  getStatus(server: ServerProfile): Promise<ServerStatus>
}

export interface VersionService {
  /** releaseTime'a göre yeniden eskiye sıralı liste. */
  list(loader: LoaderType): Promise<GameVersion[]>
  find(versionKey: string): Promise<GameVersion | undefined>
  /** Minecraft sürümünün metadata'sındaki Java gereksinimi (loader'lar vanilla'dan devralır). */
  getJavaRequirement(minecraftVersion: string): Promise<JavaRequirement>
}

export interface InstallService {
  start(versionKey: string): Promise<InstallTask>
  pause(taskId: string): Promise<InstallTask>
  resume(taskId: string): Promise<InstallTask>
  cancel(taskId: string): Promise<InstallTask>
  listTasks(): Promise<InstallTask[]>
  listInstalled(): Promise<string[]>
  /**
   * Başlatma öncesi: sürümü doğrular, eksik/bozuk dosyaları tamamlar (SHA1 doğrulamalı,
   * devam edebilen). Kurulu sürümün id'sini (versions/<id>) döndürür.
   */
  ensureInstalled(versionKey: string, onProgress: ProgressCallback): Promise<string>
}

export interface ModpackService {
  /** Faz 3: .mrpack manifest senkronizasyonu (ekle / güncelle / kaldır, kullanıcı ayarlarını koru). */
  sync(server: ServerProfile, onProgress: ProgressCallback): Promise<ModpackSyncResult>
}

export interface JavaService {
  detect(): Promise<JavaInstallation[]>
  /** Gerekli majör sürüme uygun Java'yı döndürür; yoksa JAVA_NOT_FOUND fırlatır. Hiçbir şey indirmez. */
  resolve(requiredMajor: number): Promise<JavaInstallation>
  /** resolve() gibi, ancak uygun Java yoksa gereksinimdeki runtime'ı indirir (başlatma akışı). */
  ensure(requirement: JavaRequirement, onProgress: ProgressCallback): Promise<JavaInstallation>
}

export interface SettingsService {
  get(): Promise<Settings>
  update(patch: SettingsPatch): Promise<Settings>
}

export interface PerformancePackService {
  /** Yalnızca diske bakar; ağ isteği yapmaz. */
  getState(server: ServerProfile): Promise<PerformancePackState>
  /** Modrinth'ten bu sürüme uygun modları indirip kurar / günceller. */
  install(server: ServerProfile, onProgress: ProgressCallback): Promise<PerformancePackState>
  remove(server: ServerProfile): Promise<PerformancePackState>
}

export interface QualityService {
  /**
   * Cihaz kalitesini uygular: Performans Paketi (Iris + modlar), seviyenin shader'ı, optimize oyun/launcher
   * ayarları. Seviyeler arasındaki tek fark shader'dır. Bitince settings.deviceTier kaydedilir.
   */
  apply(server: ServerProfile, tier: DeviceTier, onProgress: ProgressCallback, remember?: boolean): Promise<QualityResult>
}

export interface UserModsService {
  /** Modrinth'te sunucunun sürümüne uygun, istemcide çalışan Fabric modları ya da shader paketleri. */
  search(server: ServerProfile, query: string, offset: number, kind: ModKind): Promise<ModSearchResult>
  list(server: ServerProfile): Promise<UserModsState>
  /**
   * Modu/shader'ı ve zorunlu bağımlılıklarını kurar (Performans Paketindekiler atlanır).
   * Shader için oyunda shader modu (Iris, Oculus, OptiFine …) yoksa Iris de otomatik eklenir.
   */
  install(server: ServerProfile, projectId: string, kind: ModKind): Promise<UserModsState>
  remove(server: ServerProfile, projectId: string): Promise<UserModsState>
}

export interface LaunchService {
  launch(target: LaunchTarget): Promise<LaunchSession>
  /** Başlatılan oyun hâlâ açık mı (penceresi hazır olduktan sonra, kapanana kadar). */
  isGameRunning(): boolean
  /** Hedef anahtarı (sunucu id / sürüm anahtarı) → son oynanma (ISO). */
  getHistory(): Promise<Record<string, string>>
}

/** Tüm servislerin bir arada tutulduğu kap. */
export interface Services {
  auth: AuthService
  site: SiteService
  config: RemoteConfigService
  status: ServerStatusService
  versions: VersionService
  install: InstallService
  modpack: ModpackService
  java: JavaService
  settings: SettingsService
  launch: LaunchService
  performance: PerformancePackService
  userMods: UserModsService
  quality: QualityService
}
