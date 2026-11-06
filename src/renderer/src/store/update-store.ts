import { create } from 'zustand'
import type { UpdateStatus } from '@shared/types'
import i18n from '../i18n'
import { call, subscribe } from '../lib/api'
import { reportError } from '../lib/errors'
import { useNotifyStore } from './notify-store'

interface UpdateStore {
  status: UpdateStatus
  /** Olay aboneliğini başlatır; aboneliği iptal eden fonksiyonu döndürür. */
  init(): () => void
  install(): Promise<void>
}

/** Launcher güncellemesinin durumu (yalnızca paketlenmiş sürümde değişir). */
export const useUpdateStore = create<UpdateStore>((set, get) => ({
  status: { state: 'none' },

  init() {
    const off = subscribe('update:status', (status) => {
      const previous = get().status.state
      set({ status })
      if (status.state === 'downloaded' && previous !== 'downloaded') {
        useNotifyStore.getState().push(
          {
            kind: 'info',
            title: i18n.t('update.readyTitle'),
            message: i18n.t('update.readyMessage', { version: status.version ?? '' })
          },
          8000
        )
      }
    })
    call('update:getStatus')
      .then((status) => set({ status }))
      .catch(() => undefined)
    return off
  },

  async install() {
    try {
      await call('update:install')
    } catch (err) {
      reportError(err)
    }
  }
}))
