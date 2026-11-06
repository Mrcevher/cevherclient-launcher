import { useTranslation } from 'react-i18next'
import type { DeviceTier } from '@shared/types'

/** Soldan sağa: en güçlüden en hafife. */
export const TIER_ORDER: readonly DeviceTier[] = ['high', 'medium', 'low']

const ICON_PROPS = {
  width: 22,
  height: 22,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.7,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
  'aria-hidden': true
} as const

/** Yüksek: parıltı. */
function SparkleIcon(): React.JSX.Element {
  return (
    <svg {...ICON_PROPS}>
      <path d="M10 4.5 11.9 9.6 17 11.5 11.9 13.4 10 18.5 8.1 13.4 3 11.5 8.1 9.6z" />
      <path d="M18 3v4M16 5h4" />
      <path d="M18.5 15.5v3.5M16.75 17.25h3.5" />
    </svg>
  )
}

/** Orta: gösterge. */
function GaugeIcon(): React.JSX.Element {
  return (
    <svg {...ICON_PROPS}>
      <path d="M4.5 17.5a8.5 8.5 0 1 1 15 0" />
      <path d="m12 14 3.5-4.5" />
      <circle cx="12" cy="14.5" r="1" />
    </svg>
  )
}

/** Düşük: patates. */
function PotatoIcon(): React.JSX.Element {
  return (
    <svg {...ICON_PROPS}>
      <path d="M6.2 8.6C8 5.4 12.6 4 16.2 5.8c3.4 1.7 4.4 5.6 2.6 9.1-2 3.8-6.6 5.3-10.2 3.5C4.9 16.6 4.2 12 6.2 8.6z" />
      <path d="M10 9.5h.01M14.5 8.5h.01M13 13.5h.01M9.5 14h.01M16 12h.01" />
    </svg>
  )
}

const ICONS: Record<DeviceTier, () => React.JSX.Element> = { high: SparkleIcon, medium: GaugeIcon, low: PotatoIcon }

/**
 * Üç kalite kartı (yüksek / orta / düşük). Seçili kart açık gri zeminli, beyaz çerçeveli ve sağ üstte
 * onay işaretlidir; diğerleri koyu lacivert. Seçim yalnızca durumu değiştirir, kurulumu üst bileşen başlatır.
 */
export function TierCards({
  value,
  disabled,
  onChange
}: {
  value: DeviceTier
  disabled?: boolean
  onChange(tier: DeviceTier): void
}): React.JSX.Element {
  const { t } = useTranslation()

  return (
    <div role="radiogroup" className="grid grid-cols-1 gap-3 sm:grid-cols-3">
      {TIER_ORDER.map((tier) => {
        const selected = value === tier
        const Icon = ICONS[tier]
        return (
          <button
            key={tier}
            type="button"
            role="radio"
            aria-checked={selected}
            disabled={disabled}
            onClick={() => onChange(tier)}
            className={`relative flex min-h-[168px] cursor-pointer flex-col items-start rounded-xl border p-4 text-left text-white transition-[background-color,border-color,transform] duration-150 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-60 disabled:active:scale-100 ${
              selected
                ? 'border-white/85 bg-[#2c2d30]'
                : 'border-white/10 bg-[#050a16] hover:border-white/30'
            }`}
          >
            {selected && (
              <span className="absolute top-3 right-3 grid size-[22px] place-items-center rounded-full bg-white text-[#141518]" aria-hidden>
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3.2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="m5 12.5 4.5 4.5L19 7.5" />
                </svg>
              </span>
            )}
            <Icon />
            <span className="mt-5 text-[17px] font-bold">{t(`quality.tiers.${tier}.name`)}</span>
            <span className="mt-1.5 text-[12.5px] leading-snug text-[#a7abb3]">{t(`quality.tiers.${tier}.desc`)}</span>
          </button>
        )
      })}
    </div>
  )
}

/** Beyaz dolgulu onay kutusu ("Seçimimi hatırla"). */
export function CheckRow({ checked, disabled, onChange, label }: { checked: boolean; disabled?: boolean; onChange(v: boolean): void; label: string }): React.JSX.Element {
  return (
    <button
      type="button"
      role="checkbox"
      aria-checked={checked}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className="flex cursor-pointer items-center gap-3 text-[14px] text-[#d6d8dc] disabled:cursor-not-allowed disabled:opacity-60"
    >
      <span className={`grid size-[18px] place-items-center rounded-[4px] border ${checked ? 'border-white bg-white text-[#141518]' : 'border-white/40'}`}>
        {checked && (
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
            <path d="m5 12.5 4.5 4.5L19 7.5" />
          </svg>
        )}
      </span>
      {label}
    </button>
  )
}
