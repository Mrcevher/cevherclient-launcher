import { AnimatePresence, motion } from 'framer-motion'
import { useEffect } from 'react'
import { STATUS_REFRESH_MS } from '@shared/constants'
import logo from './assets/cevhercraft-logo.png'
import { LinkStartOverlay } from './components/effects/LinkStartOverlay'
import { CriticalErrorOverlay } from './components/feedback/CriticalErrorOverlay'
import { SystemNotifications } from './components/feedback/SystemNotifications'
import { TopBar } from './components/layout/TopBar'
import { useApplyPreferences, useBootstrap } from './hooks/use-bootstrap'
import { useSelectedServer } from './hooks/use-selected-server'
import { HomeScreen } from './screens/home/HomeScreen'
import { LoginScreen } from './screens/login/LoginScreen'
import { ModsScreen } from './screens/mods/ModsScreen'
import { NewsScreen } from './screens/news/NewsScreen'
import { ProfileScreen } from './screens/profile/ProfileScreen'
import { QualitySetupScreen } from './screens/quality/QualitySetupScreen'
import { SettingsScreen } from './screens/settings/SettingsScreen'
import { StoreScreen } from './screens/store/StoreScreen'
import { useConfigStore } from './store/config-store'
import { useNavStore, type Route } from './store/nav-store'
import { useQualityStore } from './store/quality-store'
import { useSessionStore } from './store/session-store'
import { useSettingsStore } from './store/settings-store'

const SCREENS: Record<Route, () => React.JSX.Element> = {
  home: HomeScreen,
  store: StoreScreen,
  mods: ModsScreen,
  news: NewsScreen,
  profile: ProfileScreen,
  settings: SettingsScreen
}

/** Giriş yapılmışken seçili sunucunun durumunu (oyuncu sayısı) periyodik yeniler. */
function useServerStatusPolling(enabled: boolean): void {
  const serverId = useSelectedServer()?.id
  useEffect(() => {
    if (!enabled || !serverId) return
    const refresh = (): void => void useConfigStore.getState().refreshStatus(serverId)
    refresh()
    const timer = setInterval(refresh, STATUS_REFRESH_MS)
    return () => clearInterval(timer)
  }, [enabled, serverId])
}

export function App(): React.JSX.Element {
  useBootstrap()
  useApplyPreferences()

  const status = useSessionStore((s) => s.status)
  const settingsReady = useSettingsStore((s) => s.settings !== null)
  const route = useNavStore((s) => s.route)
  const server = useSelectedServer()
  const deviceTier = useSettingsStore((s) => s.settings?.deviceTier ?? null)
  const qualitySkipped = useQualityStore((s) => s.skipped)
  // Girişten sonra cihaz kalitesi seçilmediyse (ve sunucu profili varsa) önce seçim ekranı gelir.
  const needsQuality = status === 'signedIn' && !!server && deviceTier === null && !qualitySkipped
  const ready = settingsReady && status !== 'loading'
  useServerStatusPolling(status === 'signedIn')

  // Çıkış yapınca bir sonraki girişte ana sayfadan başla.
  useEffect(() => {
    if (status === 'signedOut') useNavStore.getState().go('home')
  }, [status])

  const Screen = SCREENS[route]

  return (
    <div className="relative h-full overflow-hidden bg-bg">
      {/* Üst çubuk DOM'da ekranlardan ÖNCE olmalı: Electron sürükleme alanlarını belge sırasıyla
          işler; sonra gelen no-drag öğeler (hamburger, geri butonu) ancak böyle tıklanabilir kalır. */}
      <TopBar />
      <AnimatePresence mode="wait">
        {!ready ? (
          <motion.div key="splash" className="absolute inset-0 grid place-items-center" exit={{ opacity: 0 }}>
            <img src={logo} alt="" className="h-28 w-auto animate-pulse" draggable={false} />
          </motion.div>
        ) : needsQuality && server ? (
          <motion.div key="quality" className="absolute inset-0" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
            <QualitySetupScreen server={server} />
          </motion.div>
        ) : status === 'signedIn' ? (
          <motion.div
            key={`app-${route}`}
            className="absolute inset-0"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.18 }}
          >
            <Screen />
          </motion.div>
        ) : (
          <motion.div key="login" className="absolute inset-0" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
            <LoginScreen />
          </motion.div>
        )}
      </AnimatePresence>

      <SystemNotifications />
      <LinkStartOverlay />
      <CriticalErrorOverlay />
    </div>
  )
}
