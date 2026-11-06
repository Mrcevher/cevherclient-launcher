import type { StoreProduct } from '@shared/types'

/** LeaderOS ondalık metnini sayıya çevirir ("10.00" → 10). */
export function toAmount(value: string | undefined): number | undefined {
  if (value === undefined) return undefined
  const n = Number(value.replace(',', '.'))
  return Number.isFinite(n) ? n : undefined
}

/** İndirim geçerli mi: indirimli fiyat 0'dan büyük, fiyattan küçük ve süresi dolmamış. */
export function effectivePrice(p: StoreProduct, now = Date.now()): { price: number; old?: number } {
  const price = toAmount(p.price) ?? 0
  const disc = toAmount(p.discountedPrice)
  if (disc === undefined || disc <= 0 || disc >= price) return { price }
  if (p.discountExpiry) {
    const t = Date.parse(p.discountExpiry.replace(' ', 'T'))
    // LeaderOS "süresiz" indirimi 1000-01-01 gibi geçmiş bir tarihle tutabilir; 2000 öncesi süresiz sayılır.
    if (Number.isFinite(t) && new Date(t).getFullYear() >= 2000 && t < now) return { price }
  }
  return { price: disc, old: price }
}

/** Para birimi kodu varsa yerel biçim (₺10,00), yoksa düz sayı. */
export function formatMoney(amount: number, currency: string | undefined, lang: string): string {
  if (currency && /^[A-Z]{3}$/.test(currency)) {
    try {
      return new Intl.NumberFormat(lang, { style: 'currency', currency }).format(amount)
    } catch {
      // Bilinmeyen kod: aşağıda düz biçim.
    }
  }
  const n = new Intl.NumberFormat(lang, { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(amount)
  return currency ? `${n} ${currency}` : n
}

/** Sitedeki HTML açıklamayı düz metne çevirir (DOMParser betik çalıştırmaz). */
export function htmlToText(html: string | undefined): string {
  if (!html) return ''
  const doc = new DOMParser().parseFromString(html, 'text/html')
  return (doc.body.textContent ?? '').replace(/\s+/g, ' ').trim()
}

/** Stok: boş / negatif sınırsız sayılır. */
export function stockOf(p: StoreProduct): number | null {
  const n = toAmount(p.stock)
  return n === undefined || n < 0 ? null : n
}
