/**
 * Tipli IPC sözleşmesi — main, preload ve renderer'ın ortak kaynağı.
 *
 * - IpcInvokeContract: renderer → main istek/yanıt kanalları.
 * - IpcEventContract: main → renderer olay kanalları.
 *
 * Yeni bir kanal eklemek için: buraya imzasını ekle, INVOKE_CHANNELS/EVENT_CHANNELS
 * listesine adını yaz, main/ipc altında handler'ını kaydet. Preload yalnızca bu
 * listelerdeki kanallara izin verir.
 */

import type { IpcResult } from './errors'
import type {
  AuthState,
  DeviceTier,
  GameExitEvent,
  GameVersion,
  InstallTask,
  JavaInstallation,
  LaunchProgress,
  LaunchSession,
  LaunchTarget,
  LoaderType,
  ModKind,
  ModSearchResult,
  PerformancePackProgress,
  PerformancePackState,
  QualityResult,
  RemoteConfigSnapshot,
  ServerStatus,
  Settings,
  SettingsPatch,
  SiteProfile,
  SplashStatus,
  StoreListing,
  StorePurchase,
  SystemInfo,
  UpdateStatus,
  UserModsState
} from './types'

export interface IpcInvokeContract {
  // Pencere / sistem
  'window:minimize': () => void
  'window:close': () => void
  'system:getInfo': () => SystemInfo
  'shell:openExternal': (url: string) => void
  'dialog:pickJava': () => string | null

  // AuthService
  'auth:getState': () => AuthState
  /** Giriş: hesap sunucusu varsa kullanıcı adı + şifre, yoksa yalnızca kullanıcı adı. */
  'auth:login': (username: string, password?: string) => AuthState
  /** Hesap sunucusunda yeni hesap açar ve giriş yapar. */
  'auth:register': (username: string, password: string) => AuthState
  'auth:logout': () => AuthState
  /** Hesap şifresini değiştirir (diğer cihazlardaki oturumlar kapanır). */
  'auth:changePassword': (currentPassword: string, newPassword: string) => AuthState

  // Web sitesi (LeaderOS) — CevherAuth köprüsü üzerinden
  /** Site hesabı: eşleşme, kredi bakiyesi, son siparişler. */
  'site:profile': () => SiteProfile
  'store:listing': () => StoreListing
  /** Ürünü site kredisiyle satın alır. */
  'store:buy': (productId: string) => StorePurchase
  /** Sitenin kendi kökünden bir sayfanın HTML'i (haberler için; renderer ayrıştırır). */
  'site:fetchPage': (url: string) => string

  // PerformancePackService
  'mods:getPack': (serverId: string) => PerformancePackState
  'mods:installPack': (serverId: string) => PerformancePackState
  'mods:removePack': (serverId: string) => PerformancePackState
  /** Modrinth'te sunucunun sürümüne uygun Fabric modlarını ya da shader paketlerini arar (boş sorgu: en popülerler). */
  'mods:search': (serverId: string, query: string, offset: number, kind: ModKind) => ModSearchResult
  'mods:listUser': (serverId: string) => UserModsState
  /** Modu/shader'ı ve zorunlu bağımlılıklarını kurar (shader için, shader modu yoksa Iris de eklenir). */
  'mods:installUser': (serverId: string, projectId: string, kind: ModKind) => UserModsState
  'mods:removeUser': (serverId: string, projectId: string) => UserModsState

  /**
   * Cihaz kalitesini uygular: Performans Paketi + modlar + seçilen shader (ve Iris) kurulur, optimize
   * ayarlar yazılır; remember açıksa settings.deviceTier kaydedilir (kapalıysa bir sonraki açılışta yine sorulur). İlerleme 'mods:progress' olayıyla gelir.
   */
  'quality:apply': (serverId: string, tier: DeviceTier, remember: boolean) => QualityResult

  // RemoteConfigService
  'config:get': (refresh?: boolean) => RemoteConfigSnapshot

  // ServerStatusService
  'status:get': (serverId: string) => ServerStatus

  // VersionService
  'versions:list': (loader: LoaderType) => GameVersion[]

  // InstallService
  'install:start': (versionKey: string) => InstallTask
  'install:pause': (taskId: string) => InstallTask
  'install:resume': (taskId: string) => InstallTask
  'install:cancel': (taskId: string) => InstallTask
  'install:listTasks': () => InstallTask[]
  'install:listInstalled': () => string[]

  // JavaService
  'java:detect': () => JavaInstallation[]
  /** Verilen Minecraft sürümü için kullanılacak Java (bulunamazsa null). */
  'java:getActive': (minecraftVersion: string) => JavaInstallation | null

  // SettingsService
  'settings:get': () => Settings
  'settings:update': (patch: SettingsPatch) => Settings

  // LaunchService
  'launch:start': (target: LaunchTarget) => LaunchSession
  'launch:history': () => Record<string, string>

  // Launcher güncellemesi (yalnızca paketlenmiş sürümde etkin)
  'update:getStatus': () => UpdateStatus
  /** İndirilmiş güncellemeyi kurmak için launcher'ı yeniden başlatır. */
  'update:install': () => void
}

export interface IpcEventContract {
  'install:progress': InstallTask
  'launch:progress': LaunchProgress
  'launch:exit': GameExitEvent
  'update:status': UpdateStatus
  'mods:progress': PerformancePackProgress
  /** Yalnızca açılış penceresine gönderilir. */
  'splash:status': SplashStatus
}

export type InvokeChannel = keyof IpcInvokeContract
export type InvokeArgs<C extends InvokeChannel> = Parameters<IpcInvokeContract[C]>
export type InvokeReturn<C extends InvokeChannel> = ReturnType<IpcInvokeContract[C]>
export type EventChannel = keyof IpcEventContract

/** Preload'un izin verdiği kanallar (çalışma zamanı beyaz listesi). */
export const INVOKE_CHANNELS = [
  'window:minimize',
  'window:close',
  'system:getInfo',
  'shell:openExternal',
  'dialog:pickJava',
  'auth:getState',
  'auth:login',
  'auth:register',
  'auth:logout',
  'auth:changePassword',
  'site:profile',
  'store:listing',
  'store:buy',
  'site:fetchPage',
  'mods:getPack',
  'mods:installPack',
  'mods:removePack',
  'mods:search',
  'mods:listUser',
  'mods:installUser',
  'mods:removeUser',
  'quality:apply',
  'config:get',
  'status:get',
  'versions:list',
  'install:start',
  'install:pause',
  'install:resume',
  'install:cancel',
  'install:listTasks',
  'install:listInstalled',
  'java:detect',
  'java:getActive',
  'settings:get',
  'settings:update',
  'launch:start',
  'launch:history',
  'update:getStatus',
  'update:install'
] as const satisfies readonly InvokeChannel[]

export const EVENT_CHANNELS = [
  'install:progress',
  'launch:progress',
  'launch:exit',
  'update:status',
  'mods:progress',
  'splash:status'
] as const satisfies readonly EventChannel[]

/** window.cevher olarak renderer'a açılan API. */
export interface CevherApi {
  /** Tipli istek: her zaman IpcResult döner, asla fırlatmaz. */
  invoke<C extends InvokeChannel>(
    channel: C,
    ...args: InvokeArgs<C>
  ): Promise<IpcResult<InvokeReturn<C>>>
  /** Olay aboneliği; aboneliği iptal eden fonksiyonu döndürür. */
  on<E extends EventChannel>(event: E, listener: (payload: IpcEventContract[E]) => void): () => void
  /** process.platform değeri (pencere kontrollerinin yerleşimi için). */
  platform: string
}
