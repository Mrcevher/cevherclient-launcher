import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { DeviceTier } from '@shared/types'
import { useSelectedServer } from '../../hooks/use-selected-server'
import { useModsStore } from '../../store/mods-store'
import { useQualityStore } from '../../store/quality-store'
import { useSettingsStore } from '../../store/settings-store'
import { TierCards } from '../quality/TierCards'
import { SettingsSection } from './SettingsSection'

/** Cihaz kalitesini sonradan değiştirme: yalnızca kurulu shader değişir, diğer ayarlar aynı kalır. */
export function QualitySection(): React.JSX.Element {
  const { t } = useTranslation()
  const server = useSelectedServer()
  const current = useSettingsStore((s) => s.settings?.deviceTier ?? null)
  const busy = useQualityStore((s) => s.busy)
  const apply = useQualityStore((s) => s.apply)
  const progress = useModsStore((s) => s.progress)
  const [tier, setTier] = useState<DeviceTier>(current ?? 'medium')

  useEffect(() => {
    if (current) setTier(current)
  }, [current])

  const working = busy !== null
  return (
    <SettingsSection title={t('quality.settingsTitle')}>
      <p className="mb-4 text-[13px] text-muted">{t('quality.settingsHint')}</p>
      <TierCards value={tier} disabled={working || !server} onChange={setTier} />
      <button
        type="button"
        disabled={working || !server || tier === current}
        onClick={() => server && void apply(server.id, tier, true)}
        className="mt-4 h-11 w-full cursor-pointer rounded-xl bg-[#e9eaec] text-[14px] font-semibold text-[#141518] transition-[background-color,transform] hover:bg-white active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-45 disabled:active:scale-100 sm:w-48"
      >
        {working ? t('quality.installing', { percent: Math.round(progress * 100) }) : t('quality.apply')}
      </button>
    </SettingsSection>
  )
}
