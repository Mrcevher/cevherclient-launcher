import { useTranslation } from 'react-i18next'
import { DEFAULT_SETTINGS } from '@shared/types'
import { Button } from '../../components/ui/Button'
import { useSettingsStore } from '../../store/settings-store'
import { SettingsSection } from './SettingsSection'

/** Ek JVM argümanları. */
export function JvmSection(): React.JSX.Element {
  const { t } = useTranslation()
  const jvmArgs = useSettingsStore((s) => s.settings!.jvmArgs)
  const update = useSettingsStore((s) => s.update)

  return (
    <SettingsSection
      title={t('settings.jvm.title')}
      aside={
        jvmArgs !== DEFAULT_SETTINGS.jvmArgs && (
          <Button size="sm" variant="ghost" onClick={() => update({ jvmArgs: DEFAULT_SETTINGS.jvmArgs })}>
            {t('settings.jvm.reset')}
          </Button>
        )
      }
    >
      <textarea
        className="field h-20 resize-none font-mono text-xs!"
        value={jvmArgs}
        spellCheck={false}
        aria-label={t('settings.jvm.title')}
        onChange={(e) => update({ jvmArgs: e.target.value })}
      />
      <p className="text-muted mt-1.5 text-xs">{t('settings.jvm.hint')}</p>
    </SettingsSection>
  )
}
