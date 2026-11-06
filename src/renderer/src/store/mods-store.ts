import { create } from 'zustand'
import type { PerformancePackState } from '@shared/types'
import i18n from '../i18n'
import { call, subscribe } from '../lib/api'
import { reportError } from '../lib/errors'
import { useNotifyStore } from './notify-store'

interface ModsStore {
  pack: PerformancePackState | null
  busy: 'install' | 'remove' | null
  progress: number
  load(serverId: string): Promise<void>
  install(serverId: string): Promise<void>
  remove(serverId: string): Promise<void>
  /** İlerleme olayına abone olur (Oyna sırasında otomatik kurulum da bu olayı yayar). */
  init(): () => void
}

export const useModsStore = create<ModsStore>((set) => ({
  pack: null,
  busy: null,
  progress: 0,

  init() {
    return subscribe('mods:progress', ({ progress }) => set({ progress }))
  },

  async load(serverId) {
    try {
      set({ pack: await call('mods:getPack', serverId) })
    } catch (err) {
      reportError(err, { severity: 'notice' })
    }
  },

  async install(serverId) {
    set({ busy: 'install', progress: 0 })
    try {
      const pack = await call('mods:installPack', serverId)
      set({ pack })
      useNotifyStore.getState().push({ kind: 'success', title: i18n.t('mods.installedTitle'), message: i18n.t('mods.installedMessage') })
    } catch (err) {
      reportError(err)
    } finally {
      set({ busy: null })
    }
  },

  async remove(serverId) {
    set({ busy: 'remove' })
    try {
      set({ pack: await call('mods:removePack', serverId) })
    } catch (err) {
      reportError(err)
    } finally {
      set({ busy: null })
    }
  }
}))
