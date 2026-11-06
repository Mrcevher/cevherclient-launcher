import { AnimatePresence, motion } from 'framer-motion'
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { useSelectedServer } from '../../hooks/use-selected-server'
import { call } from '../../lib/api'
import { openLink } from '../../lib/links'
import { useConfigStore } from '../../store/config-store'
import { useNavStore } from '../../store/nav-store'
import { useSessionStore } from '../../store/session-store'
import { BagIcon, CartIcon, ChevronDownIcon, CloseIcon, GearIcon, MinimizeIcon, UserIcon } from '../ui/icons'
import { PlayerHead } from '../ui/PlayerHead'

/**
 * Çerçevesiz pencerenin üst çubuğu. Tamamı sürükleme alanıdır; sağda kullanıcı menüsü,
 * çevrimiçi oyuncu sayısı ve küçült/kapat butonları bulunur.
 */
export function TopBar(): React.JSX.Element {
  const { t } = useTranslation()
  const signedIn = useSessionStore((s) => s.status === 'signedIn')

  return (
    <header className="drag absolute inset-x-0 top-0 z-40 flex h-14 items-center justify-end gap-2 pr-2 pl-4">
      {signedIn && (
        <div className="no-drag flex items-center gap-2">
          <UserChip />
          <OnlinePill />
        </div>
      )}
      <div className="no-drag ml-1 flex items-center">
        <WindowButton label={t('titlebar.minimize')} onClick={() => void call('window:minimize')}>
          <MinimizeIcon size={18} />
        </WindowButton>
        <WindowButton label={t('titlebar.close')} onClick={() => void call('window:close')} danger>
          <CloseIcon size={18} />
        </WindowButton>
      </div>
    </header>
  )
}

function WindowButton({
  label,
  onClick,
  danger,
  children
}: {
  label: string
  onClick(): void
  danger?: boolean
  children: ReactNode
}): React.JSX.Element {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      onClick={onClick}
      className={`grid size-10 cursor-pointer place-items-center rounded-lg text-[#c9ccd1] transition-colors ${
        danger ? 'hover:bg-danger hover:text-white' : 'hover:bg-white/10 hover:text-white'
      }`}
    >
      {children}
    </button>
  )
}

/** Seçili sunucudaki çevrimiçi oyuncu sayısı (sunucu durumu periyodik yenilenir). */
function OnlinePill(): React.JSX.Element {
  const { t } = useTranslation()
  const server = useSelectedServer()
  const status = useConfigStore((s) => (server ? s.serverStatus[server.id] : undefined))
  const online = status?.online ?? false
  return (
    <span className="pill text-[13px] font-semibold text-[#d5d8dc]" title={server?.address}>
      <span
        className={`size-2 rounded-full ${online ? 'bg-hp-high shadow-[0_0_8px_var(--color-hp-high)]' : 'bg-[#5b5e66]'}`}
      />
      {online ? t('topbar.players', { count: status?.players.online ?? 0 }) : t('topbar.offline')}
    </span>
  )
}

/** Avatar + ad; tıklanınca hesap menüsü açılır. */
function UserChip(): React.JSX.Element | null {
  const { t } = useTranslation()
  const account = useSessionStore((s) => s.auth?.account)
  const logout = useSessionStore((s) => s.logout)
  const go = useNavStore((s) => s.go)
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  // Dışarı tıklayınca veya Esc ile kapan.
  useEffect(() => {
    if (!open) return
    const onDown = (e: MouseEvent): void => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent): void => {
      if (e.key === 'Escape') setOpen(false)
    }
    window.addEventListener('mousedown', onDown)
    window.addEventListener('keydown', onKey)
    return () => {
      window.removeEventListener('mousedown', onDown)
      window.removeEventListener('keydown', onKey)
    }
  }, [open])

  if (!account) return null

  const item = (icon: ReactNode, label: string, action: () => void): React.JSX.Element => (
    <button
      type="button"
      onClick={() => {
        setOpen(false)
        action()
      }}
      className="flex w-full cursor-pointer items-center gap-3 rounded-lg px-3 py-2.5 text-left text-[14px] font-medium text-[#d5d8dc] transition-colors hover:bg-white/6 hover:text-white"
    >
      <span className="text-[#c9ccd1]">{icon}</span>
      {label}
    </button>
  )

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
        className="pill cursor-pointer pl-1.5 transition-colors hover:border-white/15"
      >
        <PlayerHead name={account.username} size={24} />
        <span className="max-w-32 truncate text-[14px] font-bold">{account.username}</span>
        <ChevronDownIcon size={14} className="text-[#b9bcc2]" />
      </button>

      <AnimatePresence>
        {open && (
          <motion.div
            role="menu"
            className="absolute top-full right-0 z-50 mt-2 w-64 rounded-2xl border border-border bg-[#141518] p-3 shadow-[0_20px_50px_rgb(0_0_0/0.6)]"
            initial={{ opacity: 0, y: -6, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -6, scale: 0.98 }}
            transition={{ duration: 0.14 }}
          >
            <div className="mb-2 flex items-center gap-3 rounded-xl bg-panel-2 p-3">
              <div className="rounded-lg border border-border bg-[#0f1012] p-1">
                <PlayerHead name={account.username} size={40} />
              </div>
              <span className="truncate text-[16px] font-bold">{account.username}</span>
            </div>
            {item(<UserIcon size={18} />, t('nav.profile'), () => go('profile'))}
            {item(<CartIcon size={18} />, t('nav.store'), () => go('store'))}
            {item(<BagIcon size={18} />, t('userMenu.orders'), () => openLink('orders'))}
            {item(<GearIcon size={18} />, t('nav.settings'), () => go('settings'))}
            <button type="button" onClick={() => void logout()} className="btn btn-red mt-2 w-full">
              {t('userMenu.logout')}
            </button>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
