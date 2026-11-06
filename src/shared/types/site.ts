/**
 * Web sitesi (LeaderOS) verisi: launcher içi Mağaza, Profil ve Haberler.
 * Mağaza ve profil CevherAuth eklentisi üzerinden gelir (site API anahtarı sunucuda kalır).
 */

export interface StoreProduct {
  id: string
  name: string
  /** Sitedeki ürün açıklaması (HTML olabilir; renderer düz metne çevirir). */
  details?: string
  /** "10.00" gibi ondalık metin. */
  price: string
  discountedPrice?: string
  discountExpiry?: string
  stock?: string
  duration?: string
  priority?: string
  featured: boolean
  image?: string
}

export interface StoreCategory {
  id: string
  /** Alt kategorilerde "Üst › Alt". */
  name: string
  parentId?: string
  image?: string
  products: StoreProduct[]
}

export interface StoreListing {
  /** Sitenin kök adresi (ör. https://cevhercraft.com). */
  site: string
  categories: StoreCategory[]
}

export interface SiteOrder {
  id?: string
  total?: string
  status?: string
  date?: string
}

export interface SiteProfile {
  /** Launcher hesabı aynı adlı site hesabıyla eşleşmiş mi (satın alma için gerekli). */
  linked: boolean
  /** "10.25 TL" gibi biçimli bakiye. */
  credits?: string
  rawCredits?: number
  currency?: string
  registeredAt?: string
  orders?: SiteOrder[]
}

export interface StorePurchase {
  orderId?: string
  total?: string
  credits?: string
  rawCredits?: number
  currency?: string
}
