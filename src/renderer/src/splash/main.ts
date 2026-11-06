/**
 * Açılış penceresi: saydam zeminde CevherCraft logosu, altında durum yazısı, yüzde ve çubuk.
 * Main process 'splash:status' ile aşamayı ve çubuğun hedefini gönderir (bkz. src/main/splash.ts).
 * React yüklenmez: pencere anında açılsın.
 */

import './splash.css'
import type { Language, SplashStage } from '@shared/types'
import logo from '../assets/cevhercraft-logo.png'
import de from '../i18n/locales/de.json'
import en from '../i18n/locales/en.json'
import fr from '../i18n/locales/fr.json'
import it from '../i18n/locales/it.json'
import ja from '../i18n/locales/ja.json'
import tr from '../i18n/locales/tr.json'

const LOCALES: Record<Language, { splash: Record<SplashStage, string> }> = { tr, en, de, fr, it, ja }

/** Çubuğun CSS geçiş süresi (splash.css .bar); yüzde sayacı da bu sürede ilerler. */
const FILL_MS = 450

const lang = (new URLSearchParams(location.search).get('lang') ?? 'tr') as Language
const texts = (LOCALES[lang] ?? LOCALES.tr).splash
document.documentElement.lang = lang

const logoEl = document.getElementById('logo') as HTMLImageElement
const label = document.getElementById('label') as HTMLElement
const percent = document.getElementById('percent') as HTMLElement
const bar = document.getElementById('bar') as HTMLElement

logoEl.src = logo
label.textContent = texts.starting

let shown = 0
let target = 0
let frame = 0

/** Çubuğu hedefe ilerletir; yüzde aynı eğriyle (ease-out cubic) sayar. Geri gitmez. */
function fillTo(next: number): void {
  if (next <= target) return
  target = next
  bar.style.width = `${next * 100}%`
  cancelAnimationFrame(frame)
  const from = shown
  const start = performance.now()
  const step = (now: number): void => {
    const t = Math.min(1, (now - start) / FILL_MS)
    shown = from + (next - from) * (1 - (1 - t) ** 3)
    percent.textContent = `${Math.round(shown * 100)}%`
    if (t < 1) frame = requestAnimationFrame(step)
  }
  frame = requestAnimationFrame(step)
}

window.cevher.on('splash:status', ({ stage, progress, closing }) => {
  label.textContent = texts[stage]
  fillTo(progress)
  if (closing) document.body.classList.add('closing')
})
