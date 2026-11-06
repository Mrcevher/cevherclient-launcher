import type { ServerProfile } from '@shared/types'
import { useConfigStore } from '../store/config-store'
import { useSettingsStore } from '../store/settings-store'

/** Ayarlardaki seçili sunucu; yoksa/silinmişse config'deki ilk sunucu. */
export function useSelectedServer(): ServerProfile | null {
  const servers = useConfigStore((s) => s.snapshot?.config.servers)
  const selectedId = useSettingsStore((s) => s.settings?.selectedServerId)
  if (!servers || servers.length === 0) return null
  return servers.find((s) => s.id === selectedId) ?? servers[0]!
}
