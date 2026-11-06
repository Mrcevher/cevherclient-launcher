import { motion, type MotionStyle } from 'framer-motion'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  BinaryIcon,
  ChevronThinIcon,
  CircuitBoardIcon,
  GaugeIcon,
  GpuIcon,
  HardDriveIcon,
  LayersIcon,
  MemoryStickIcon,
  MonitorIcon,
  ProcessorIcon,
  RatioIcon,
  ShapesIcon,
  SlidersIcon
} from '../../components/ui/icons'
import { useNavStore } from '../../store/nav-store'
import { useSettingsStore } from '../../store/settings-store'
import {
  COLLAPSED_ROW_COUNT,
  platformTab,
  REQUIREMENT_FPS,
  REQUIREMENT_PLATFORMS,
  REQUIREMENT_ROWS,
  REQUIREMENTS,
  type RequirementLine,
  type RequirementPlatform,
  type RequirementRowKey
} from './requirements'

const ROW_ICONS: Record<RequirementRowKey, typeof MonitorIcon> = {
  os: MonitorIcon,
  arch: BinaryIcon,
  cpu: ProcessorIcon,
  cpuFeatures: LayersIcon,
  gpu: GpuIcon,
  gpuFeatures: ShapesIcon,
  vram: CircuitBoardIcon,
  storage: HardDriveIcon,
  ram: MemoryStickIcon,
  graphics: SlidersIcon,
  resolution: RatioIcon
}

/** Tablonun ortak ızgarası: etiket | minimum | önerilen. */
const ROW = 'grid grid-cols-[338fr_322fr_322fr]'
/** Önerilen sütunu biraz daha açık zeminde durur. */
const REC_BG = 'bg-[#111216]'

/**
 * Ana sayfada videonun altındaki "Sistem gereksinimleri" kartı: Windows / Mac / Linux sekmeleri,
 * minimum (30 FPS) ve önerilen (60 FPS) sütunları; ilk satırlar görünür, gerisi "Daha fazla göster" ile açılır.
 */
