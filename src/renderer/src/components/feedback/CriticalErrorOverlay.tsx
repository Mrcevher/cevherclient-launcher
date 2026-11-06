import { AnimatePresence, motion } from 'framer-motion'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNotifyStore } from '../../store/notify-store'
import { Button } from '../ui/Button'
import { AlertIcon } from '../ui/icons'

/** Düz üstlü altıgen (600×520) köşe noktaları. */
const HEX = '150,4 450,4 596,260 450,516 150,516 4,260'
const HEX_INNER = '160,20 440,20 578,260 440,500 160,500 22,260'

/**
 * Kritik hatalar için altıgen çerçeveli tam ekran uyarı.
 * Mesaj metni lib/errors.ts'deki çeviri katmanından hazır gelir.
 */
export function CriticalErrorOverlay(): React.JSX.Element {
  const { t } = useTranslation()
  const critical = useNotifyStore((s) => s.critical)
  const clear = useNotifyStore((s) => s.clearCritical)
  const [showDetail, setShowDetail] = useState(false)

  useEffect(() => {
    setShowDetail(false)
    if (!critical) return
    const onKey = (e: KeyboardEvent): void => {
      if (e.key === 'Escape') clear()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [critical, clear])

  const retry = (): void => {
    const fn = critical?.retry
    clear()
    fn?.()
  }

  return (
    <AnimatePresence>
      {critical && (
        <motion.div
          key="critical"
          role="alertdialog"
          aria-modal
          aria-labelledby="critical-title"
          className="fixed inset-0 z-[70] grid place-items-center backdrop-blur-md"
          style={{ background: 'radial-gradient(circle, rgb(40 0 0 / 0.55), rgb(0 0 0 / 0.82))' }}
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
        >
          <motion.div
            className="relative grid h-[520px] w-[600px] place-items-center"
            initial={{ scale: 0.85, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            exit={{ scale: 0.92, opacity: 0 }}
            transition={{ type: 'spring', stiffness: 300, damping: 26 }}
          >
            <svg className="absolute inset-0" viewBox="0 0 600 520" aria-hidden>
              <polygon points={HEX} fill="rgb(12 4 6 / 0.88)" stroke="var(--color-hp-low)" strokeWidth="2.5" />
              <polygon
                points={HEX_INNER}
                fill="none"
                stroke="var(--color-hp-low)"
                strokeOpacity="0.4"
                strokeWidth="1"
                strokeDasharray="6 8"
              />
              <polygon
                points={HEX}
                fill="none"
                stroke="var(--color-hp-low)"
                strokeWidth="8"
                strokeOpacity="0.25"
                style={{ filter: 'blur(8px)', animation: 'glow-pulse 2.4s ease-in-out infinite' }}
              />
            </svg>

            <div className="relative flex w-[380px] flex-col items-center text-center">
              <div
                className="grid size-16 place-items-center text-hp-low"
                style={{
                  clipPath: 'polygon(25% 0, 75% 0, 100% 50%, 75% 100%, 25% 100%, 0 50%)',
                  background: 'color-mix(in oklab, var(--color-hp-low) 18%, transparent)'
                }}
              >
                <AlertIcon size={34} strokeWidth={2.4} />
              </div>

              <h2 id="critical-title" className="tech mt-4 text-3xl font-bold tracking-wide text-white uppercase">
                {critical.error.title}
              </h2>
              <p className="mt-3 text-sm leading-relaxed text-white/80">{critical.error.message}</p>

              <p className="tech mt-4 text-xs tracking-[0.25em] text-hp-low/90 uppercase">
                {t('common.errorCode')}: {critical.error.code}
              </p>

              {critical.error.detail && (
                <div className="mt-2 w-full">
                  <button
                    type="button"
                    className="cursor-pointer text-xs text-white/50 underline-offset-2 hover:text-white/80 hover:underline"
                    onClick={() => setShowDetail((v) => !v)}
                  >
                    {t('common.details')}
                  </button>
                  {showDetail && (
                    <pre className="mt-2 max-h-20 overflow-auto rounded-lg bg-black/50 p-2 text-left font-mono text-[11px] break-all whitespace-pre-wrap text-white/70 select-text">
                      {critical.error.detail}
                    </pre>
                  )}
                </div>
              )}

              <div className="mt-6 flex gap-3">
                {critical.retry && (
                  <Button variant="danger" onClick={retry}>
                    {t('common.retry')}
                  </Button>
                )}
                <Button variant="outline" onClick={clear} autoFocus>
                  {t('common.ok')}
                </Button>
              </div>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
