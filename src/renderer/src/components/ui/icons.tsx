/**
 * Uygulamaya özel, satır içi SVG ikon seti (harici ikon kütüphanesi/görsel yok).
 * Hepsi currentColor kullanır; boyut `size` ile verilir.
 */

import type { SVGProps } from 'react'

type IconProps = SVGProps<SVGSVGElement> & { size?: number }

function Icon({ size = 20, children, ...rest }: IconProps): React.JSX.Element {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
      {...rest}
    >
      {children}
    </svg>
  )
}

export const PlayIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M8 5.5v13l10.5-6.5L8 5.5Z" fill="currentColor" stroke="none" />
  </Icon>
)

export const VersionsIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="m12 3 8.5 4.5L12 12 3.5 7.5 12 3Z" />
    <path d="m3.5 12 8.5 4.5 8.5-4.5" />
    <path d="m3.5 16.5 8.5 4.5 8.5-4.5" />
  </Icon>
)

export const SettingsIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M4 6h9M17 6h3M4 12h3M11 12h9M4 18h11M19 18h1" />
    <circle cx="15" cy="6" r="2" />
    <circle cx="9" cy="12" r="2" />
    <circle cx="17" cy="18" r="2" />
  </Icon>
)

export const NewsIcon = (p: IconProps) => (
  <Icon {...p}>
    <rect x="4" y="4" width="16" height="16" rx="2.5" />
    <path d="M8 9h8M8 12.5h8M8 16h5" />
  </Icon>
)

export const MinimizeIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M6 12h12" />
  </Icon>
)

export const CloseIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M6.5 6.5l11 11M17.5 6.5l-11 11" />
  </Icon>
)

export const RefreshIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M19.5 12a7.5 7.5 0 1 1-2.2-5.3" />
    <path d="M19.5 4.5v4h-4" />
  </Icon>
)

export const PauseIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M9 6v12M15 6v12" strokeWidth={2.4} />
  </Icon>
)

export const DownloadIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M12 4v11M7.5 10.5 12 15l4.5-4.5" />
    <path d="M5 19.5h14" />
  </Icon>
)

export const CheckIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="m5.5 12.5 4 4 9-9" />
  </Icon>
)

export const AlertIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M12 7.5v6" />
    <circle cx="12" cy="17" r="0.6" fill="currentColor" />
  </Icon>
)

export const InfoIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M12 11v6" />
    <circle cx="12" cy="7.5" r="0.6" fill="currentColor" />
  </Icon>
)

export const LogoutIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M14 4.5h4a1.5 1.5 0 0 1 1.5 1.5v12a1.5 1.5 0 0 1-1.5 1.5h-4" />
    <path d="M10 8l-4 4 4 4M6 12h9" />
  </Icon>
)

export const UsersIcon = (p: IconProps) => (
  <Icon {...p}>
    <circle cx="9" cy="8.5" r="3" />
    <path d="M3.5 19a5.5 5.5 0 0 1 11 0" />
    <path d="M16 6a3 3 0 0 1 0 5.5M17.5 14.5A5.5 5.5 0 0 1 20.5 19" />
  </Icon>
)

export const SignalIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M5 19v-3M10 19v-6M15 19v-9M20 19V6" strokeWidth={2.2} />
  </Icon>
)

export const KeyIcon = (p: IconProps) => (
  <Icon {...p}>
    <circle cx="8" cy="15" r="3.5" />
    <path d="m10.5 12.5 8-8M15.5 7.5l2 2M13.5 9.5l1.5 1.5" />
  </Icon>
)

export const FolderIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M3.5 7.5A1.5 1.5 0 0 1 5 6h4l2 2h8a1.5 1.5 0 0 1 1.5 1.5v8A1.5 1.5 0 0 1 19 19H5a1.5 1.5 0 0 1-1.5-1.5v-10Z" />
  </Icon>
)

