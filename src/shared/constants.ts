/** Uygulama genelindeki sabitler. */

export const APP_NAME = 'Cevherclient'

/** RAM slider'ı: en az bu kadar, bu adımla; sisteme en az bu kadar bırakılır. */
export const MIN_MEMORY_MB = 1024
export const MEMORY_STEP_MB = 512
export const RESERVED_SYSTEM_MEMORY_MB = 2048

/** Offline kullanıcı adı kuralı (Minecraft ile aynı). */
export const USERNAME_PATTERN = /^[A-Za-z0-9_]{3,16}$/

/** Dashboard'daki sunucu durumu yenileme aralığı. */
export const STATUS_REFRESH_MS = 30_000

/** LINK START geçiş animasyonunun süresi. */
export const LINK_START_DURATION_MS = 1500

/** Sistem RAM'ine göre RAM slider'ının üst sınırı. */
export function maxMemoryFor(totalMemoryMb: number): number {
  const usable = totalMemoryMb - RESERVED_SYSTEM_MEMORY_MB
  const stepped = Math.floor(usable / MEMORY_STEP_MB) * MEMORY_STEP_MB
  return Math.max(MIN_MEMORY_MB, stepped)
}
