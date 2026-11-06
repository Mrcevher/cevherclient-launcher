import { create } from 'zustand'
import { AppError } from '@shared/errors'
import { LINK_START_DURATION_MS } from '@shared/constants'
import type { LaunchStage, LaunchTarget } from '@shared/types'
import i18n from '../i18n'
import { call, subscribe } from '../lib/api'
import { reportError } from '../lib/errors'
import { useConfigStore } from './config-store'
import { useNotifyStore } from './notify-store'
import { useSessionStore } from './session-store'

/**
 * idle      → hazır
 * linkStart → LINK START geçiş animasyonu oynuyor (~1.5 sn)
 * launching → main process başlatma aşamalarını yürütüyor
 * running   → oyun açık
 */
export type LaunchPhase = 'idle' | 'linkStart' | 'launching' | 'running'

interface LaunchStore {
  phase: LaunchPhase
  stage: LaunchStage | null
  progress: number
  target: LaunchTarget | null
  init(): () => void
  /** Oyna butonu: önce LINK START, ardından başlatma. */
  start(target: LaunchTarget, label: string): Promise<void>
}

const wait = (ms: number): Promise<void> => new Promise((r) => setTimeout(r, ms))

export const useLaunchStore = create<LaunchStore>((set, get) => ({
  phase: 'idle',
  stage: null,
  progress: 0,
  target: null,

  init() {
    const offProgress = subscribe('launch:progress', ({ stage, progress }) => {
      if (get().phase === 'launching') set({ stage, progress })
    })
    const offExit = subscribe('launch:exit', () => {
      set({ phase: 'idle', stage: null, progress: 0, target: null })
      useNotifyStore.getState().push({
        kind: 'info',
        title: i18n.t('launch.exitedTitle'),
        message: i18n.t('launch.exitedMessage')
      })
    })
    return () => {
      offProgress()
      offExit()
    }
  },

  async start(target, label) {
    if (get().phase !== 'idle') return

    set({ phase: 'linkStart', target, stage: null, progress: 0 })
    await wait(LINK_START_DURATION_MS)
    set({ phase: 'launching', stage: 'preparing' })

    try {
      await call('launch:start', target)
      set({ phase: 'running', progress: 1 })
      useNotifyStore.getState().push({
        kind: 'success',
        title: i18n.t('launch.startedTitle'),
        message: i18n.t('launch.startedMessage', { target: label })
      })
      void useConfigStore.getState().loadHistory()
    } catch (err) {
      set({ phase: 'idle', stage: null, progress: 0, target: null })
      // Oturum süresi dolduysa main oturumu kapatmış olabilir: giriş ekranına dönülür.
      if (err instanceof AppError && err.code === 'AUTH_FAILED') void useSessionStore.getState().init()
      reportError(err, { retry: () => void get().start(target, label) })
    }
  }
}))