export const SearchIcon = (p: IconProps) => (
  <Icon {...p}>
    <circle cx="11" cy="11" r="6" />
    <path d="m20 20-4.5-4.5" />
  </Icon>
)

export const ServerIcon = (p: IconProps) => (
  <Icon {...p}>
    <rect x="4" y="4.5" width="16" height="6" rx="1.5" />
    <rect x="4" y="13.5" width="16" height="6" rx="1.5" />
    <path d="M8 7.5h.01M8 16.5h.01" strokeWidth={2.6} />
  </Icon>
)

export const CpuIcon = (p: IconProps) => (
  <Icon {...p}>
    <rect x="6.5" y="6.5" width="11" height="11" rx="1.5" />
    <path d="M9.5 3v3.5M14.5 3v3.5M9.5 17.5V21M14.5 17.5V21M3 9.5h3.5M3 14.5h3.5M17.5 9.5H21M17.5 14.5H21" />
  </Icon>
)

export const CoffeeIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M5 9h11v5a5 5 0 0 1-5 5h-1a5 5 0 0 1-5-5V9Z" />
    <path d="M16 10.5h1.5a2.5 2.5 0 0 1 0 5H16M8.5 3.5v2.5M12 3.5v2.5" />
  </Icon>
)

// ── Yeni tasarımın menü ikonları ─────────────────────────────────────────────

export const MenuIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M4 7h16M4 12h16M4 17h16" strokeWidth={2.2} />
  </Icon>
)

export const CartIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M3.5 4.5h2.2l2.1 10.2a1.5 1.5 0 0 0 1.5 1.2h7.9a1.5 1.5 0 0 0 1.4-1.1l1.6-6.3H6.4" />
    <circle cx="9.5" cy="19.5" r="1.3" fill="currentColor" stroke="none" />
    <circle cx="17" cy="19.5" r="1.3" fill="currentColor" stroke="none" />
  </Icon>
)

export const CubeIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M12 3 20 7.5v9L12 21l-8-4.5v-9L12 3Z" />
    <path d="M4 7.5 12 12l8-4.5M12 12v9" />
  </Icon>
)

export const StarIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="m12 4 2.4 5 5.4.6-4 3.7 1.1 5.3L12 16l-4.9 2.6 1.1-5.3-4-3.7 5.4-.6L12 4Z" fill="currentColor" stroke="none" />
  </Icon>
)

export const ChatIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M5 5.5h14a1.5 1.5 0 0 1 1.5 1.5v8a1.5 1.5 0 0 1-1.5 1.5h-8l-4.5 3.5V16.5H5A1.5 1.5 0 0 1 3.5 15V7A1.5 1.5 0 0 1 5 5.5Z" fill="currentColor" stroke="none" />
  </Icon>
)

export const UserIcon = (p: IconProps) => (
  <Icon {...p}>
    <circle cx="12" cy="7.5" r="3.5" fill="currentColor" stroke="none" />
    <path d="M5 20.5c.6-4 3.4-6.5 7-6.5s6.4 2.5 7 6.5H5Z" fill="currentColor" stroke="none" />
  </Icon>
)

export const GearIcon = (p: IconProps) => (
  <Icon {...p}>
    <circle cx="12" cy="12" r="3" />
    <path d="M12 3.5v2.2M12 18.3v2.2M20.5 12h-2.2M5.7 12H3.5M18 6l-1.6 1.6M7.6 16.4 6 18M18 18l-1.6-1.6M7.6 7.6 6 6" />
  </Icon>
)

export const ArrowLeftIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M19 12H5M11 6l-6 6 6 6" />
  </Icon>
)

export const ChevronDownIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="m7 10 5 5 5-5" fill="currentColor" stroke="none" />
  </Icon>
)

export const BagIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M5.5 8.5h13l-1 11h-11l-1-11Z" />
    <path d="M9 8.5V7a3 3 0 0 1 6 0v1.5" />
  </Icon>
)

