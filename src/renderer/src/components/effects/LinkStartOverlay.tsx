import { AnimatePresence, motion, useReducedMotion } from 'framer-motion'
import { useMemo } from 'react'
import { LINK_START_DURATION_MS } from '@shared/constants'
import { useLaunchStore } from '../../store/launch-store'

/** Işık çizgilerinin renk paleti (tema bağımsız, çok renkli tünel). */
const STREAK_COLORS = ['#38e1ff', '#ff4fd8', '#ffe14d', '#5dff8f', '#7c6bff', '#ffffff', '#ff7a45']

interface Streak {
  angle: number
  color: string
  thick: number
  dur: number
  delay: number
  start: number
}

function buildStreaks(count: number): Streak[] {
  return Array.from({ length: count }, (_, i) => ({
    angle: (360 / count) * i + Math.random() * 6,
    color: STREAK_COLORS[Math.floor(Math.random() * STREAK_COLORS.length)]!,
    thick: 1.5 + Math.random() * 3,
    dur: 0.45 + Math.random() * 0.5,
    delay: Math.random() * 0.55,
    start: 2 + Math.random() * 12
  }))
}

const DURATION_S = LINK_START_DURATION_MS / 1000

/**
 * "LINK START" geçişi: merkezden dışarı akan renkli ışık çizgileriyle tünel efekti,
 * sonunda beyaz bir parlama. Oyna butonuna basılınca ~1.5 sn oynar, ardından
 * launch-store başlatma aşamasına geçer.
 */
export function LinkStartOverlay(): React.JSX.Element {
  const active = useLaunchStore((s) => s.phase === 'linkStart')
  const reduceMotion = useReducedMotion()
  // Her oynatmada farklı bir desen için `active` değişince yeniden üret.
  const streaks = useMemo(() => (active ? buildStreaks(90) : []), [active])

  return (
    <AnimatePresence>
      {active && (
        <motion.div
          key="link-start"
          className="fixed inset-0 z-[60] overflow-hidden"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0, transition: { duration: 0.4 } }}
          transition={{ duration: 0.12 }}
          style={{ background: 'radial-gradient(circle at 50% 50%, #0a1022 0%, #000 70%)' }}
          aria-live="assertive"
        >
          {!reduceMotion &&
            streaks.map((s, i) => (
              <span
                key={i}
                className="link-streak"
                style={
                  {
                    '--angle': `${s.angle}deg`,
                    '--color': s.color,
                    '--thick': `${s.thick}px`,
                    '--dur': `${s.dur}s`,
                    '--delay': `${s.delay}s`,
                    '--start': `${s.start}vmax`
                  } as React.CSSProperties
                }
              />
            ))}

          {/* Tünelin merkezindeki çekirdek ışık */}
          <motion.div
            className="absolute top-1/2 left-1/2 size-40 -translate-x-1/2 -translate-y-1/2 rounded-full"
            style={{ background: 'radial-gradient(circle, #fff 0%, rgb(140 220 255 / 0.6) 25%, transparent 70%)' }}
            initial={{ scale: 0.2, opacity: 0.4 }}
            animate={{ scale: [0.2, 1, 0.8, 6], opacity: [0.4, 1, 0.9, 1] }}
            transition={{ duration: DURATION_S, times: [0, 0.3, 0.7, 1], ease: 'easeIn' }}
          />

          <motion.h1
            lang="en"
            className="tech absolute inset-x-0 top-1/2 -translate-y-1/2 text-center text-6xl font-bold text-white uppercase"
            style={{ textShadow: '0 0 18px rgb(120 220 255 / 0.9), 0 0 42px rgb(120 120 255 / 0.6)' }}
            initial={{ opacity: 0, letterSpacing: '0.9em', scale: 1.15 }}
            animate={{ opacity: [0, 1, 1, 0], letterSpacing: ['0.9em', '0.32em', '0.3em', '0.5em'], scale: [1.15, 1, 1, 1.4] }}
            transition={{ duration: DURATION_S, times: [0, 0.3, 0.75, 1] }}
          >
            Link Start
          </motion.h1>

          {/* Son parlama */}
          <motion.div
            className="absolute inset-0 bg-white"
            initial={{ opacity: 0 }}
            animate={{ opacity: [0, 0, 0.95] }}
            transition={{ duration: DURATION_S, times: [0, 0.78, 1], ease: 'easeIn' }}
          />
        </motion.div>
      )}
    </AnimatePresence>
  )
}
