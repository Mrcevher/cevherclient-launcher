import { create } from 'zustand'
import { AppError } from '@shared/errors'
import type { SiteProfile, StoreListing, StorePurchase } from '@shared/types'
import { call } from '../lib/api'
import { reportError } from '../lib/errors'
import { useSessionStore } from './session-store'

type Load = 'idle' | 'loading' | 'ready' | 'error'

interface SiteStore {
  profile: SiteProfile | null
  profileState: Load
  listing: StoreListing | null
  listingState: Load
  /** Satın alınmakta olan ürün id'si. */
  buying: string | null
  loadProfile(force?: boolean): Promise<void>
  loadListing(force?: boolean): Promise<void>
  /** Başarıda satın alma sonucunu, hatada null döner (hata zaten gösterilmiş olur). */
  buy(productId: string): Promise<StorePurchase | null>
  reset(): void
}

/** Oturum sunucuda düşmüşse launcher'daki oturum durumunu yeniler (giriş ekranına döner). */
function handle(err: unknown): void {
  reportError(err)
  const e = AppError.from(err)
  if (e.code === 'AUTH_FAILED' && e.meta?.reason === 'sessionExpired') void useSessionStore.getState().init()
}

/** Web sitesi (LeaderOS) verisi: site hesabı/kredi ve mağaza listesi. */
export const useSiteStore = create<SiteStore>((set, get) => ({
  profile: null,
  profileState: 'idle',
  listing: null,
  listingState: 'idle',
  buying: null,

  async loadProfile(force = false) {
    if (!force && get().profileState === 'loading') return
    if (!force && get().profileState === 'ready') return
    set({ profileState: 'loading' })
    try {
      set({ profile: await call('site:profile'), profileState: 'ready' })
    } catch (err) {
      set({ profileState: 'error' })
      handle(err)
    }
  },

  async loadListing(force = false) {
    if (get().listingState === 'loading') return
    if (!force && get().listingState === 'ready') return
    set({ listingState: 'loading' })
    try {
      set({ listing: await call('store:listing'), listingState: 'ready' })
    } catch (err) {
      set({ listingState: 'error' })
      handle(err)
    }
  },

  async buy(productId) {
    if (get().buying) return null
    set({ buying: productId })
    try {
      const result = await call('store:buy', productId)
      // Bakiye ve son siparişler değişti; stok da değişmiş olabilir.
      const profile = get().profile
      if (profile && result.credits !== undefined) {
        set({ profile: { ...profile, credits: result.credits, rawCredits: result.rawCredits ?? profile.rawCredits } })
      }
      void get().loadProfile(true)
      void get().loadListing(true)
      return result
    } catch (err) {
      handle(err)
      return null
    } finally {
      set({ buying: null })
    }
  },

  reset() {
    set({ profile: null, profileState: 'idle', listing: null, listingState: 'idle', buying: null })
  }
}))

// Hesap değişince (çıkış / başka hesapla giriş) önceki hesabın site verisi kalmasın.
let lastUser: string | undefined
useSessionStore.subscribe((s) => {
  const user = s.auth?.account?.username
  if (user !== lastUser) {
    lastUser = user
    useSiteStore.getState().reset()
  }
})
