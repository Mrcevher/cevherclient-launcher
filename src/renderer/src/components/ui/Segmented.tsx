import { motion } from 'framer-motion'
import { useId } from 'react'

interface SegmentedProps<T extends string> {
  value: T
  options: Array<{ value: T; label: string }>
  onChange(value: T): void
  size?: 'sm' | 'md'
  /** Etiketler özel ad ise (Fabric, Quilt…) "en": büyük harfe çevirmede Türkçe İ kuralı uygulanmasın. */
  lang?: string
}

/** Sekme/segment kontrolü; seçili göstergesi seçenekler arasında kayar. */
export function Segmented<T extends string>({ value, options, onChange, size = 'md', lang }: SegmentedProps<T>): React.JSX.Element {
  const layoutId = useId()
  return (
    <div role="tablist" lang={lang} className="inline-flex gap-1 rounded-xl border border-border bg-panel-2 p-1">
      {options.map((opt) => {
        const active = opt.value === value
        return (
          <button
            key={opt.value}
            role="tab"
            type="button"
            aria-selected={active}
            onClick={() => onChange(opt.value)}
            className={`relative cursor-pointer rounded-lg font-bold transition-colors ${
              size === 'sm' ? 'px-3 py-1 text-xs' : 'px-4 py-1.5 text-sm'
            } ${active ? 'text-white' : 'text-muted hover:text-text'}`}
          >
            {active && (
              <motion.span
                layoutId={layoutId}
                className="absolute inset-0 rounded-lg bg-primary"
                transition={{ type: 'spring', stiffness: 500, damping: 38 }}
              />
            )}
            <span className="relative">{opt.label}</span>
          </button>
        )
      })}
    </div>
  )
}
