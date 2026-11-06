import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { PageLayout } from '../../components/layout/PageLayout'
import { Checkbox } from '../../components/ui/Checkbox'
import { Panel } from '../../components/ui/Panel'
import { useSelectedServer } from '../../hooks/use-selected-server'
import { useModBrowserStore } from '../../store/mod-browser-store'
import { useModsStore } from '../../store/mods-store'
import { useSettingsStore } from '../../store/settings-store'
import { ModBrowser } from './ModBrowser'

/**
 * Modlar: Fabric mod yükleyicisini açıp kapatma, Performans Paketi (Sodium, Lithium …) ve
 * Modrinth araması (launcher'dan çıkmadan mod kurma).
 * Modlar seçili sunucunun oyun klasörüne kurulur.
 */
export function ModsScreen(): React.JSX.Element {
  const { t } = useTranslation()
  const server = useSelectedServer()
  const useFabric = useSettingsStore((s) => s.settings?.useFabric ?? true)
  const autoInstall = useSettingsStore((s) => s.settings?.perfPackAutoInstall ?? true)
  const update = useSettingsStore((s) => s.update)
  const { pack, busy, progress, load, install, remove } = useModsStore()

  useEffect(() => {
    if (server) void load(server.id)
  }, [server, load])

  useEffect(() => {
    if (server && busy === null) void useModBrowserStore.getState().loadUser(server.id)
  }, [server, busy, pack])

  const installedCount = pack?.mods.filter((m) => m.installedVersion).length ?? 0

  return (
    <PageLayout title={t('nav.mods')}>
      <Panel title={t('mods.loaderTitle')}>
        <Checkbox checked={useFabric} onChange={(v) => update({ useFabric: v })} label={t('mods.useFabric')} />
        <p className={`mt-3 text-[13px] ${useFabric ? 'text-hp-high' : 'text-muted'}`}>
          {useFabric ? t('mods.fabricOn') : t('mods.fabricOff')}
        </p>
      </Panel>

      <Panel
        title={t('mods.packTitle')}
        aside={
          pack && (
            <span className="text-[12px] font-semibold text-muted">
              {t('mods.packCount', { installed: installedCount, total: pack.mods.length - pack.unavailable.length })} · Minecraft{' '}
              {pack.minecraftVersion}
            </span>
          )
        }
      >
        <ul className="space-y-1.5">
          {(pack?.mods ?? []).map((m) => {
            const unavailable = pack?.unavailable.includes(m.slug)
            return (
              <li key={m.slug} className="flex items-baseline gap-2 text-[14px]">
                <span className="text-[#c9ccd1]">•</span>
                <span className="font-semibold text-text">{m.name}</span>
                <span className="min-w-0 flex-1 truncate text-[12.5px] text-muted">{t(`mods.desc.${m.slug}`)}</span>
                {m.installedVersion ? (
                  <span className="shrink-0 text-[11.5px] font-semibold text-hp-high" title={m.installedVersion}>
                    {t('mods.installed')}
                  </span>
                ) : unavailable ? (
                  <span className="shrink-0 text-[11.5px] font-semibold text-hp-mid">{t('mods.unavailable')}</span>
                ) : null}
              </li>
            )
          })}
        </ul>

        {busy === 'install' && (
          <div className="mt-4 h-2 overflow-hidden rounded-full bg-white/8">
            <div className="h-full rounded-full bg-secondary transition-[width] duration-300" style={{ width: `${Math.max(3, progress * 100)}%` }} />
          </div>
        )}

        <div className="mt-4 flex flex-wrap items-center gap-3">
          <button
            type="button"
            className="btn btn-yellow"
            disabled={!server || !useFabric || busy !== null}
            onClick={() => server && void install(server.id)}
          >
            {busy === 'install'
              ? t('mods.installing', { percent: Math.round(progress * 100) })
              : pack?.installed
                ? t('mods.update')
                : t('mods.install')}
          </button>
          {installedCount > 0 && (
            <button
              type="button"
              className="btn btn-outline-red"
              disabled={!server || busy !== null}
              onClick={() => server && void remove(server.id)}
            >
              {t('mods.remove')}
            </button>
          )}
        </div>

        <div className="mt-4">
          <Checkbox
            checked={autoInstall}
            disabled={!useFabric}
            onChange={(v) => update({ perfPackAutoInstall: v })}
            label={t('mods.autoInstall')}
          />
        </div>
      </Panel>
      {server && <ModBrowser server={server} disabled={!useFabric} />}
    </PageLayout>
  )
}
