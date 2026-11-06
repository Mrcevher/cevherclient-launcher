import { AnimatePresence, motion } from 'framer-motion'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import type { LauncherLinks } from '@shared/types'
import { openLink } from '../../lib/links'
import { useConfigStore } from '../../store/config-store'
import { useNavStore, type Route } from '../../store/nav-store'
import { useSettingsStore } from '../../store/settings-store'
import { CartIcon, ChatIcon, CubeIcon, GearIcon, MenuIcon, NewsIcon, StarIcon, UserIcon } from '../ui/icons'

type Item = { key: string; icon: ReactNode; route?: Route; link?: keyof LauncherLinks; upper?: boolean }

/** Tasarımdaki sıra: Mağaza ve dış bağlantılar büyük harfle, diğer uygulama sayfaları normal yazımla. */
const ITEMS: Item[] = [
  { key: 'store', icon: <CartIcon size={18} />, route: 'store', upper: true },
  { key: 'mods', icon: <CubeIcon size={18} />, route: 'mods' },
  { key: 'news', icon: <NewsIcon size={18} />, route: 'news' },
  { key: 'credits', icon: <StarIcon size={17} />, link: 'credits', upper: true },
  { key: 'discord', icon: <ChatIcon size={17} />, link: 'discord', upper: true },
  { key: 'profile', icon: <UserIcon size={18} />, route: 'profile' },
  { key: 'settings', icon: <GearIcon size={18} />, route: 'settings' }
]

/** Ana sayfanın sol menüsü: hamburger ile açılıp kapanır, arka plan görselinin üstünde durur. */
export function Sidebar(): React.JSX.Element {
  const { t } = useTranslation()
  const open = useNavStore((s) => s.sidebarOpen)
  const toggle = useNavStore((s) => s.toggleSidebar)
  const go = useNavStore((s) => s.go)
  const links = useConfigStore((s) => s.snapshot?.config.links)
  const version = useSettingsStore((s) => s.system?.launcherVersion)

  return (
    <>
      <AnimatePresence initial={false}>
        {open && (
          <motion.aside
            key="sidebar"
            className="absolute inset-y-0 left-0 z-20 w-[250px] pt-[70px]"
            style={{ background: 'linear-gradient(90deg, rgb(8 9 10 / 0.82) 0%, rgb(8 9 10 / 0.62) 70%, rgb(8 9 10 / 0))' }}
            initial={{ x: -260, opacity: 0 }}
            animate={{ x: 0, opacity: 1 }}
            exit={{ x: -260, opacity: 0 }}
            transition={{ type: 'spring', stiffness: 380, damping: 36 }}
          >
            <div className="px-4">
              <div lang="en" className="text-[15px] font-bold text-white">
                CevherClient
              </div>
              <div className="text-[11px] font-medium text-[#9aa0a8]">v{version ?? '…'}</div>
            </div>

            <nav className="mt-5 flex flex-col gap-1.5 pr-4" aria-label="Menu">
              {ITEMS.filter((it) => !it.link || links?.[it.link]).map((it) => (
                <button
                  key={it.key}
                  type="button"
                  onClick={() => (it.route ? go(it.route) : it.link && openLink(it.link))}
                  className="group flex cursor-pointer items-center gap-3 rounded-r-xl py-3 pl-4 text-left text-white transition-colors hover:bg-white/8"
                >
                  <span className="w-5 text-[#e6e8eb] transition-transform group-hover:scale-110">{it.icon}</span>
                  <span lang={it.key === 'discord' ? 'en' : undefined} className={`text-[14px] font-bold ${it.upper ? 'tracking-wide uppercase' : ''}`}>
                    {t(`nav.${it.key}`)}
                  </span>
                </button>
              ))}
            </nav>
          </motion.aside>
        )}
      </AnimatePresence>

      {/* Hamburger her zaman sol üstte */}
      <button
        type="button"
        aria-label={t('nav.menu')}
        aria-expanded={open}
        onClick={toggle}
        className="no-drag absolute top-2.5 left-2.5 z-50 grid size-11 cursor-pointer place-items-center rounded-xl border border-white/20 bg-white/10 text-white backdrop-blur-md transition-[background-color,border-color,transform] hover:border-white/35 hover:bg-white/20 active:scale-95"
      >
        <MenuIcon size={22} />
      </button>
    </>
  )
}
