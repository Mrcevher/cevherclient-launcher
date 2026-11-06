import { useTranslation } from 'react-i18next'
import { Button } from '../../components/ui/Button'
import { FolderIcon } from '../../components/ui/icons'
import { call } from '../../lib/api'
import { reportError } from '../../lib/errors'
import { useSettingsStore } from '../../store/settings-store'
import { SettingsSection } from './SettingsSection'

/** Java yolu: dosya seçici veya "Otomatik". */
export function JavaSection(): React.JSX.Element {
  const { t } = useTranslation()
  const javaPath = useSettingsStore((s) => s.settings!.javaPath)
  const installs = useSettingsStore((s) => s.javaInstalls)
  const update = useSettingsStore((s) => s.update)

  const browse = async (): Promise<void> => {
    try {
      const picked = await call('dialog:pickJava')
      if (picked) update({ javaPath: picked })
    } catch (err) {
      reportError(err)
    }
  }

  return (
    <SettingsSection title={t('settings.java.title')}>
      <div className="flex gap-2">
        <div
          className={`field flex items-center truncate text-xs ${javaPath ? 'font-mono' : 'text-muted'}`}
          title={javaPath ?? undefined}
        >
          {javaPath ?? t('settings.java.autoHint')}
        </div>
        <Button icon={<FolderIcon size={16} />} onClick={() => void browse()} className="shrink-0">
          {t('settings.java.browse')}
        </Button>
        <Button variant={javaPath ? 'outline' : 'primary'} onClick={() => update({ javaPath: null })} className="shrink-0">
          {t('settings.java.auto')}
        </Button>
      </div>

      <p className="mt-4 mb-1.5 text-[12.5px] font-semibold text-muted">{t('settings.java.detected')}</p>
      <ul className="space-y-1">
        {installs.map((j) => (
          <li key={j.path} className="flex items-center gap-2 text-xs">
            <span className="rounded-md bg-primary/15 px-1.5 text-[12px] font-bold text-primary">Java {j.majorVersion}</span>
            <span className="text-muted truncate font-mono" title={j.path}>
              {j.path}
            </span>
          </li>
        ))}
      </ul>
    </SettingsSection>
  )
}
