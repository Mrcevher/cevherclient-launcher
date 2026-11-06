import { create } from 'zustand'
import { AppError } from '@shared/errors'
import type { RemoteConfigSnapshot, ServerStatus } from '@shared/types'
import { call } from '../lib/api'
import { reportError } from '../lib/errors'

interface ConfigStore {
  snapshot: RemoteConfigSnapshot | null
  status: 'idle' | 'loading' | 'ready' | 'error'
  /** Sunucu id → son bilinen durum */
  serverStatus: Record<string, ServerStatus>
  statusLoading: Record<string, boolean>
  /** Hedef anahtarı → son oynanma */
  history: Record<string, string>
  load(refresh?: boolean): Promise<void>
  refreshStatus(serverId: string): Promise<void>
  loadHistory(): Promise<void>
}

export const useConfigStore = create<ConfigStore>((set, get) => ({
  snapshot: null,
  status: 'idle',
  serverStatus: {},
  statusLoading: {},
  history: {},

  async load(refresh = false) {
    set({ status: 'loading' })
    try {
      const snapshot = await call('config:get', refresh)
      set({ snapshot, status: 'ready' })

      if (!snapshot.launcherUpToDate) {
        const system = await call('system:getInfo')
        reportError(
          new AppError('LAUNCHER_OUTDATED', snapshot.source, {
            required: snapshot.config.minLauncherVersion,
            current: system.launcherVersion
          })
        )
      }
    } catch (err) {
      set({ status: 'error' })
      reportError(err, { retry: () => void get().load(true) })
    }
  },

  async refreshStatus(serverId) {
    if (get().statusLoading[serverId]) return
    set((s) => ({ statusLoading: { ...s.statusLoading, [serverId]: true } }))
    try {
      const status = await call('status:get', serverId)
      set((s) => ({ serverStatus: { ...s.serverStatus, [serverId]: status } }))
    } catch (err) {
      // Arka plan kontrolü: kullanıcıyı tam ekran uyarıyla bölme.
      reportError(err, { severity: 'notice' })
    } finally {
      set((s) => ({ statusLoading: { ...s.statusLoading, [serverId]: false } }))
    }
  },

  async loadHistory() {
    try {
      set({ history: await call('launch:history') })
    } catch (err) {
      reportError(err, { severity: 'notice' })
    }
  }
}))
