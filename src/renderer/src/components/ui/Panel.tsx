import type { ReactNode } from 'react'

/** Sayfalardaki koyu kart: başlık + içerik. */
export function Panel({
  title,
  aside,
  children,
  className = ''
}: {
  title?: string
  aside?: ReactNode
  children: ReactNode
  className?: string
}): React.JSX.Element {
  return (
    <section className={`panel p-5 ${className}`}>
      {(title || aside) && (
        <header className="mb-3 flex items-center justify-between gap-3">
          {title && <h3 className="text-[15px] font-bold text-text">{title}</h3>}
          {aside}
        </header>
      )}
      {children}
    </section>
  )
}
