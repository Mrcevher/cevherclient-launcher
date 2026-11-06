import { useTranslation } from 'react-i18next'
import { PageLayout } from '../../components/layout/PageLayout'
import { useSettingsStore } from '../../store/settings-store'
import { JavaSection } from './JavaSection'
import { JvmSection } from './JvmSection'
import { LanguageSection } from './LanguageSection'
import { MemorySection } from './MemorySection'
import { QualitySection } from './QualitySection'
import { ResolutionSection } from './ResolutionSection'

/** Ayarlar: değişiklikler anında uygulanır ve diske (settings.json) kaydedilir. */
export function SettingsScreen(): React.JSX.Element {
  const { t } = useTranslation()
  const ready = useSettingsStore((s) => s.settings !== null)

  return (
    <PageLayout title={t('nav.settings')}>
      {ready && (
        <>
          <p className="-mt-1 text-xs text-muted">{t('settings.autosave')}</p>
          <QualitySection />
          <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <div className="flex flex-col gap-4">
              <MemorySection />
              <JavaSection />
            </div>
            <div className="flex flex-col gap-4">
              <ResolutionSection />
              <JvmSection />
              <LanguageSection />
            </div>
          </div>
        </>
      )}
    </PageLayout>
  )
}
