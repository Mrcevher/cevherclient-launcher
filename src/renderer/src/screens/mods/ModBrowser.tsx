import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import type { InstalledMod, ModKind, ModSearchHit, ServerProfile } from '@shared/types'
import { CubeIcon, SearchIcon } from '../../components/ui/icons'
import { Panel } from '../../components/ui/Panel'
import { useModBrowserStore } from '../../store/mod-browser-store'

const SEARCH_DELAY_MS = 350
const KINDS: readonly ModKind[] = ['mod', 'shader']

function formatCount(n: number, lang: string): string {
  return new Intl.NumberFormat(lang, { notation: 'compact', maximumFractionDigits: 1 }).format(n)
}

/** Mod/shader ikonu: Modrinth görseli, yoksa düz küp. */
function ModIcon({ url, className = 'size-10' }: { url: string | null; className?: string }): React.JSX.Element {
  return url ? (
    <img src={url} alt="" draggable={false} loading="lazy" className={`${className} shrink-0 rounded-lg bg-panel-2 object-cover`} />
  ) : (
    <span className={`${className} grid shrink-0 place-items-center rounded-lg bg-panel-2 text-muted`}>
      <CubeIcon size={18} />
    </span>
  )
}

/** Kurulu mod ya da shader listesi (kaldırma düğmesiyle). */
function InstalledList({
  title,
  items,
  busyId,
  onRemove
}: {
  title: string
  items: InstalledMod[]
  busyId: string | null
  onRemove: (projectId: string) => void
}): React.JSX.Element | null {
  const { t } = useTranslation()
  if (items.length === 0) return null
  return (
    <Panel title={title} aside={<span className="text-[12px] font-semibold text-muted">{items.length}</span>}>
      <ul className="divide-y divide-border/70">
        {items.map((m) => (
          <li key={m.projectId} className="flex items-center gap-3 py-2 first:pt-0 last:pb-0">
            <ModIcon url={m.iconUrl} className="size-9" />
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2">
                <span className="truncate text-[14px] font-semibold">{m.title}</span>
                {m.dependency && (
                  <span className="rounded bg-white/8 px-1.5 text-[10.5px] font-semibold text-muted">{t('modBrowser.dependency')}</span>
                )}
              </div>
              <div className="truncate text-[12px] text-muted">{m.version}</div>
            </div>
            <button
              type="button"
              className="btn btn-outline-red h-8 px-3.5 text-[12.5px]"
              disabled={busyId !== null}
              onClick={() => onRemove(m.projectId)}
            >
              {t('mods.remove')}
            </button>
          </li>
        ))}
      </ul>
    </Panel>
  )
}

/**
 * Modrinth araması ve tek tıkla kurulum (launcher'dan çıkmadan). İki sekme: Fabric modları ve
 * shader paketleri. Yalnızca sunucunun Minecraft sürümüne uygun olanlar listelenir. Shader kurarken
 * oyunda shader modu yoksa Iris otomatik eklenir (sunucu tarafında).
 */
