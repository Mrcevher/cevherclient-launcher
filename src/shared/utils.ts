/** Main ve renderer'ın ortak kullandığı küçük, saf yardımcılar. */

import type { Language, LocalizedText } from './types'

/**
 * LocalizedText'i istenen dile çevirir. Yoksa Türkçe arayüzde TR → EN, diğer dillerde
 * EN → TR, en son ilk bulunan değer (uzaktan config çoğunlukla yalnızca TR/EN içerir).
 */
export function localize(text: LocalizedText | undefined, lang: Language): string {
  if (text === undefined) return ''
  if (typeof text === 'string') return text
  const fallback = lang === 'tr' ? (text.tr ?? text.en) : (text.en ?? text.tr)
  return text[lang] ?? fallback ?? Object.values(text)[0] ?? ''
}

/**
 * Noktalı sürüm karşılaştırması ("0.1.10" > "0.1.9"). Sayı olmayan ekler
 * ("-beta") yok sayılır. a<b → -1, a=b → 0, a>b → 1
 */
export function compareVersions(a: string, b: string): number {
  const pa = a.split(/[.-]/).map((p) => parseInt(p, 10)).filter((n) => !Number.isNaN(n))
  const pb = b.split(/[.-]/).map((p) => parseInt(p, 10)).filter((n) => !Number.isNaN(n))
  const len = Math.max(pa.length, pb.length)
  for (let i = 0; i < len; i++) {
    const diff = (pa[i] ?? 0) - (pb[i] ?? 0)
    if (diff !== 0) return diff < 0 ? -1 : 1
  }
  return 0
}

/**
 * Minecraft sürümü için gereken Java majör sürümü — Faz 1 sezgiseli.
 * Faz 2'de bunun yerine sürüm metadata'sındaki javaVersion.majorVersion kullanılacak.
 * Yeni numaralandırma (26.x ve sonrası) → Java 25.
 */
export function requiredJavaMajor(mcVersion: string): number {
  const [first = 0, second = 0, third = 0] = mcVersion.split(/[.-]/).map((p) => parseInt(p, 10) || 0)
  if (first >= 26) return 25
  if (first !== 1) return 21
  if (second > 20 || (second === 20 && third >= 5)) return 21
  if (second >= 18) return 17
  if (second === 17) return 16
  return 8
}

/** Bayt değerini okunur biçime çevirir (1.2 MB). */
export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  const units = ['KB', 'MB', 'GB']
  let value = bytes / 1024
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit++
  }
  return `${value.toFixed(value < 10 ? 1 : 0)} ${units[unit]}`
}
