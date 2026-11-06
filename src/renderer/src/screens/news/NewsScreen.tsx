import { motion } from 'framer-motion'
import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { Language } from '@shared/types'
import { PageLayout } from '../../components/layout/PageLayout'
import { ExternalIcon } from '../../components/ui/icons'
import { SafeHtml } from '../../components/ui/SafeHtml'
import { call } from '../../lib/api'
import { reportError } from '../../lib/errors'
import { formatDate, formatRelative } from '../../lib/format'
import { loadSitePosts, siteOrigin, type SitePost } from '../../lib/site-news'
import { useConfigStore } from '../../store/config-store'
import { NewsCard } from './NewsCard'

type State = { kind: 'loading' } | { kind: 'site'; posts: SitePost[] } | { kind: 'config' }

/** Ekran kapanıp açılınca siteye tekrar gidilmesin (5 dk). */
let cache: { at: number; posts: SitePost[] } | null = null
const CACHE_MS = 5 * 60_000

/**
 * Haberler sitenin blogundan gelir (yeniden eskiye). Site tanımlı değilse, erişilemezse ya da
 * blogda yazı yoksa uzaktan config'deki duyurular gösterilir.
 */
export function NewsScreen(): React.JSX.Element {
  const { t } = useTranslation()
  const news = useConfigStore((s) => s.snapshot?.config.news)
  const [state, setState] = useState<State>(() =>
    cache && Date.now() - cache.at < CACHE_MS ? { kind: 'site', posts: cache.posts } : { kind: 'loading' }
  )

  useEffect(() => {
    if (state.kind !== 'loading') return
    if (!siteOrigin()) {
      setState({ kind: 'config' })
      return
    }
    let alive = true
    loadSitePosts()
      .then((posts) => {
        cache = { at: Date.now(), posts }
        if (alive) setState({ kind: 'site', posts })
      })
      .catch((err) => {
        console.warn('[cevher] site haberleri okunamadı', err)
        if (alive) setState({ kind: 'config' })
      })
    return () => {
      alive = false
    }
  }, [state.kind])

  const configSorted = useMemo(() => [...(news ?? [])].sort((a, b) => Date.parse(b.date) - Date.parse(a.date)), [news])
  const showConfig = state.kind === 'config' || (state.kind === 'site' && state.posts.length === 0)

  return (
    <PageLayout title={t('nav.news')}>
      {state.kind === 'loading' ? (
        <div className="panel p-10 text-center text-sm text-muted">{t('common.loading')}</div>
      ) : showConfig ? (
        configSorted.length === 0 ? (
          <div className="panel p-10 text-center text-sm text-muted">{t('news.empty')}</div>
        ) : (
          configSorted.map((item, i) => <NewsCard key={item.id} item={item} index={i} latest={i === 0} />)
        )
      ) : (
        state.posts.map((post, i) => <SitePostCard key={post.url} post={post} index={i} latest={i === 0} />)
      )}
    </PageLayout>
  )
}

function SitePostCard({ post, index, latest }: { post: SitePost; index: number; latest: boolean }): React.JSX.Element {
  const { t, i18n } = useTranslation()
  const lang = i18n.language as Language
  const [open, setOpen] = useState(index === 0)
  const date = post.date ? new Date(post.date) : null
  const long = (post.content?.textContent ?? '').trim().length > 420

  return (
    <motion.article
      className="panel overflow-hidden"
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: index * 0.05, duration: 0.25 }}
    >
      {post.image && (
        <img src={post.image} alt="" draggable={false} referrerPolicy="no-referrer" className="max-h-[220px] w-full object-cover" />
      )}
      <div className="flex gap-5 p-5">
        {date && (
          <div
            className={`flex w-16 shrink-0 flex-col items-center justify-center self-start rounded-xl border py-2 ${
              latest ? 'border-primary/60 bg-primary/12 text-primary' : 'border-border bg-panel-2 text-text'
            }`}
            title={formatDate(post.date!, lang)}
          >
            <span className="text-[26px] leading-none font-extrabold tabular-nums">{date.getDate()}</span>
            <span className="mt-1 text-[11px] font-bold tracking-wider uppercase">
              {new Intl.DateTimeFormat(lang, { month: 'short' }).format(date)}
            </span>
          </div>
        )}
        <div className="min-w-0 flex-1">
          <div className="flex items-baseline justify-between gap-3">
            <h2 className="text-[17px] font-bold">{post.title}</h2>
            {post.date && <span className="shrink-0 text-xs text-muted">{formatRelative(post.date, lang)}</span>}
          </div>
          {post.content && (
            <div className={`relative ${!open && long ? 'max-h-[150px] overflow-hidden' : ''}`}>
              <SafeHtml root={post.content} base={post.url} />
              {!open && long && <div className="pointer-events-none absolute inset-x-0 bottom-0 h-14 bg-gradient-to-t from-panel to-transparent" />}
            </div>
          )}
          <div className="mt-3 flex flex-wrap gap-2">
            {long && (
              <button type="button" className="btn btn-ghost" onClick={() => setOpen((v) => !v)}>
                {open ? t('news.less') : t('news.more')}
              </button>
            )}
            <button
              type="button"
              className="btn btn-ghost"
              onClick={() => call('shell:openExternal', post.url).catch((err) => reportError(err))}
            >
              {t('news.openSite')} <ExternalIcon size={15} />
            </button>
          </div>
        </div>
      </div>
    </motion.article>
  )
}