export function SystemRequirements({ ref, cardStyle }: { ref?: React.Ref<HTMLElement>; cardStyle?: MotionStyle }): React.JSX.Element {
  const { t } = useTranslation()
  const platform = useSettingsStore((s) => s.system?.platform)
  const sidebarOpen = useNavStore((s) => s.sidebarOpen)
  const [tab, setTab] = useState<RequirementPlatform>(() => platformTab(platform))
  const [expanded, setExpanded] = useState(false)
  const spec = REQUIREMENTS[tab]

  const text = (line: RequirementLine): string => (typeof line === 'string' ? line : t(`home.requirements.values.${line.t}`))

  const renderRow = (key: RequirementRowKey): React.JSX.Element => {
    const RowIcon = ROW_ICONS[key]
    const [min, rec] = spec[key]
    return (
      <div key={key} className={`${ROW} border-t border-white/[0.05]`}>
        <div className="flex items-start gap-[13px] py-[14.5px] pr-4 pl-[15px]">
          <span className="grid size-7 shrink-0 place-items-center rounded-lg bg-[#19181e] text-[#8f8e94]">
            <RowIcon size={14} />
          </span>
          <span className="text-[13px] leading-7 text-[#86868b]">{t(`home.requirements.rows.${key}`)}</span>
        </div>
        <ValueCell lines={min.map(text)} />
        <ValueCell lines={rec.map(text)} className={REC_BG} />
      </div>
    )
  }

  return (
    // Zemin saydam: ana sayfanın uzun video arka planı kartın arkasında görünür.
    // Sol menü açıkken kart menünün altında kalmasın diye yanına kayar (menü 250px).
    <section
      ref={ref}
      className={`relative min-h-screen pt-[72px] pr-6 pb-12 transition-[padding] duration-300 ${sidebarOpen ? 'pl-[262px]' : 'pl-6'}`}
    >
      <motion.div
        className="relative mx-auto max-w-[1032px] rounded-[22px] border border-white/[0.07] p-6"
        style={{ background: 'linear-gradient(180deg, #111018, #0e0f14)', ...cardStyle }}
      >
        <div className="flex items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <span className="grid size-9 place-items-center rounded-[10px] border border-white/[0.08] bg-[#1d1b24] text-[#c9c8ce]">
              <ProcessorIcon size={16} />
            </span>
            <h2 className="text-[18px] font-semibold tracking-[-0.01em] text-white">{t('home.requirements.title')}</h2>
          </div>

          <div role="tablist" aria-label={t('home.requirements.title')} className="flex rounded-full border border-white/[0.07] bg-[#18181e] p-1">
            {REQUIREMENT_PLATFORMS.map((p) => {
              const active = p.id === tab
              return (
                <button
                  key={p.id}
                  type="button"
                  role="tab"
                  aria-selected={active}
                  onClick={() => setTab(p.id)}
                  className={`relative h-[33px] cursor-pointer rounded-full px-4 text-[14px] font-semibold transition-colors ${active ? 'text-white' : 'text-[#8b8b90] hover:text-[#c9c9cd]'}`}
                >
                  {active && (
                    <motion.span
                      layoutId="requirements-tab"
                      className="absolute inset-0 rounded-full bg-[#303036]"
                      transition={{ type: 'spring', stiffness: 520, damping: 42 }}
                    />
                  )}
                  {/* z-10: kayan hap komşu sekmenin üstünden geçerken yazıyı örtmesin */}
                  <span className="relative z-10">{p.label}</span>
                </button>
              )
            })}
          </div>
        </div>

        <div className="mt-[22px] overflow-hidden rounded-[14px] border border-white/[0.06] bg-[#0c0c11]">
          <div className={`${ROW} h-12`}>
            <div />
            <div className="flex items-center justify-between gap-2 px-4">
              <span className="text-[13px] font-semibold text-[#e6e6e7]">{t('home.requirements.minimum')}</span>
              <FpsBadge fps={REQUIREMENT_FPS[0]} />
            </div>
            <div className={`flex items-center justify-between gap-2 px-4 ${REC_BG}`}>
              <span className="flex items-center gap-2 text-[13px] font-semibold text-[#e6e6e7]">
                <span className="size-1.5 rounded-full bg-[#ffa37c]" />
                {t('home.requirements.recommended')}
              </span>
              <FpsBadge fps={REQUIREMENT_FPS[1]} />
            </div>
          </div>

          {/* Sekme değişince değerler kısa bir geçişle yenilenir */}
          <motion.div key={tab} initial={{ opacity: 0.35 }} animate={{ opacity: 1 }} transition={{ duration: 0.2 }}>
            {REQUIREMENT_ROWS.slice(0, COLLAPSED_ROW_COUNT).map(renderRow)}
            <motion.div
              initial={false}
              animate={expanded ? { height: 'auto', opacity: 1 } : { height: 0, opacity: 0 }}
              transition={{ duration: 0.35, ease: [0.4, 0, 0.2, 1] }}
              className="overflow-hidden"
              inert={!expanded}
            >
              {REQUIREMENT_ROWS.slice(COLLAPSED_ROW_COUNT).map(renderRow)}
            </motion.div>
          </motion.div>
        </div>

        <div className="mt-[17px] flex justify-center">
          <button
            type="button"
            aria-expanded={expanded}
            onClick={() => setExpanded((v) => !v)}
            className="inline-flex h-[37px] cursor-pointer items-center gap-1.5 rounded-[10px] border border-[#333337] bg-[#1c1c21] px-3.5 text-[13px] font-medium text-[#c8c8ca] transition-colors hover:bg-[#232328] hover:text-white"
          >
            {expanded ? t('home.requirements.showLess') : t('home.requirements.showMore')}
            <motion.span className="text-[#8a8a8e]" animate={{ rotate: expanded ? 180 : 0 }} transition={{ duration: 0.25 }}>
              <ChevronThinIcon size={14} />
            </motion.span>
          </button>
        </div>
      </motion.div>
    </section>
  )
}

function FpsBadge({ fps }: { fps: number }): React.JSX.Element {
  return (
    <span className="inline-flex h-[22px] shrink-0 items-center gap-1 rounded-full border border-[#2e2f33] bg-[#17181c] px-2 text-[11.5px] font-semibold text-[#c9c9cb]">
      <GaugeIcon size={12} className="text-[#8a8a8e]" />
      {fps} FPS
    </span>
  )
}

function ValueCell({ lines, className = '' }: { lines: string[]; className?: string }): React.JSX.Element {
  return (
    <div className={`px-4 pt-[16.5px] pb-[14.5px] text-[14px] leading-[23px] font-semibold text-[#ececed] ${className}`}>
      {lines.map((line) => (
        <div key={line}>{line}</div>
      ))}
    </div>
  )
}
