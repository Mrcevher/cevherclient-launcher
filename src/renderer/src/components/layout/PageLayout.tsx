import { motion } from 'framer-motion'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavStore } from '../../store/nav-store'
import { ArrowLeftIcon } from '../ui/icons'

/** Ana sayfa dışındaki ekranların iskeleti: geri butonu + başlık + kaydırılabilir içerik. */
export function PageLayout({ title, children }: { title: string; children: ReactNode }): React.JSX.Element {
  const { t } = useTranslation()
  const go = useNavStore((s) => s.go)

  return (
    <div className="absolute inset-0 flex flex-col bg-bg">
      <div className="flex shrink-0 items-center gap-4 px-6 pt-12 pb-3">
        <button
          type="button"
          aria-label={t('common.back')}
          onClick={() => go('home')}
          className="no-drag relative z-50 grid size-11 cursor-pointer place-items-center rounded-xl border border-border bg-panel text-[#c9ccd1] transition-colors hover:border-[#3a3b41] hover:text-white"
        >
          <ArrowLeftIcon size={20} />
        </button>
        <h1 className="text-[24px] font-extrabold text-[#cfd2d6]">{title}</h1>
      </div>
      <motion.div
        className="min-h-0 flex-1 overflow-y-auto px-6 pb-6"
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.2 }}
      >
        <div className="flex flex-col gap-4">{children}</div>
      </motion.div>
    </div>
  )
}
