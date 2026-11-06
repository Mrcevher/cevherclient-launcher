import { useEffect, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import type { Language } from '@shared/types'
import { PageLayout } from '../../components/layout/PageLayout'
import { ExternalIcon } from '../../components/ui/icons'
import { Panel } from '../../components/ui/Panel'
import { PlayerHead } from '../../components/ui/PlayerHead'
import { formatDate } from '../../lib/format'
import { hasLink, openLink } from '../../lib/links'
import { formatMoney, toAmount } from '../../lib/site-format'
import { useNavStore } from '../../store/nav-store'
import { useNotifyStore } from '../../store/notify-store'
import { useSessionStore } from '../../store/session-store'
import { useSiteStore } from '../../store/site-store'

const MIN_PASSWORD = 6

/** Profil: skin kafalı oyuncu kartı, site hesabı (kredi, siparişler), hesap bilgileri, şifre değiştirme. */
export function ProfileScreen(): React.JSX.Element {
  const { t } = useTranslation()
  const account = useSessionStore((s) => s.auth?.account)
  const passwordRequired = useSessionStore((s) => s.auth?.passwordRequired ?? false)
  const loadProfile = useSiteStore((s) => s.loadProfile)

  useEffect(() => {
    if (passwordRequired) void loadProfile(true)
  }, [passwordRequired, loadProfile])

  return (
    <PageLayout title={t('nav.profile')}>
      <Panel>
        <div className="flex items-center gap-4">
          <div className="rounded-xl border border-border bg-[#0f1012] p-1.5">
            <PlayerHead name={account?.username ?? ''} size={64} />
          </div>
          <div className="min-w-0 flex-1">
            <div className="truncate text-[20px] font-bold">{account?.username}</div>
            <div className="text-[12.5px] text-muted">{t('profile.accountType')}</div>
          </div>
        </div>
      </Panel>

      {passwordRequired && <SitePanel />}

      <Panel title={t('profile.infoTitle')}>
        <dl className="divide-y divide-border/70 text-[14px]">
          <Row label={t('profile.username')}>{account?.username}</Row>
          <Row label="UUID">
            <span className="font-mono text-[12.5px] text-[#c3c6cb] select-text">{account?.uuid}</span>
          </Row>
        </dl>
      </Panel>

      {passwordRequired && <ChangePasswordPanel />}
    </PageLayout>
  )
}

/** Site (LeaderOS) hesabı: eşleşme, kredi, kayıt tarihi ve son siparişler. */
function SitePanel(): React.JSX.Element {
  const { t, i18n } = useTranslation()
  const lang = i18n.language as Language
  const profile = useSiteStore((s) => s.profile)
  const state = useSiteStore((s) => s.profileState)
  const go = useNavStore((s) => s.go)

  const date = (value: string | undefined): string => {
    if (!value) return '—'
    const ms = Date.parse(value.replace(' ', 'T'))
    return Number.isFinite(ms) ? formatDate(new Date(ms).toISOString(), lang) : value
  }

  const actions = (
    <div className="mt-4 flex flex-wrap gap-3">
      <button type="button" className="btn btn-green" onClick={() => go('store')}>
        {t('nav.store')}
      </button>
      {hasLink('credits') && (
        <button type="button" className="btn btn-ghost" onClick={() => openLink('credits')}>
          {t('store.topUp')} <ExternalIcon size={15} />
        </button>
      )}
    </div>
  )

  if (!profile) {
    return (
      <Panel title={t('profile.siteTitle')}>
        <p className="text-[13px] text-muted">{state === 'error' ? t('store.loadFailed') : t('common.loading')}</p>
      </Panel>
    )
  }
  if (!profile.linked) {
    return (
      <Panel title={t('profile.siteTitle')}>
        <p className="text-[13px] text-muted">{t('store.notLinked')}</p>
      </Panel>
    )
  }

  const orders = profile.orders ?? []
  return (
    <Panel title={t('profile.siteTitle')}>
      <dl className="divide-y divide-border/70 text-[14px]">
        <Row label={t('store.balance')}>
          <span className="text-[16px] font-extrabold">
            {profile.credits ?? (profile.rawCredits !== undefined ? formatMoney(profile.rawCredits, profile.currency, lang) : '—')}
          </span>
        </Row>
        <Row label={t('profile.registeredAt')}>{date(profile.registeredAt)}</Row>
      </dl>

      {orders.length > 0 && (
        <>
          <h4 className="mt-5 mb-2 text-[13.5px] font-bold text-[#c3c6cb]">{t('profile.recentOrders')}</h4>
          <ul className="divide-y divide-border/70 rounded-xl bg-panel-2 px-4 text-[13.5px]">
            {orders.map((o, i) => {
              const total = toAmount(o.total)
              return (
                <li key={o.id ?? i} className="flex items-center justify-between gap-4 py-2.5">
                  <span className="text-[#c3c6cb]">#{o.id ?? '?'}</span>
                  <span className="text-muted">{date(o.date)}</span>
                  <span className="font-semibold tabular-nums">{total !== undefined ? formatMoney(total, profile.currency, lang) : (o.total ?? '—')}</span>
                </li>
              )
            })}
          </ul>
        </>
      )}
      {actions}
    </Panel>
  )
}

function Row({ label, children }: { label: string; children: React.ReactNode }): React.JSX.Element {
  return (
    <div className="flex items-center justify-between gap-4 py-3 first:pt-0 last:pb-0">
      <dt className="text-[#c3c6cb]">{label}</dt>
      <dd className="min-w-0 truncate text-right font-semibold">{children}</dd>
    </div>
  )
}

function ChangePasswordPanel(): React.JSX.Element {
  const { t } = useTranslation()
  const changePassword = useSessionStore((s) => s.changePassword)
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [repeat, setRepeat] = useState('')
  const [touched, setTouched] = useState(false)
  const [busy, setBusy] = useState(false)

  const nextValid = next.length >= MIN_PASSWORD
  const repeatValid = next === repeat
  const valid = current.length > 0 && nextValid && repeatValid

  const submit = async (e: FormEvent): Promise<void> => {
    e.preventDefault()
    setTouched(true)
    if (!valid || busy) return
    setBusy(true)
    const ok = await changePassword(current, next)
    setBusy(false)
    if (ok) {
      setCurrent('')
      setNext('')
      setRepeat('')
      setTouched(false)
      useNotifyStore.getState().push({ kind: 'success', title: t('profile.passwordChangedTitle'), message: t('profile.passwordChanged') })
    }
  }

  const hint = touched && !nextValid ? t('login.passwordRule', { min: MIN_PASSWORD }) : touched && !repeatValid ? t('login.passwordMismatch') : null

  return (
    <Panel title={t('profile.passwordTitle')}>
      <form onSubmit={(e) => void submit(e)} noValidate className="flex max-w-[340px] flex-col gap-3">
        <input
          type="password"
          className="field"
          placeholder={t('profile.currentPassword')}
          autoComplete="current-password"
          value={current}
          onChange={(e) => setCurrent(e.target.value)}
        />
        <input
          type="password"
          className="field"
          placeholder={t('profile.newPassword')}
          autoComplete="new-password"
          value={next}
          onChange={(e) => setNext(e.target.value)}
        />
        <input
          type="password"
          className="field"
          placeholder={t('profile.repeatPassword')}
          autoComplete="new-password"
          value={repeat}
          onChange={(e) => setRepeat(e.target.value)}
        />
        {hint && <p className="text-[12.5px] text-danger">{hint}</p>}
        <button type="submit" className="btn btn-green w-fit" disabled={busy}>
          {t('profile.changePassword')}
        </button>
        <p className="text-[12px] text-muted">{t('profile.passwordNote')}</p>
      </form>
    </Panel>
  )
}
