import { motion } from 'framer-motion'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { DeviceTier, ServerProfile } from '@shared/types'
import { BackdropVideo } from '../../components/ui/BackdropVideo'
import { useModsStore } from '../../store/mods-store'
import { useQualityStore } from '../../store/quality-store'
import { CheckRow, TierCards } from './TierCards'

/**
 * Girişten sonra, cihaz kalitesi henüz seçilmediyse açılan pencere. Üç seçenek yalnızca kurulacak
 * shader'ı değiştirir; performans ve oyun ayarları üçünde de en üst düzeyde optimize edilir.
 * "Seçimimi hatırla" kapalıysa seçim kurulur ama kaydedilmez: launcher bir sonraki açılışta yine sorar.
 */
export function QualitySetupScreen({ server }: { server: ServerProfile }): React.JSX.Element {
  const { t } = useTranslation()
  const busy = useQualityStore((s) => s.busy)
  const apply = useQualityStore((s) => s.apply)
  const skip = useQualityStore((s) => s.skip)
  const progress = useModsStore((s) => s.progress)

  const [tier, setTier] = useState<DeviceTier>('medium')
  const [remember, setRemember] = useState(true)
  const working = busy !== null

  return (
    <div className="absolute inset-0 grid place-items-center overflow-x-hidden overflow-y-auto p-6">
      <BackdropVideo className="scale-105 blur-[6px]" />
      <div className="absolute inset-0 bg-black/60" />
      <motion.div
        role="dialog"
        aria-modal="true"
        aria-labelledby="quality-title"
        className="relative w-full max-w-[600px] rounded-[20px] border border-white/10 bg-[#1b1c1e]/95 p-8 shadow-[0_28px_80px_rgb(0_0_0/0.65)] backdrop-blur-md"
        initial={{ opacity: 0, y: 16, scale: 0.97 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ type: 'spring', stiffness: 240, damping: 26 }}
      >
        <h1 id="quality-title" className="text-[22px] leading-tight font-bold text-white">
          {t('quality.title')}
        </h1>
        <p className="mt-2 text-[13.5px] leading-snug text-[#a7abb3]">{t('quality.subtitle')}</p>

        <div className="mt-6">
          <TierCards value={tier} disabled={working} onChange={setTier} />
        </div>

        <div className="mt-6">
          <CheckRow checked={remember} disabled={working} onChange={setRemember} label={t('quality.remember')} />
          <p className="mt-4 text-[12.5px] text-[#a7abb3]">{working ? t('quality.wait') : t('quality.note')}</p>
        </div>

        <div className="mt-6 grid grid-cols-2 gap-4">
          <button
            type="button"
            disabled={working}
            onClick={skip}
            className="h-12 cursor-pointer rounded-xl bg-white/6 text-[14.5px] font-semibold text-[#c9ccd1] transition-[background-color,transform] hover:bg-white/10 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-50 disabled:active:scale-100"
          >
            {t('quality.cancel')}
          </button>
          <button
            type="button"
            disabled={working}
            onClick={() => void apply(server.id, tier, remember)}
            className="relative h-12 cursor-pointer overflow-hidden rounded-xl bg-[#e9eaec] text-[14.5px] font-semibold text-[#141518] transition-[background-color,transform] hover:bg-white active:scale-[0.98] disabled:cursor-wait disabled:active:scale-100"
          >
            {working && (
              <span
                className="absolute inset-y-0 left-0 bg-black/12 transition-[width] duration-300"
                style={{ width: `${Math.max(3, progress * 100)}%` }}
                aria-hidden
              />
            )}
            <span className="relative">{working ? t('quality.installing', { percent: Math.round(progress * 100) }) : t('quality.apply')}</span>
          </button>
        </div>
      </motion.div>
    </div>
  )
}
