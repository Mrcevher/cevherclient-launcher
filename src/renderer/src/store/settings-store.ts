import { create } from 'zustand'
import type { JavaInstallation, Settings, SettingsPatch, SystemInfo } from '@shared/types'
import { call } from '../lib/api'
import { reportError } from '../lib/errors'

interface SettingsStore {
  settings: Settings | null
  system: SystemInfo | null
  javaInstalls: JavaInstallation[]
  load(): Promise<void>
  /**
   * Ayarı anında arayüze uygular, diske yazmayı kısa bir gecikmeyle toplu yapar
   * (slider/renk seçici sürüklenirken her karede IPC çağrısı olmasın).
   */
  update(patch: SettingsPatch): void
}

const PERSIST_DELAY_MS = 300
let pending: SettingsPatch = {}
let timer: ReturnType<typeof setTimeout> | null = null

function mergePatch(base: SettingsPatch, patch: SettingsPatch): SettingsPatch {
  return {
    ...base,
    ...patch,
    ...(patch.resolution ? { resolution: { ...(base.resolution ?? {}), ...patch.resolution } as Settings['resolution'] } : {})
  }
}

export const useSettingsStore = create<SettingsStore>((set, get) => ({
  settings: null,
  system: null,
  javaInstalls: [],

  async load() {
    try {
      const [settings, system, javaInstalls] = await Promise.all([
        call('settings:get'),
        call('system:getInfo'),
        call('java:detect')
      ])
      set({ settings, system, javaInstalls })
    } catch (err) {
      reportError(err)
    }
  },

  update(patch) {
    const current = get().settings
    if (!current) return
    set({ settings: { ...current, ...mergePatch(current, patch) } as Settings })

    pending = mergePatch(pending, patch)
    if (timer) clearTimeout(timer)
    timer = setTimeout(async () => {
      const toSend = pending
      pending = {}
      timer = null
      try {
        // Main process değerleri doğrular/sınırlar; dönen hali esas al.
        const saved = await call('settings:update', toSend)
        if (Object.keys(pending).length === 0) set({ settings: saved })
      } catch (err) {
        reportError(err)
      }
    }, PERSIST_DELAY_MS)
  }
}))
