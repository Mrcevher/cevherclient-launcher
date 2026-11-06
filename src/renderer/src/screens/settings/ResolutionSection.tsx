import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useSettingsStore } from '../../store/settings-store'
import { SettingsSection, Toggle } from './SettingsSection'

const PRESETS: Array<[number, number]> = [
  [854, 480],
  [1280, 720],
  [1600, 900],
  [1920, 1080],
  [2560, 1440]
]

/**
 * Oyun penceresi çözünürlüğü. Sayı alanları yazarken sınırlanmasın diye
 * değer odak kaybında (veya Enter'da) kaydedilir.
 */
export function ResolutionSection(): React.JSX.Element {
  const { t } = useTranslation()
  const resolution = useSettingsStore((s) => s.settings!.resolution)
  const update = useSettingsStore((s) => s.update)
  const [width, setWidth] = useState(String(resolution.width))
  const [height, setHeight] = useState(String(resolution.height))

  // Main process değeri sınırladıysa veya hazır boyut seçildiyse alanları eşitle.
  useEffect(() => {
    setWidth(String(resolution.width))
    setHeight(String(resolution.height))
  }, [resolution.width, resolution.height])

  const commit = (): void => {
    const w = parseInt(width, 10)
    const h = parseInt(height, 10)
    update({ resolution: { ...resolution, width: Number.isFinite(w) ? w : resolution.width, height: Number.isFinite(h) ? h : resolution.height } })
  }

  const presetValue = PRESETS.some(([w, h]) => w === resolution.width && h === resolution.height)
    ? `${resolution.width}x${resolution.height}`
    : ''

  return (
    <SettingsSection title={t('settings.resolution.title')}>
      <div className="grid grid-cols-[1fr_auto_1fr] items-end gap-2">
        <NumberField label={t('settings.resolution.width')} value={width} onChange={setWidth} onCommit={commit} />
        <span className="text-muted pb-2.5">×</span>
        <NumberField label={t('settings.resolution.height')} value={height} onChange={setHeight} onCommit={commit} />
      </div>

      <div className="mt-3 flex items-center justify-between gap-3">
        <select
          className="field w-auto! cursor-pointer"
          aria-label={t('settings.resolution.preset')}
          value={presetValue}
          onChange={(e) => {
            const [w, h] = e.target.value.split('x').map(Number)
            if (w && h) update({ resolution: { ...resolution, width: w, height: h } })
          }}
        >
          <option value="" disabled>
            {t('settings.resolution.preset')}
          </option>
          {PRESETS.map(([w, h]) => (
            <option key={w} value={`${w}x${h}`}>
              {w} × {h}
            </option>
          ))}
        </select>
        <Toggle
          checked={resolution.fullscreen}
          onChange={(fullscreen) => update({ resolution: { ...resolution, fullscreen } })}
          label={t('settings.resolution.fullscreen')}
        />
      </div>
      <p className="mt-3 text-[12px] text-muted">{t('settings.resolution.fitHint')}</p>
    </SettingsSection>
  )
}

function NumberField({
  label,
  value,
  onChange,
  onCommit
}: {
  label: string
  value: string
  onChange(v: string): void
  onCommit(): void
}): React.JSX.Element {
  return (
    <label className="block">
      <span className="mb-1 block text-[12.5px] font-semibold text-muted">{label}</span>
      <input
        className="field text-base! font-semibold tabular-nums"
        inputMode="numeric"
        value={value}
        onChange={(e) => onChange(e.target.value.replace(/\D/g, ''))}
        onBlur={onCommit}
        onKeyDown={(e) => e.key === 'Enter' && onCommit()}
      />
    </label>
  )
}
