/** Dil duyarlı tarih/sayı biçimlendirme (Intl). */

import type { GameVersion } from '@shared/types'

export function formatDate(iso: string, lang: string): string {
  return new Intl.DateTimeFormat(lang, { day: 'numeric', month: 'long', year: 'numeric' }).format(new Date(iso))
}

/** "3 gün önce" gibi göreli zaman. */
export function formatRelative(iso: string, lang: string): string {
  const diffSec = (Date.parse(iso) - Date.now()) / 1000
  const rtf = new Intl.RelativeTimeFormat(lang, { numeric: 'auto' })
  const units: Array<[Intl.RelativeTimeFormatUnit, number]> = [
    ['year', 31_536_000],
    ['month', 2_592_000],
    ['week', 604_800],
    ['day', 86_400],
    ['hour', 3_600],
    ['minute', 60]
  ]
  for (const [unit, seconds] of units) {
    if (Math.abs(diffSec) >= seconds) return rtf.format(Math.round(diffSec / seconds), unit)
  }
  return rtf.format(Math.round(diffSec), 'second')
}

export function formatMemory(mb: number): string {
  return mb >= 1024 ? `${(mb / 1024).toFixed(mb % 1024 === 0 ? 0 : 1)} GB` : `${mb} MB`
}

const LOADER_LABEL: Record<GameVersion['loader'], string> = {
  vanilla: 'Vanilla',
  fabric: 'Fabric',
  forge: 'Forge',
  neoforge: 'NeoForge',
  quilt: 'Quilt'
}

export function loaderLabel(loader: GameVersion['loader']): string {
  return LOADER_LABEL[loader]
}

/** Sürüm anahtarından ("fabric:1.21.10:0.17.2") okunur ad üretir. */
export function versionLabelFromKey(key: string): string {
  const [loader, mc] = key.split(':') as [GameVersion['loader'], string]
  return loader === 'vanilla' ? mc : `${LOADER_LABEL[loader] ?? loader} ${mc}`
}
