import { useEffect } from 'react'
import i18n from '../i18n'
import { useConfigStore } from '../store/config-store'
import { useInstallStore } from '../store/install-store'
import { useLaunchStore } from '../store/launch-store'
import { useModsStore } from '../store/mods-store'
import { useSessionStore } from '../store/session-store'
import { useSettingsStore } from '../store/settings-store'
import { useUpdateStore } from '../store/update-store'

/** Açılışta store'ları doldurur ve main process olaylarına abone olur. */
export function useBootstrap(): void {
  useEffect(() => {
    void useSettingsStore.getState().load()
    void useSessionStore.getState().init()
    void useConfigStore.getState().load()
    void useConfigStore.getState().loadHistory()
    const offInstall = useInstallStore.getState().init()
    const offLaunch = useLaunchStore.getState().init()
    const offUpdate = useUpdateStore.getState().init()
    const offMods = useModsStore.getState().init()
    return () => {
      offInstall()
      offLaunch()
      offUpdate()
      offMods()
    }
  }, [])
}

/** Dil ayarını canlı olarak uygular (tasarım tek temalı; renkler globals.css'te). */
export function useApplyPreferences(): void {
  const language = useSettingsStore((s) => s.settings?.language)

  useEffect(() => {
    if (language && i18n.language !== language) void i18n.changeLanguage(language)
    if (language) document.documentElement.lang = language
  }, [language])
}
