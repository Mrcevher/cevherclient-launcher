/**
 * Mock servislerin ortak yardımcıları.
 *
 * Hata ekranlarını denemek için uygulamayı şu ortam değişkeniyle başlatın:
 *   CEVHER_MOCK_FAIL=LAUNCH_FAILED,AUTH_FAILED npm run dev
 * Listelenen her kod, ilgili mock serviste yapay olarak fırlatılır.
 */

import { AppError, isErrorCode, type ErrorCode, type ErrorMeta } from '@shared/errors'

const forcedFailures = new Set<ErrorCode>(
  (process.env.CEVHER_MOCK_FAIL ?? '')
    .split(',')
    .map((s) => s.trim().toUpperCase())
    .filter(isErrorCode)
)

/** CEVHER_MOCK_FAIL içinde bu kod varsa fırlatır. */
export function maybeFail(code: ErrorCode, meta?: ErrorMeta): void {
  if (forcedFailures.has(code)) throw new AppError(code, 'CEVHER_MOCK_FAIL ile tetiklendi', meta)
}

export function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

export function randomInt(min: number, max: number): number {
  return Math.floor(min + Math.random() * (max - min + 1))
}

/** Süreyi adımlara bölerek ilerleme bildiren sahte iş. */
export async function simulateWork(
  durationMs: number,
  onProgress: (progress: number) => void,
  steps = 10
): Promise<void> {
  for (let i = 1; i <= steps; i++) {
    await delay(durationMs / steps)
    onProgress(i / steps)
  }
}
