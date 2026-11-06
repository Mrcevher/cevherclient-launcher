/** Kalıcı kullanıcı ayarları (userData/settings.json). */

export const THEME_IDS = ['diamond', 'amethyst', 'gold'] as const
export type ThemeId = (typeof THEME_IDS)[number]

export const LANGUAGES = ['tr', 'en', 'de', 'fr', 'it', 'ja'] as const
export type Language = (typeof LANGUAGES)[number]

/** Cihaz kalitesi: tek fark kurulan shader'dır (düşük → SuperDuperVanilla, orta → Photon, yüksek → Complementary Unbound). */
export const DEVICE_TIERS = ['low', 'medium', 'high'] as const
export type DeviceTier = (typeof DEVICE_TIERS)[number]

/** Eski varsayılan JVM argümanları; hâlâ bunlar duruyorsa kalite seçilince optimize setle değiştirilir. */
export const LEGACY_JVM_ARGS = '-XX:+UseG1GC -XX:+ParallelRefProcEnabled'

/** Üç kalite seviyesinde de aynı uygulanan, istemci için ayarlanmış G1 bayrakları. */
export const OPTIMIZED_JVM_ARGS = [
  '-XX:+UseG1GC',
  '-XX:+ParallelRefProcEnabled',
  '-XX:MaxGCPauseMillis=50',
  '-XX:+UnlockExperimentalVMOptions',
  '-XX:+DisableExplicitGC',
  '-XX:G1NewSizePercent=30',
  '-XX:G1MaxNewSizePercent=40',
  '-XX:G1HeapRegionSize=8M',
  '-XX:G1ReservePercent=20',
  '-XX:G1HeapWastePercent=5',
  '-XX:G1MixedGCCountTarget=4',
  '-XX:InitiatingHeapOccupancyPercent=15',
  '-XX:G1MixedGCLiveThresholdPercent=90',
  '-XX:G1RSetUpdatingPauseTimePercent=5',
  '-XX:SurvivorRatio=32',
  '-XX:MaxTenuringThreshold=1',
  '-XX:+PerfDisableSharedMem',
  '-Dfile.encoding=UTF-8'
].join(' ')

export interface Resolution {
  width: number
  height: number
  fullscreen: boolean
}

export interface Settings {
  /** null → otomatik Java seçimi. */
  javaPath: string | null
  /** Oyuna ayrılan RAM (MB). */
  memoryMb: number
  resolution: Resolution
  /** Ek JVM argümanları (boşlukla ayrılmış). */
  jvmArgs: string
  theme: ThemeId
  /** null → temanın varsayılan accent rengi. */
  accentColor: string | null
  language: Language
  /** Dashboard'da seçili sunucu profili. */
  selectedServerId: string | null
  /**
   * Uzaktan config adresi (http(s) URL veya dosya yolu). null → yerleşik varsayılan.
   * Arayüzde gösterilmez; settings.json veya CEVHER_CONFIG_URL ile değiştirilir.
   */
  remoteConfigUrl: string | null
  /** Sunucu profili Fabric ile mi açılsın? Kapalıysa vanilla: modlar ve çıkışta kapanma çalışmaz. */
  useFabric: boolean
  /** Performans Paketi, kurulu değilse ilk Oyna'da otomatik kurulsun mu? */
  perfPackAutoInstall: boolean
  /** Girişten sonra seçilen cihaz kalitesi; null → henüz seçilmedi (seçim ekranı gösterilir). */
  deviceTier: DeviceTier | null
}

export type SettingsPatch = Partial<Settings>

export const DEFAULT_SETTINGS: Settings = {
  javaPath: null,
  memoryMb: 4096,
  resolution: { width: 1280, height: 720, fullscreen: false },
  jvmArgs: OPTIMIZED_JVM_ARGS,
  theme: 'diamond',
  accentColor: null,
  language: 'tr',
  selectedServerId: null,
  remoteConfigUrl: null,
  useFabric: true,
  perfPackAutoInstall: true,
  deviceTier: null
}
