import { AnimatePresence, motion } from 'framer-motion'
import type { ReactNode } from 'react'
import { useNotifyStore, type NoticeKind } from '../../store/notify-store'
import { AlertIcon, CheckIcon, CloseIcon, InfoIcon } from '../ui/icons'

const KIND_COLOR: Record<NoticeKind, string> = {
  info: 'var(--color-accent)',
  success: 'var(--color-hp-high)',
  warning: 'var(--color-hp-mid)',
  error: 'var(--color-hp-low)'
}

const KIND_ICON: Record<NoticeKind, ReactNode> = {
  info: <InfoIcon size={18} />,
  success: <CheckIcon size={18} />,
  warning: <AlertIcon size={18} />,
  error: <AlertIcon size={18} />
}

/**
 * SAO sistem mesajı tarzı pop-up'lar: üst ortada, dikey olarak "açılan" ince
 * çerçeveli paneller. Tıklayınca kapanır.
 */
export function SystemNotifications(): React.JSX.Element {
  const notices = useNotifyStore((s) => s.notices)
  const dismiss = useNotifyStore((s) => s.dismiss)

  return (
    <div className="pointer-events-none fixed inset-x-0 top-12 z-50 flex flex-col items-center gap-2" aria-live="polite">
      <AnimatePresence initial={false}>
        {notices.map((n) => (
          <motion.button
            key={n.id}
            type="button"
            layout
            onClick={() => dismiss(n.id)}
            initial={{ opacity: 0, scaleY: 0.1, y: -6 }}
            animate={{ opacity: 1, scaleY: 1, y: 0 }}
            exit={{ opacity: 0, scaleY: 0.1, transition: { duration: 0.18 } }}
            transition={{ type: 'spring', stiffness: 520, damping: 34 }}
            className="glass-strong pointer-events-auto relative flex w-[400px] cursor-pointer items-start gap-3 overflow-hidden rounded-xl! px-4 py-3 text-left"
            style={{ ['--kind' as string]: KIND_COLOR[n.kind] }}
          >
            {/* Üst ışık çizgisi */}
            <span
              className="absolute inset-x-6 top-0 h-px"
              style={{ background: 'linear-gradient(90deg, transparent, var(--kind), transparent)' }}
            />
            <span
              className="mt-0.5 grid size-8 shrink-0 place-items-center rounded-full"
              style={{
                color: 'var(--kind)',
                boxShadow: 'inset 0 0 0 1.5px var(--kind), 0 0 14px color-mix(in oklab, var(--kind) 45%, transparent)'
              }}
            >
              {KIND_ICON[n.kind]}
            </span>
            <span className="min-w-0 flex-1">
              <span className="block text-[14px] font-bold">{n.title}</span>
              {n.message && <span className="text-muted mt-0.5 block text-[13px] leading-snug">{n.message}</span>}
            </span>
            <CloseIcon size={14} className="text-muted mt-1 shrink-0" />
          </motion.button>
        ))}
      </AnimatePresence>
    </div>
  )
}
