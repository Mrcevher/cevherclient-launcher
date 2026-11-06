/**
 * Sitenin (LeaderOS) blog sayfalarından haber akışı. LeaderOS API'sinde blog uç noktası olmadığı
 * için herkese açık /blog sayfası ve yazı sayfaları okunur. HTML main process'te indirilir
 * (yalnızca sitenin kendi kökünden), burada DOMParser ile ayrıştırılır: DOMParser betik
 * çalıştırmaz, görsel yüklemez; içerik gösterilirken de yalnızca beyaz listedeki etiketler çizilir.
 */

import { useConfigStore } from '../store/config-store'
import { call } from './api'

/** Sitenin kökü (main process'teki SiteService.siteOrigin ile aynı kural). */
export function siteOrigin(): string | undefined {
  const links = useConfigStore.getState().snapshot?.config.links
  for (const candidate of [links?.website, links?.store, links?.credits]) {
    if (!candidate) continue
    try {
      return new URL(candidate).origin
    } catch {
      // Sonrakini dene.
    }
  }
  return undefined
}

export interface SitePost {
  url: string
  title: string
  /** ISO tarih (bulunabildiyse). */
  date?: string
  image?: string
  /** Yazının içerik kökü (ayrıştırılmış, henüz çizilmemiş). */
  content?: Element
}

const MAX_POSTS = 10
const SKIP_PATH = /\/(categories|category|tags?|search|page)(\/|$)/i
const CONTENT_SELECTORS = [
  '.news-content',
  '.post-content',
  '.blog-content',
  '.article-content',
  '.news-body',
  '.post-body',
  '.blog-post',
  'article',
  '.card-body'
]

function parse(html: string): Document {
  return new DOMParser().parseFromString(html, 'text/html')
}

function abs(href: string | null | undefined, base: string): string | undefined {
  if (!href) return undefined
  try {
    return new URL(href, base).href
  } catch {
    return undefined
  }
}

function textOf(el: Element | null | undefined): string {
  return (el?.textContent ?? '').replace(/\s+/g, ' ').trim()
}

/** img src; lazyload kullanan temalarda asıl adres data-src'dedir. */
function imgSrc(img: Element | null, base: string): string | undefined {
  if (!img) return undefined
  const src = img.getAttribute('data-src') || img.getAttribute('src')
  const url = abs(src, base)
  return url && /^https:/i.test(url) && !/\/loaders\//.test(url) ? url : undefined
}

/** "12.03.2026", "2026-03-12", "12 Mart 2026" gibi tarihleri ISO'ya çevirir. */
const MONTHS: Record<string, number> = {
  ocak: 1, şubat: 2, subat: 2, mart: 3, nisan: 4, mayıs: 5, mayis: 5, haziran: 6, temmuz: 7, ağustos: 8, agustos: 8,
  eylül: 9, eylul: 9, ekim: 10, kasım: 11, kasim: 11, aralık: 12, aralik: 12,
  january: 1, february: 2, march: 3, april: 4, may: 5, june: 6, july: 7, august: 8, september: 9, october: 10, november: 11, december: 12
}
function findDate(text: string): string | undefined {
  let m = text.match(/\b(\d{4})-(\d{2})-(\d{2})(?:[ T](\d{2}):(\d{2}))?/)
  if (m) return new Date(`${m[1]}-${m[2]}-${m[3]}T${m[4] ?? '12'}:${m[5] ?? '00'}:00`).toISOString()
  m = text.match(/\b(\d{1,2})[./](\d{1,2})[./](\d{4})\b/)
  if (m) return new Date(Number(m[3]), Number(m[2]) - 1, Number(m[1]), 12).toISOString()
  m = text.toLowerCase().match(/\b(\d{1,2})\s+([a-zçğıöşü]+)\s+(\d{4})\b/)
  if (m && MONTHS[m[2]!]) return new Date(Number(m[3]), MONTHS[m[2]!]! - 1, Number(m[1]), 12).toISOString()
  return undefined
}