export const ExternalIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M14 4.5h5.5V10M19.5 4.5 11 13M17 14v4.5a1 1 0 0 1-1 1H5.5a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1H10" />
  </Icon>
)

// ── Sistem gereksinimleri kartı (lucide çizimleri) ───────────────────────────

export const MonitorIcon = (p: IconProps) => (
  <Icon {...p}>
    <rect width="20" height="14" x="2" y="3" rx="2" />
    <path d="M8 21h8M12 17v4" />
  </Icon>
)

export const BinaryIcon = (p: IconProps) => (
  <Icon {...p}>
    <rect x="14" y="14" width="4" height="6" rx="2" />
    <rect x="6" y="4" width="4" height="6" rx="2" />
    <path d="M6 20h4M14 10h4M6 14h2v6M14 4h2v6" />
  </Icon>
)

export const ProcessorIcon = (p: IconProps) => (
  <Icon {...p}>
    <rect width="16" height="16" x="4" y="4" rx="2" />
    <rect width="6" height="6" x="9" y="9" rx="1" />
    <path d="M15 2v2M15 20v2M2 15h2M2 9h2M20 15h2M20 9h2M9 2v2M9 20v2" />
  </Icon>
)

export const LayersIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83Z" />
    <path d="m22 17.65-9.17 4.16a2 2 0 0 1-1.66 0L2 17.65" />
    <path d="m22 12.65-9.17 4.16a2 2 0 0 1-1.66 0L2 12.65" />
  </Icon>
)

export const GpuIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M2 21V3" />
    <path d="M2 5h18a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H2.26" />
    <path d="M7 17v3a1 1 0 0 0 1 1h5a1 1 0 0 0 1-1v-3" />
    <circle cx="16" cy="11" r="2" />
    <circle cx="8" cy="11" r="2" />
  </Icon>
)

export const ShapesIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M8.3 10a.7.7 0 0 1-.626-1.079L11.4 3a.7.7 0 0 1 1.198-.043L16.3 8.9a.7.7 0 0 1-.572 1.1Z" />
    <rect x="3" y="14" width="7" height="7" rx="1" />
    <circle cx="17.5" cy="17.5" r="3.5" />
  </Icon>
)

export const CircuitBoardIcon = (p: IconProps) => (
  <Icon {...p}>
    <rect width="18" height="18" x="3" y="3" rx="2" />
    <path d="M11 9h4a2 2 0 0 0 2-2V3" />
    <circle cx="9" cy="9" r="2" />
    <path d="M7 21v-4a2 2 0 0 1 2-2h4" />
    <circle cx="15" cy="15" r="2" />
  </Icon>
)

export const HardDriveIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M22 12H2" />
    <path d="M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11Z" />
    <path d="M6 16h.01M10 16h.01" />
  </Icon>
)

export const MemoryStickIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M6 19v-3M10 19v-3M14 19v-3M18 19v-3M8 11V9M16 11V9M12 11V9M2 15h20" />
    <path d="M2 7a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v1.1a2 2 0 0 0 0 3.837V17a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-5.1a2 2 0 0 0 0-3.837Z" />
  </Icon>
)

export const SlidersIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M21 4h-7M10 4H3M21 12h-9M8 12H3M21 20h-5M12 20H3M14 2v4M8 10v4M16 18v4" />
  </Icon>
)

export const RatioIcon = (p: IconProps) => (
  <Icon {...p}>
    <rect width="12" height="20" x="6" y="2" rx="2" />
    <rect width="20" height="12" x="2" y="6" rx="2" />
  </Icon>
)

export const GaugeIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="m12 14 4-4" />
    <path d="M3.34 19a10 10 0 1 1 17.32 0" />
  </Icon>
)

/** İnce çizgili aşağı ok (ChevronDownIcon dolu üçgendir). */
export const ChevronThinIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="m6 9 6 6 6-6" />
  </Icon>
)
