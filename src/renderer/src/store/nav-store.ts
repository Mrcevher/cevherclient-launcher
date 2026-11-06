import { create } from 'zustand'

/** Ekranlar. "home" arka plan görselli ana sayfa; diğerleri geri butonlu sayfalar. */
export type Route = 'home' | 'store' | 'mods' | 'news' | 'profile' | 'settings'

interface NavStore {
  route: Route
  /** Ana sayfadaki sol menü açık mı (hamburger ile değişir). */
  sidebarOpen: boolean
  go(route: Route): void
  toggleSidebar(): void
}

/** Launcher her açılışında menü kapalı gelir: yalnızca hamburgere basılınca açılır. */
export const useNavStore = create<NavStore>((set, get) => ({
  route: 'home',
  sidebarOpen: false,
  go: (route) => set({ route }),
  toggleSidebar: () => set({ sidebarOpen: !get().sidebarOpen })
}))
