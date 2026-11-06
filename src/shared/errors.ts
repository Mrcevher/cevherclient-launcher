/**
 * Cevherclient tipli hata altyapısı.
 *
 * Main process'teki tüm servisler hata fırlatırken `AppError` kullanır. IPC sınırında
 * hata `SerializedError` olarak taşınır (Electron, Error sınıfının özel alanlarını
 * kopyalamaz). Renderer tarafında bu kodlar tek bir katmanda (renderer/src/lib/errors.ts)
 * TR/EN kullanıcı dostu mesajlara çevrilir.
 */

export const ERROR_CODES = [
  'JAVA_NOT_FOUND',
  'NO_INTERNET',
  'AUTH_FAILED',
  'DOWNLOAD_FAILED',
  'LAUNCH_FAILED',
  'SERVER_OFFLINE',
  'MODPACK_SYNC_FAILED',
  'STORE_FAILED',
  // Spesifikasyondaki listeye ek olarak, altyapının kendi ihtiyaçları:
  'CONFIG_INVALID',
  'LAUNCHER_OUTDATED',
  'UNKNOWN'
] as const

export type ErrorCode = (typeof ERROR_CODES)[number]

/**
 * notice   → SAO sistem mesajı tarzı pop-up
 * critical → altıgen çerçeveli tam ekran uyarı
 */
export type ErrorSeverity = 'notice' | 'critical'

export const ERROR_SEVERITY: Record<ErrorCode, ErrorSeverity> = {
  JAVA_NOT_FOUND: 'critical',
  NO_INTERNET: 'notice',
  AUTH_FAILED: 'notice',
  DOWNLOAD_FAILED: 'notice',
  LAUNCH_FAILED: 'critical',
  SERVER_OFFLINE: 'notice',
  MODPACK_SYNC_FAILED: 'critical',
  STORE_FAILED: 'notice',
  CONFIG_INVALID: 'critical',
  LAUNCHER_OUTDATED: 'critical',
  UNKNOWN: 'notice'
}

/** i18n mesajlarına enterpolasyon için taşınan ek bilgiler (ör. sunucu adı). */
export type ErrorMeta = Record<string, string | number>

/** IPC üzerinden taşınabilen düz hata nesnesi. */
export interface SerializedError {
  code: ErrorCode
  /** Geliştiriciye yönelik teknik ayrıntı; kullanıcıya "detay" olarak gösterilir. */
  detail?: string
  meta?: ErrorMeta
}

export function isErrorCode(value: unknown): value is ErrorCode {
  return typeof value === 'string' && (ERROR_CODES as readonly string[]).includes(value)
}

export class AppError extends Error {
  readonly code: ErrorCode
  readonly detail?: string
  readonly meta?: ErrorMeta

  constructor(code: ErrorCode, detail?: string, meta?: ErrorMeta) {
    super(detail ? `${code}: ${detail}` : code)
    this.name = 'AppError'
    this.code = code
    this.detail = detail
    this.meta = meta
  }

  toJSON(): SerializedError {
    return { code: this.code, detail: this.detail, meta: this.meta }
  }

  /** Bilinmeyen herhangi bir değeri AppError'a dönüştürür. */
  static from(value: unknown, fallback: ErrorCode = 'UNKNOWN'): AppError {
    if (value instanceof AppError) return value
    if (isSerializedError(value)) return new AppError(value.code, value.detail, value.meta)
    if (value instanceof Error) return new AppError(fallback, value.message)
    return new AppError(fallback, typeof value === 'string' ? value : undefined)
  }
}

export function isSerializedError(value: unknown): value is SerializedError {
  return (
    typeof value === 'object' &&
    value !== null &&
    isErrorCode((value as { code?: unknown }).code)
  )
}

/** Tüm IPC çağrılarının döndürdüğü sonuç zarfı. */
export type IpcResult<T> = { ok: true; data: T } | { ok: false; error: SerializedError }
