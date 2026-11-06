import { AnimatePresence, motion, useMotionValueEvent, useReducedMotion, useScroll, useTransform } from 'framer-motion'
import { useEffect, useLayoutEffect, useRef, useState, type RefObject } from 'react'
import { useTranslation } from 'react-i18next'
import { ChevronThinIcon } from '../../components/ui/icons'
import { Sidebar } from '../../components/layout/Sidebar'
import { BackdropVideo } from '../../components/ui/BackdropVideo'
import { useSelectedServer } from '../../hooks/use-selected-server'
import { useLaunchStore } from '../../store/launch-store'
import { useSessionStore } from '../../store/session-store'
import { useSettingsStore } from '../../store/settings-store'
import { useUpdateStore } from '../../store/update-store'
import { SystemRequirements } from './SystemRequirements'
import { usePlay } from './use-play'

/** Bu kadar kaydırınca OYNA sol üste yerleşir. */
const DOCK_AFTER_PX = 48
/** Sol üstteki küçük OYNA: hamburgerin (10px + 44px) sağında, onunla aynı hizada. */
const DOCKED_FRAME = { left: 64, top: 10, width: 184, height: 44 }
/** Hero'daki büyük OYNA'nın yüksekliği (cevher-buttons.css --cv-height). */
const PLAY_HEIGHT = 68
const DOCK_SPRING = { type: 'spring', stiffness: 260, damping: 30, mass: 0.9 } as const

type Size = { width: number; height: number }

/** `value`'nun [from, to] aralığındaki konumu, 0–1 arasına sıkıştırılmış. */
function progress(value: number, from: number, to: number): number {
  if (to <= from) return value > from ? 1 : 0
  return Math.min(1, Math.max(0, (value - from) / (to - from)))
}

function useElementSize(ref: RefObject<HTMLElement | null>): Size {
  const [size, setSize] = useState<Size>({ width: 0, height: 0 })
  useLayoutEffect(() => {
    const el = ref.current
    if (!el) return
    const observer = new ResizeObserver(([entry]) => setSize({ width: entry.contentRect.width, height: entry.contentRect.height }))
    observer.observe(el)
    return () => observer.disconnect()
  }, [ref])
  return size
}

/**
 * Ana sayfa: sabit, sessiz video arka planın üstünden kayan içerik. En üstte ortada büyük OYNA ve
 * sol altta oyuncu / sürüm bilgisi; aşağı kaydırdıkça sistem gereksinimleri kartı belirir ve OYNA
 * sol üste yerleşir. Menü, OYNA (ve App'teki üst çubuk) kaydırma alanının dışında olduğu için yerinde kalır.
 */
export function HomeScreen(): React.JSX.Element {
  const { t } = useTranslation()
  const server = useSelectedServer()
  const account = useSessionStore((s) => s.auth?.account)
  const version = useSettingsStore((s) => s.system?.launcherVersion)
  const onPlay = usePlay(server)
  const reduceMotion = useReducedMotion()

  const rootRef = useRef<HTMLDivElement>(null)
  const scrollerRef = useRef<HTMLDivElement>(null)
  const requirementsRef = useRef<HTMLElement>(null)
  const size = useElementSize(rootRef)
  const heightRef = useRef(0)
  heightRef.current = size.height

  const { scrollY } = useScroll({ container: scrollerRef })
  const [docked, setDocked] = useState(false)
  useMotionValueEvent(scrollY, 'change', (y) => setDocked(y > DOCK_AFTER_PX))

  // Alttaki karartma yalnızca en üstte (OYNA ve bilgi satırı okunaklı kalsın); kaydırınca kalkar.
  const shade = useTransform(scrollY, (y) => 1 - progress(y, 0, heightRef.current * 0.4))
  // Kart aşağıdan süzülerek belirir.
  const cardOpacity = useTransform(scrollY, (y) => progress(y, heightRef.current * 0.2, heightRef.current * 0.7))
  const cardY = useTransform(cardOpacity, (p) => (reduceMotion ? 0 : (1 - p) * 48))

  return (
    <div ref={rootRef} className="absolute inset-0 bg-bg">
      {/* Sabit arka plan; içerik üstünden kayar */}
      <BackdropVideo />
      <motion.div
        className="pointer-events-none absolute inset-0"
        style={{ opacity: shade, background: 'linear-gradient(180deg, transparent 55%, rgb(0 0 0 / 0.5))' }}
      />

      <div ref={scrollerRef} className="absolute inset-0 overflow-x-hidden overflow-y-auto [scrollbar-width:none]">
        <div className="relative z-[1]">
          {/* İlk ekran: üstünde OYNA (PlayDock) duran, gökteki kalenin göründüğü alan */}
          <div className="relative h-screen">
            {/* Aşağıdaki bölüme kaydırma ipucu */}
            <button
              type="button"
              onClick={() => requirementsRef.current?.scrollIntoView({ behavior: 'smooth' })}
              className="absolute inset-x-0 bottom-1.5 z-10 mx-auto flex w-max cursor-pointer flex-col items-center text-[11px] font-bold tracking-[0.12em] text-white/45 uppercase transition-colors hover:text-white/80"
            >
              {t('home.requirements.title')}
              <ChevronThinIcon size={13} />
            </button>

            <div className="absolute bottom-3 left-4 z-30 flex items-center gap-2">
              <span className="pill h-7 text-[12px] font-medium text-[#c3c6cb]">
                {account?.username} · Minecraft {server?.minecraftVersion ?? '—'} · CevherClient v{version ?? '…'}
              </span>
            </div>
          </div>

          <SystemRequirements ref={requirementsRef} cardStyle={{ opacity: cardOpacity, y: cardY }} />
        </div>
      </div>

      <Sidebar />

      {size.width > 0 && (
        <PlayDock
          size={size}
          docked={docked}
          serverReady={!!server}
          onPlay={onPlay}
          // Buton kaydırma alanının dışında: üzerindeyken çevrilen tekerlek de sayfayı kaydırsın.
          onWheel={(e) => scrollerRef.current?.scrollBy({ top: e.deltaY, behavior: 'smooth' })}
        />
      )}
    </div>
  )
}

