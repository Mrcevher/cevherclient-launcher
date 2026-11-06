import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import type { Language, NewsItem } from '@shared/types'
import { localize } from '@shared/utils'
import { Markdown } from '../../components/ui/Markdown'
import { formatDate, formatRelative } from '../../lib/format'

/** Tek duyuru kartı: tarih rozeti, başlık, markdown içerik. */
export function NewsCard({ item, index, latest }: { item: NewsItem; index: number; latest: boolean }): React.JSX.Element {
  const { i18n } = useTranslation()
  const lang = i18n.language as Language
  const date = new Date(item.date)

  return (
    <motion.article
      className="panel flex gap-5 p-5"
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: index * 0.05, duration: 0.25 }}
    >
      <div
        className={`flex w-16 shrink-0 flex-col items-center justify-center self-start rounded-xl border py-2 ${
          latest ? 'border-primary/60 bg-primary/12 text-primary' : 'border-border bg-panel-2 text-text'
        }`}
        title={formatDate(item.date, lang)}
      >
        <span className="text-[26px] leading-none font-extrabold tabular-nums">{date.getDate()}</span>
        <span className="mt-1 text-[11px] font-bold tracking-wider uppercase">
          {new Intl.DateTimeFormat(lang, { month: 'short' }).format(date)}
        </span>
      </div>

      <div className="min-w-0 flex-1">
        <div className="flex items-baseline justify-between gap-3">
          <h2 className="text-[17px] font-bold">{localize(item.title, lang)}</h2>
          <span className="shrink-0 text-xs text-muted">{formatRelative(item.date, lang)}</span>
        </div>
        <Markdown>{localize(item.content, lang)}</Markdown>
      </div>
    </motion.article>
  )
}
