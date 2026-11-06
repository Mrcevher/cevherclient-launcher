import { useTranslation } from 'react-i18next'
import { MEMORY_STEP_MB, MIN_MEMORY_MB, maxMemoryFor } from '@shared/constants'
import { formatMemory } from '../../lib/format'
import { useSettingsStore } from '../../store/settings-store'
import { SettingsSection } from './SettingsSection'

/** RAM slider'ı: üst sınır sistem RAM'ine göre belirlenir. */
export function MemorySection(): React.JSX.Element {
  const { t } = useTranslation()
  const memoryMb = useSettingsStore((s) => s.settings!.memoryMb)
  const totalMb = useSettingsStore((s) => s.system?.totalMemoryMb ?? 8192)
  const update = useSettingsStore((s) => s.update)

  const max = maxMemoryFor(totalMb)
  const value = Math.min(memoryMb, max)
  const fill = max > MIN_MEMORY_MB ? ((value - MIN_MEMORY_MB) / (max - MIN_MEMORY_MB)) * 100 : 100

  return (
    <SettingsSection
      title={t('settings.memory.title')}
      aside={<span className="text-2xl font-extrabold tabular-nums">{formatMemory(value)}</span>}
    >
      <input
        type="range"
        className="range"
        min={MIN_MEMORY_MB}
        max={max}
        step={MEMORY_STEP_MB}
        value={value}
        style={{ ['--fill' as string]: `${fill}%` }}
        aria-label={t('settings.memory.title')}
        onChange={(e) => update({ memoryMb: Number(e.target.value) })}
      />
      <div className="mt-2 flex justify-between text-[12px] font-semibold text-muted tabular-nums">
        <span>{formatMemory(MIN_MEMORY_MB)}</span>
        <span>{formatMemory(max)}</span>
      </div>
      <p className="text-muted mt-2 text-xs">
        {t('settings.memory.system', { total: formatMemory(totalMb) })} · {t('settings.memory.hint')}
      </p>
    </SettingsSection>
  )
}
