import type { ButtonHTMLAttributes, ReactNode } from 'react'

type Variant = 'primary' | 'outline' | 'ghost' | 'danger'
type Size = 'sm' | 'md' | 'lg'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
  size?: Size
  icon?: ReactNode
  loading?: boolean
}

const VARIANT: Record<Variant, string> = {
  primary:
    'bg-accent text-on-accent shadow-[0_0_0_1px_color-mix(in_oklab,var(--color-accent)_60%,white)_inset] hover:shadow-[0_0_22px_color-mix(in_oklab,var(--color-accent)_55%,transparent)]',
  outline: 'border border-border text-text hover:border-accent hover:text-accent bg-transparent',
  ghost: 'text-muted hover:text-text hover:bg-white/5',
  danger: 'border border-hp-low/50 text-hp-low hover:bg-hp-low/12'
}

const SIZE: Record<Size, string> = {
  sm: 'h-8 px-3 text-xs gap-1.5 rounded-lg',
  md: 'h-10 px-4 text-sm gap-2 rounded-xl',
  lg: 'h-12 px-6 text-base gap-2.5 rounded-xl'
}

/** Ortak buton. Hover'da hafif büyüme/glow, basılıyken küçülme. */
export function Button({
  variant = 'outline',
  size = 'md',
  icon,
  loading = false,
  disabled,
  className = '',
  children,
  ...rest
}: ButtonProps): React.JSX.Element {
  return (
    <button
      type="button"
      disabled={disabled || loading}
      className={`inline-flex cursor-pointer items-center justify-center font-semibold whitespace-nowrap transition-[transform,box-shadow,background-color,color,border-color] duration-150 active:scale-[0.97] disabled:cursor-not-allowed disabled:opacity-45 disabled:active:scale-100 ${VARIANT[variant]} ${SIZE[size]} ${className}`}
      {...rest}
    >
      {loading ? (
        <span className="size-4 animate-spin rounded-full border-2 border-current border-t-transparent" aria-hidden />
      ) : (
        icon
      )}
      {children}
    </button>
  )
}

/** Yalnızca ikon içeren yuvarlak buton (başlık çubuğu, liste eylemleri). */
export function IconButton({
  label,
  className = '',
  children,
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { label: string }): React.JSX.Element {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      className={`inline-grid size-8 cursor-pointer place-items-center rounded-full text-muted transition hover:bg-white/8 hover:text-text active:scale-95 disabled:cursor-not-allowed disabled:opacity-40 ${className}`}
      {...rest}
    >
      {children}
    </button>
  )
}
