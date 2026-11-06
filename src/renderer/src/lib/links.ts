import type { LauncherLinks } from '@shared/types'
import { useConfigStore } from '../store/config-store'
import { call } from './api'
import { reportError } from './errors'

/** Config'teki dış bağlantıyı (Mağaza, Kredi Al, Discord …) sistem tarayıcısında açar. */
export function openLink(key: keyof LauncherLinks): void {
  const url = useConfigStore.getState().snapshot?.config.links?.[key]
  if (!url) return
  call('shell:openExternal', url).catch((err) => reportError(err))
}

/** Bağlantı config'de tanımlı mı (tanımsızsa menüde gösterilmez). */
export function hasLink(key: keyof LauncherLinks): boolean {
  return !!useConfigStore.getState().snapshot?.config.links?.[key]
}