function isPostUrl(url: URL, origin: string): boolean {
  if (url.origin !== origin) return false
  const path = url.pathname.replace(/\/+$/, '')
  return /^\/blog\/.+/i.test(path) && !SKIP_PATH.test(path)
}

/** Liste sayfasındaki kart: bağlantının en yakın, görsel/başlık içeren atası. */
function cardOf(a: Element): Element {
  let el: Element = a
  for (let i = 0; i < 4 && el.parentElement; i++) {
    el = el.parentElement
    if (el.querySelector('img') && textOf(el).length > textOf(a).length) return el
  }
  return a
}

function contentRoot(doc: Document): Element | undefined {
  doc.querySelectorAll('script, style, noscript, iframe, form, nav, header, footer, .menu-overlay, .user-wrap, .seam-band').forEach((e) => e.remove())
  let best: Element | undefined
  let bestLen = 0
  for (const sel of CONTENT_SELECTORS) {
    doc.querySelectorAll(sel).forEach((el) => {
      const len = textOf(el).length
      if (len > bestLen) {
        best = el
        bestLen = len
      }
    })
    if (best && bestLen > 40) return best
  }
  return best ?? doc.querySelector('.news-section, main, .pop-page-wrapper') ?? undefined
}

async function loadPost(url: string, hint: Partial<SitePost>): Promise<SitePost> {
  const doc = parse(await call('site:fetchPage', url))
  const meta = (p: string): string | undefined => doc.querySelector(`meta[property="${p}"], meta[name="${p}"]`)?.getAttribute('content') ?? undefined
  const siteName = meta('og:site_name')

  const date =
    meta('article:published_time') ??
    doc.querySelector('time[datetime]')?.getAttribute('datetime') ??
    hint.date ??
    undefined
  const content = contentRoot(doc)
  // Başlık: içerikteki ilk başlık, yoksa og:title (site adı değilse), yoksa listedeki kart başlığı.
  const heading = content?.querySelector('h1, h2, .post-title, .news-title')
  const ogTitle = meta('og:title')
  const title = textOf(heading) || (ogTitle && ogTitle !== siteName ? ogTitle : '') || hint.title || url
  if (heading && textOf(heading) === title) heading.remove()

  const ogImage = abs(meta('og:image'), url)
  const image = hint.image ?? (ogImage && !/logo/i.test(ogImage) ? ogImage : undefined)

  let isoDate: string | undefined
  if (date && Number.isFinite(Date.parse(date))) isoDate = new Date(date).toISOString()
  else isoDate = findDate(textOf(content).slice(0, 400))

  return { url, title, date: isoDate, image, content }
}

/** Blog listesini ve yazıları okur; yeniden eskiye sıralı döner. */
export async function loadSitePosts(): Promise<SitePost[]> {
  const origin = siteOrigin()
  if (!origin) return []
  const doc = parse(await call('site:fetchPage', '/blog'))

  const hints = new Map<string, Partial<SitePost>>()
  doc.querySelectorAll('a[href]').forEach((a) => {
    let url: URL
    try {
      url = new URL(a.getAttribute('href')!, origin)
    } catch {
      return
    }
    url.hash = ''
    url.search = ''
    if (!isPostUrl(url, origin) || hints.size >= MAX_POSTS) return
    const prev = hints.get(url.href) ?? {}
    const card = cardOf(a)
    hints.set(url.href, {
      title: prev.title || textOf(card.querySelector('h1, h2, h3, h4, h5')) || textOf(a) || undefined,
      image: prev.image ?? imgSrc(card.querySelector('img'), origin),
      date: prev.date ?? card.querySelector('time[datetime]')?.getAttribute('datetime') ?? findDate(textOf(card))
    })
  })

  const posts = await Promise.all(
    [...hints].map(([url, hint]) =>
      loadPost(url, hint).catch(() => ({ url, title: hint.title ?? url, date: hint.date, image: hint.image }) as SitePost)
    )
  )
  return posts.sort((a, b) => (b.date ? Date.parse(b.date) : 0) - (a.date ? Date.parse(a.date) : 0))
}
