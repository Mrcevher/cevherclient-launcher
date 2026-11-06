import { create } from 'zustand'
import type { ModKind, ModSearchHit, UserModsState } from '@shared/types'
import i18n from '../i18n'
import { call } from '../lib/api'
import { reportError } from '../lib/errors'
import { useNotifyStore } from './notify-store'

interface ModBrowserStore {
  /** Hangi sekme: mod mu shader mı aranıyor. */
  kind: ModKind
  query: string
  hits: ModSearchHit[]
  total: number
  searching: boolean
  /** Kurulan oyuncu modları/shader'ları (+ Performans Paketi slug'ları). */
  user: UserModsState | null
  /** Şu an kurulan / kaldırılan proje. */
  busyId: string | null
  setKind(kind: ModKind): void
  setQuery(query: string): void
  search(serverId: string, more?: boolean): Promise<void>
  loadUser(serverId: string): Promise<void>
  install(serverId: string, hit: Pick<ModSearchHit, 'projectId' | 'title'>): Promise<void>
  remove(serverId: string, projectId: string): Promise<void>
}

/** Arama sırası: kullanıcı yazarken eski isteğin sonucu yenisinin üstüne yazılmasın. */
let searchSeq = 0

export const useModBrowserStore = create<ModBrowserStore>((set, get) => ({
  kind: 'mod',
  query: '',
  hits: [],
  total: 0,
  searching: false,
  user: null,
  busyId: null,

  // Sekme değişince eski sekmenin sonuçları görünmesin; arama kutusu da sıfırlanır.
  setKind: (kind) => set(get().kind === kind ? {} : { kind, query: '', hits: [], total: 0 }),
  setQuery: (query) => set({ query }),

  async search(serverId, more = false) {
    const seq = ++searchSeq
    const { kind, query } = get()
    const offset = more ? get().hits.length : 0
    set({ searching: true })
    try {
      const res = await call('mods:search', serverId, query, offset, kind)
      if (seq !== searchSeq) return
      set({ hits: more ? [...get().hits, ...res.hits] : res.hits, total: res.total })
    } catch (err) {
      if (seq === searchSeq) reportError(err, { severity: 'notice' })
    } finally {
      if (seq === searchSeq) set({ searching: false })
    }
  },

  async loadUser(serverId) {
    try {
      set({ user: await call('mods:listUser', serverId) })
    } catch (err) {
      reportError(err, { severity: 'notice' })
    }
  },

  async install(serverId, hit) {
    const { kind, user } = get()
    set({ busyId: hit.projectId })
    try {
      const next = await call('mods:installUser', serverId, hit.projectId, kind)
      set({ user: next })
      const irisAdded = kind === 'shader' && !user?.shaderMod && next.shaderMod !== null
      useNotifyStore.getState().push({
        kind: 'success',
        title: i18n.t(kind === 'shader' ? 'modBrowser.shaderInstalledTitle' : 'modBrowser.installedTitle'),
        message: irisAdded
          ? i18n.t('modBrowser.shaderInstalledWithIris', { name: hit.title, loader: next.shaderMod })
          : i18n.t('modBrowser.installedMessage', { name: hit.title })
      })
    } catch (err) {
      reportError(err)
    } finally {
      set({ busyId: null })
    }
  },

  async remove(serverId, projectId) {
    set({ busyId: projectId })
    try {
      set({ user: await call('mods:removeUser', serverId, projectId) })
    } catch (err) {
      reportError(err)
    } finally {
      set({ busyId: null })
    }
  }
}))
