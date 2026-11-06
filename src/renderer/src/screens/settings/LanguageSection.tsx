import { useTranslation } from 'react-i18next'
import { LANGUAGES } from '@shared/types'
import { Segmented } from '../../components/ui/Segmented'
import { useSettingsStore } from '../../store/settings-store'
import { SettingsSection } from './SettingsSection'

/** Arayüz dili (TR/EN). */
export function LanguageSection(): React.JSX.Element {
  const { t } = useTranslation()
  const language = useSettingsStore((s) => s.settings!.language)
  const update = useSettingsStore((s) => s.update)

  return (
    <SettingsSection title={t('settings.language.title')}>
      <Segmented
        lang="en"
        value={language}
        onChange={(lang) => update({ language: lang })}
        options={LANGUAGES.map((l) => ({ value: l, label: t(`settings.language.${l}`) }))}
      />
    </SettingsSection>
  )
}
