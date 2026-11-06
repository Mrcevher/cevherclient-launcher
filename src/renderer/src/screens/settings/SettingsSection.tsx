import type { ReactNode } from 'react'

/** Ayarlar ekranındaki her bölümün kart çerçevesi. */
export function SettingsSection({
  title,
  aside,
  children,
  className = ''
}: {
  title: string
  aside?: ReactNode
  children: ReactNode
  className?: string
}): React.JSX.Element {
  return (
    <section className={`panel p-5 ${className}`}>
      <header className="mb-4 flex items-center justify-between gap-3">
        <h3 className="text-[15px] font-bold text-text">{title}</h3>
        {aside}
      </header>
      {children}
    </section>
  )
}

/** Basit aç/kapa anahtarı. */
export function Toggle({ checked, onChange, label }: { checked: boolean; onChange(v: boolean): void; label: string }): React.JSX.Element {
  return (
    <label className="flex cursor-pointer items-center gap-3 text-sm">
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        onClick={() => onChange(!checked)}
        className={`relative h-6 w-11 cursor-pointer rounded-full border transition-colors ${
          checked ? 'border-primary bg-primary' : 'border-border bg-black/30'
        }`}
      >
        <span
          className={`absolute top-0.5 left-0.5 size-[18px] rounded-full bg-white shadow transition-transform ${
            checked ? 'translate-x-5' : ''
          }`}
        />
      </button>
      {label}
    </label>
  )
}
