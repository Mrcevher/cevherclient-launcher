import { AppError } from '@shared/errors'
import type { SiteOrder, SiteProfile, StoreCategory, StoreListing, StoreProduct, StorePurchase } from '@shared/types'
import type { AuthService, RemoteConfigService } from '../types'

const MAX_PAGE_BYTES = 2 * 1024 * 1024

type Obj = Record<string, unknown>

const str = (v: unknown): string | undefined =>
  typeof v === 'string' ? v : typeof v === 'number' && Number.isFinite(v) ? String(v) : undefined
const num = (v: unknown): number | undefined => {
  const n = typeof v === 'number' ? v : typeof v === 'string' ? Number(v) : NaN
  return Number.isFinite(n) ? n : undefined
}
const obj = (v: unknown): Obj | null => (typeof v === 'object' && v !== null && !Array.isArray(v) ? (v as Obj) : null)
const httpsUrl = (v: unknown): string | undefined => {
  const s = str(v)
  return s && /^https:\/\//i.test(s) ? s : undefined
}

function parseProduct(v: unknown): StoreProduct | null {
  const o = obj(v)
  const id = str(o?.id)
  const name = str(o?.name)
  const price = str(o?.price)
  if (!o || !id || !name || price === undefined) return null
  return {
    id,
    name,
    details: str(o.details),
    price,
    discountedPrice: str(o.discountedPrice),
    discountExpiry: str(o.discountExpiry),
    stock: str(o.stock),
    duration: str(o.duration),
    priority: str(o.priority),
    featured: o.featured === true,
    image: httpsUrl(o.image)
  }
}

function parseCategory(v: unknown): StoreCategory | null {
  const o = obj(v)
  const id = str(o?.id)
  const name = str(o?.name)
  if (!o || !id || !name) return null
  const products = (Array.isArray(o.products) ? o.products : []).map(parseProduct).filter((p): p is StoreProduct => !!p)
  return { id, name, parentId: str(o.parentId), image: httpsUrl(o.image), products }
}

function parseCredits(o: Obj): Pick<SiteProfile, 'credits' | 'rawCredits' | 'currency'> {
  return { credits: str(o.credits), rawCredits: num(o.rawCredits), currency: str(o.currency) }
}

/**
 * Web sitesi (LeaderOS) verisi. Mağaza ve profil, oturum belirteciyle CevherAuth eklentisinin
 * site köprüsünden gelir; haberler sitenin herkese açık blog sayfalarından okunur (HTML, renderer
 * DOMParser ile ayrıştırır — main process'te HTML ayrıştırıcı yok).
 */
export class SiteService {
  constructor(
    private readonly auth: AuthService,
    private readonly config: RemoteConfigService
  ) {}

  async profile(): Promise<SiteProfile> {
    const res = await this.auth.siteRequest('/api/site/profile')
    const orders: SiteOrder[] = (Array.isArray(res.orders) ? res.orders : [])
      .map(obj)
      .filter((o): o is Obj => !!o)
      .map((o) => ({ id: str(o.id), total: str(o.total), status: str(o.status), date: str(o.date) }))
    return { linked: res.linked === true, registeredAt: str(res.registeredAt), orders, ...parseCredits(res) }
  }

  async listing(): Promise<StoreListing> {
    const res = await this.auth.siteRequest('/api/store/listing')
    const categories = (Array.isArray(res.categories) ? res.categories : [])
      .map(parseCategory)
      .filter((c): c is StoreCategory => !!c)
    return { site: httpsUrl(res.site) ?? (await this.siteOrigin()) ?? '', categories }
  }

  async buy(productId: string): Promise<StorePurchase> {
    if (!/^\d{1,12}$/.test(productId)) throw new AppError('STORE_FAILED', `Geçersiz ürün: ${productId}`, { reason: 'productNotFound' })
    const res = await this.auth.siteRequest('/api/store/buy', { productId })
    return { orderId: str(res.orderId), total: str(res.total), ...parseCredits(res) }
  }

  /** Sitenin kökü: links.website, yoksa Mağaza bağlantısının kökü. */
  async siteOrigin(): Promise<string | undefined> {
    const links = (await this.config.get()).config.links
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

  /** Sitenin bir sayfasının HTML'i (yalnızca sitenin kendi kökünden, https). */
  async fetchPage(url: string): Promise<string> {
    const origin = await this.siteOrigin()
    if (!origin) throw new AppError('STORE_FAILED', 'links.website tanımlı değil', { reason: 'disabled' })
    let target: URL
    try {
      target = new URL(url, origin)
    } catch {
      throw new AppError('STORE_FAILED', `Geçersiz adres: ${url}`, { reason: 'unavailable' })
    }
    if (target.origin !== origin || target.protocol !== 'https:') {
      throw new AppError('STORE_FAILED', `Site dışı adres: ${target.href}`, { reason: 'unavailable' })
    }
    let response: Response
    try {
      response = await fetch(target.href, {
        headers: { Accept: 'text/html', 'User-Agent': 'CevherClient' },
        redirect: 'follow',
        signal: AbortSignal.timeout(12_000)
      })
    } catch (err) {
      throw new AppError('NO_INTERNET', `${target.href}: ${(err as Error).message}`)
    }
    if (new URL(response.url).origin !== origin) {
      throw new AppError('STORE_FAILED', `Site dışına yönlendirildi: ${response.url}`, { reason: 'unavailable' })
    }
    if (!response.ok) throw new AppError('STORE_FAILED', `${target.href}: HTTP ${response.status}`, { reason: 'unavailable' })
    const buf = await response.arrayBuffer()
    if (buf.byteLength > MAX_PAGE_BYTES) throw new AppError('STORE_FAILED', `${target.href}: sayfa çok büyük`, { reason: 'unavailable' })
    return new TextDecoder('utf-8').decode(buf)
  }
}
