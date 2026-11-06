import { create } from 'zustand'
import type { DeviceTier } from '@shared/types'
import i18n from '../i18n'
import { call } from '../lib/api'
import { reportError } from '../lib/errors'
import { useNotifyStore } from './notify-store'
import { useSettingsStore } from './settings-store'

interface QualityStore {
  /** Kurulumu süren seviye; boşta null. */
  busy: DeviceTier | null
  /** Son deneme başarısız oldu. */
  failed: boolean
  /** Seçim ekranı bu oturumda geçildi (kurulum yapılamadıysa); sonraki açılışta yine sorulur. */
  skipped: boolean
  /** remember=false: kurulur ama seçim kaydedilmez, ekran bu oturumda kapanır ve bir sonraki açılışta yine sorulur. */
  apply(serverId: string, tier: DeviceTier, remember: boolean): Promise<void>
  skip(): void
}

export const useQualityStore = create<QualityStore>((set, get) => ({
  busy: null,
  failed: false,
  skipped: false,

  async apply(serverId, tier, remember) {
    if (get().busy) return
    set({ busy: tier, failed: false })
    try {
      const result = await call('quality:apply', serverId, tier, remember)
      // Seçim main process'te kaydedildi; ayarları yeniden oku ki seçim ekranı kapansın.
      await useSettingsStore.getState().load()
      if (!remember) set({ skipped: true })
      useNotifyStore.getState().push({
        kind: 'success',
        title: i18n.t('quality.doneTitle'),
        message: i18n.t('quality.doneMessage', { shader: result.shaderTitle })
      })
    } catch (err) {
      set({ failed: true })
      reportError(err)
    } finally {
      set({ busy: null })
    }
  },

  skip() {
    set({ skipped: true })
  }
}))