export function ModBrowser({ server, disabled }: { server: ServerProfile; disabled: boolean }): React.JSX.Element {
  const { t, i18n } = useTranslation()
  const { kind, query, hits, total, searching, user, busyId, setKind, setQuery, search, loadUser, install, remove } =
    useModBrowserStore()
  const isShader = kind === 'shader'

  // Yazmayı bitirince (ya da sekme değişince) ara; boşsa en popülerler.
  useEffect(() => {
    const timer = setTimeout(() => void search(server.id), SEARCH_DELAY_MS)
    return () => clearTimeout(timer)
  }, [kind, query, server.id, search])

  useEffect(() => {
    void loadUser(server.id)
  }, [server.id, loadUser])

  const installedIds = new Set(user?.mods.map((m) => m.projectId))
  const packSlugs = new Set(user?.packSlugs)
  const installedMods = user?.mods.filter((m) => m.kind === 'mod') ?? []
  const installedShaders = user?.mods.filter((m) => m.kind === 'shader') ?? []

  const action = (hit: ModSearchHit): React.JSX.Element => {
    if (!isShader && packSlugs.has(hit.slug)) {
      return <span className="text-[12px] font-semibold text-secondary">{t('modBrowser.inPack')}</span>
    }
    if (installedIds.has(hit.projectId)) {
      return <span className="text-[12px] font-semibold text-hp-high">{t('modBrowser.installed')}</span>
    }
    return (
      <button
        type="button"
        className="btn btn-green h-8 px-4 text-[13px]"
        disabled={disabled || busyId !== null}
        onClick={() => void install(server.id, hit)}
      >
        {busyId === hit.projectId ? t('modBrowser.installing') : t('modBrowser.install')}
      </button>
    )
  }

  return (
    <>
      <InstalledList
        title={t('modBrowser.installedTitleList')}
        items={installedMods}
        busyId={busyId}
        onRemove={(id) => void remove(server.id, id)}
      />
      <InstalledList
        title={t('modBrowser.installedShaders')}
        items={installedShaders}
        busyId={busyId}
        onRemove={(id) => void remove(server.id, id)}
      />

      <Panel
        title={t('modBrowser.title')}
        aside={<span className="text-[12px] font-semibold text-muted">Modrinth · Fabric {server.minecraftVersion}</span>}
      >
        <div className="flex flex-wrap items-center gap-2.5">
          <div role="tablist" className="inline-flex rounded-lg border border-border bg-field p-0.5">
            {KINDS.map((k) => (
              <button
                key={k}
                type="button"
                role="tab"
                aria-selected={kind === k}
                className={`h-8 rounded-md px-4 text-[13px] font-semibold transition-colors ${
                  kind === k ? 'bg-panel-2 text-text shadow-sm' : 'text-muted hover:text-text'
                }`}
                onClick={() => setKind(k)}
              >
                {t(k === 'mod' ? 'modBrowser.tabMods' : 'modBrowser.tabShaders')}
              </button>
            ))}
          </div>
          <label className="relative block min-w-[220px] flex-1">
            <SearchIcon size={16} className="absolute top-1/2 left-3 -translate-y-1/2 text-muted" />
            <input
              className="field h-9 pl-9!"
              placeholder={t(isShader ? 'modBrowser.searchShader' : 'modBrowser.search')}
              value={query}
              spellCheck={false}
              onChange={(e) => setQuery(e.target.value)}
            />
          </label>
        </div>

        {disabled && <p className="mt-2 text-[12.5px] text-hp-mid">{t('modBrowser.fabricOff')}</p>}
        {isShader && (
          <p className="mt-2 text-[12px] leading-snug text-muted">
            {user?.shaderMod ? t('modBrowser.shaderModFound', { name: user.shaderMod }) : t('modBrowser.shaderAutoIris')}
          </p>
        )}
        <p className="mt-2 text-[12px] text-muted">
          {query.trim() ? t('modBrowser.results', { count: total }) : t(isShader ? 'modBrowser.popularShader' : 'modBrowser.popular')}
        </p>

        <ul className="mt-2 max-h-[26rem] divide-y divide-border/70 overflow-y-auto pr-1">
          {hits.map((hit) => (
            <li key={hit.projectId} className="flex items-center gap-3 py-2.5 first:pt-0">
              <ModIcon url={hit.iconUrl} />
              <div className="min-w-0 flex-1">
                <div className="flex items-baseline gap-2">
                  <span className="truncate text-[14px] font-bold">{hit.title}</span>
                  <span className="shrink-0 text-[12px] text-muted">{t('modBrowser.by', { author: hit.author })}</span>
                </div>
                <p className="line-clamp-1 text-[12.5px] leading-snug text-[#a9adb4]">{hit.description}</p>
                <div className="mt-0.5 flex items-center gap-3 text-[11.5px] text-muted">
                  <span>{t('modBrowser.downloads', { count: formatCount(hit.downloads, i18n.language) })}</span>
                  {!isShader && hit.serverSide === 'required' && (
                    <span className="font-semibold text-hp-mid">{t('modBrowser.needsServer')}</span>
                  )}
                </div>
              </div>
              <div className="shrink-0">{action(hit)}</div>
            </li>
          ))}
          {!searching && hits.length === 0 && (
            <li className="py-8 text-center text-sm text-muted">{t(isShader ? 'modBrowser.emptyShader' : 'modBrowser.empty')}</li>
          )}
          {searching && <li className="py-3 text-center text-[12.5px] text-muted">{t('common.loading')}</li>}
          {!searching && hits.length > 0 && hits.length < total && (
            <li className="flex justify-center py-3">
              <button type="button" className="btn btn-ghost h-8 text-[13px]" onClick={() => void search(server.id, true)}>
                {t('modBrowser.more')}
              </button>
            </li>
          )}
        </ul>
      </Panel>
    </>
  )
}