/**
 * OYNA'nın yeri: sayfanın en üstündeyken altta ortada büyük; aşağı kaydırılınca yaylı bir
 * geçişle küçülüp sol üste, hamburgerin yanına kayar. Yukarı dönünce eski yerine iner.
 */
function PlayDock({
  size,
  docked,
  serverReady,
  onPlay,
  onWheel
}: {
  size: Size
  docked: boolean
  serverReady: boolean
  onPlay(): void
  onWheel(e: React.WheelEvent): void
}): React.JSX.Element {
  const width = Math.min(380, Math.max(260, size.width * 0.296))
  const frame = docked
    ? DOCKED_FRAME
    : { left: (size.width - width) / 2, top: size.height * (1 - 0.065) - PLAY_HEIGHT, width, height: PLAY_HEIGHT }

  return (
    <motion.div className="no-drag absolute z-50" initial={false} animate={frame} transition={DOCK_SPRING} onWheel={onWheel}>
      <PlayArea docked={docked} serverReady={serverReady} onPlay={onPlay} />
    </motion.div>
  )
}

/** OYNA butonu; başlatma sürerken aşama + ilerleme, oyun açıkken "Oyun açık" gösterir. */
function PlayArea({ docked, onPlay, serverReady }: { docked: boolean; onPlay(): void; serverReady: boolean }): React.JSX.Element {
  const { t } = useTranslation()
  const phase = useLaunchStore((s) => s.phase)
  const stage = useLaunchStore((s) => s.stage)
  const progress = useLaunchStore((s) => s.progress)
  const running = phase === 'running'

  // Yeni sürüm inerken OYNA, dolan bir çubuğa dönüşür; indikten sonra launcher kendini yeniden başlatıp kurar.
  // Oyun açıkken ya da başlatılırken kesintiye uğramaz (kurulum launcher kapanınca yapılır).
  const update = useUpdateStore((s) => s.status)
  const installUpdate = useUpdateStore((s) => s.install)
  const updating = phase === 'idle' && (update.state === 'available' || update.state === 'downloading' || update.state === 'downloaded')
  const restarting = updating && update.state === 'downloaded'
  const installTried = useRef(false)
  useEffect(() => {
    if (!restarting || installTried.current) return
    installTried.current = true
    const timer = setTimeout(() => void installUpdate(), 900)
    return () => clearTimeout(timer)
  }, [restarting, installUpdate])

  const barLabel = updating ? t(restarting ? 'update.restarting' : 'update.updating') : stage ? t(`launch.stages.${stage}`) : t('home.launching')
  const barProgress = updating ? (restarting ? 1 : (update.progress ?? 0)) : progress

  return (
    <AnimatePresence mode="wait" initial={false}>
      {phase === 'launching' || updating ? (
        <motion.div
          key="progress"
          className={`flex size-full flex-col justify-center rounded-xl border border-white/10 bg-[#0d0e10]/85 backdrop-blur-md ${docked ? 'gap-1 px-3' : 'gap-2 px-5'}`}
          initial={{ opacity: 0, y: 6 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0 }}
        >
          <div className={`flex items-baseline justify-between gap-2 font-semibold ${docked ? 'text-[11px]' : 'text-[13px]'}`}>
            <span className="truncate">{barLabel}</span>
            <span className="tabular-nums text-[#c3c6cb]">{Math.round(barProgress * 100)}%</span>
          </div>
          <div className={`overflow-hidden rounded-full bg-white/10 ${docked ? 'h-1.5' : 'h-2'}`}>
            <div
              className="h-full rounded-full bg-primary shadow-[0_0_12px_var(--color-primary)] transition-[width] duration-300"
              style={{ width: `${Math.max(3, barProgress * 100)}%` }}
            />
          </div>
        </motion.div>
      ) : (
        // Giriş animasyonu sarmalayıcıda: butonun hover/active transform'u CSS'te (styles/cevher-buttons.css).
        <motion.div key="play" className="size-full" initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}>
          {/* Oyun açıkken "is-running" (kristal camlı OYUN AÇIK): disabled değil, tıklama yalnızca yok sayılır.
              Yükseklik sarmalayıcıdan gelir; yazı ve iç boşluk yerleşirken birlikte küçülür. */}
          <motion.button
            type="button"
            onClick={running ? undefined : onPlay}
            disabled={!serverReady || phase === 'linkStart'}
            aria-disabled={running || undefined}
            className={running ? 'cv-btn is-running' : 'cv-btn'}
            style={{ '--cv-height': '100%' } as React.CSSProperties}
            initial={false}
            animate={docked ? { fontSize: '14px', paddingLeft: '16px', paddingRight: '16px' } : { fontSize: '22px', paddingLeft: '28px', paddingRight: '28px' }}
            transition={DOCK_SPRING}
          >
            {running ? t('home.running') : t('home.play')}
          </motion.button>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
