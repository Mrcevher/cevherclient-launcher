/**
 * Hata → kullanıcı mesajı çeviri katmanı (tek yer).
 *
 * Uygulamadaki her hata buradan geçer: kod i18n'den başlık/mesaja çevrilir,
 * önem derecesine göre ya SAO sistem mesajı (notice) ya da altıgen çerçeveli
 * tam ekran uyarı (critical) olarak gösterilir.
 */

import { AppError, ERROR_SEVERITY, type ErrorCode, type ErrorSeverity } from '@shared/errors'
import i18n from '../i18n'
import { useNotifyStore } from '../store/notify-store'

export interface UserFacingError {
  code: ErrorCode
  severity: ErrorSeverity
  title: string
  message: string
  detail?: string
}

/** Herhangi bir hatayı, gösterilmeye hazır TR/EN metne çevirir. */
export function describeError(error: unknown): UserFacingError {
  const err = AppError.from(error)
  const meta = err.meta ?? {}
  const base = `errors.${err.code}`

  // Hataya özgü bir neden (meta.reason) varsa ve çevirisi tanımlıysa onu kullan.
  const reasonKey = typeof meta.reason === 'string' ? `${base}.reasons.${meta.reason}` : null
  const message =
    reasonKey && i18n.exists(reasonKey) ? i18n.t(reasonKey, meta) : i18n.t(`${base}.message`, meta)

  return {
    code: err.code,
    severity: ERROR_SEVERITY[err.code],
    title: i18n.t(`${base}.title`),
    message,
    detail: err.detail
  }
}

export interface ReportOptions {
  /** Kritik uyarıda "Tekrar dene" butonu gösterilir. */
  retry?: () => void
  /** Önem derecesini zorla (ör. arka plan hatasını sadece bildirim olarak göster). */
  severity?: ErrorSeverity
}

/** Hatayı kullanıcıya gösterir. Bileşenler hatayı kendileri biçimlendirmez. */
export function reportError(error: unknown, options: ReportOptions = {}): void {
  const described = describeError(error)
  const severity = options.severity ?? described.severity
  console.warn('[cevher]', described.code, described.detail ?? '')

  const notify = useNotifyStore.getState()
  if (severity === 'critical') {
    notify.showCritical({ ...described, severity }, options.retry)
  } else {
    notify.push({ kind: 'error', title: described.title, message: described.message })
  }
}
