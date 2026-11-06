import { useEffect, useState } from 'react'
import { useConfigStore } from '../../store/config-store'
import { PixelAvatar } from './PixelAvatar'

const DEFAULT_TEMPLATE = 'https://mc-heads.net/avatar/{username}/{size}'

/** Görsel yüklenemeyen adlar (oturum boyunca tekrar denenmez). */
const failed = new Set<string>()

/**
 * Oyuncunun skin'inden kafa görseli (şapka katmanıyla). Adres config'teki `avatarUrl` şablonundan
 * gelir; görsel yüklenemezse (internet yok, servis kapalı) addan türetilen piksel kafaya düşer.
 */
export function PlayerHead({ name, size = 32 }: { name: string; size?: number }): React.JSX.Element {
  const template = useConfigStore((s) => s.snapshot?.config.avatarUrl) ?? DEFAULT_TEMPLATE
  // Retina ekranlarda net görünsün diye en az 64 piksel istenir.
  const px = Math.max(64, Math.ceil(size * 2))
  const src = template.replaceAll('{username}', encodeURIComponent(name)).replaceAll('{size}', String(px))
  const [error, setError] = useState(() => failed.has(src))

  useEffect(() => setError(failed.has(src)), [src])

  if (!name || error) return <PixelAvatar name={name} size={size} />

  return (
    <img
      src={src}
      width={size}
      height={size}
      alt=""
      draggable={false}
      referrerPolicy="no-referrer"
      className="shrink-0 rounded-[3px] [image-rendering:pixelated]"
      onError={() => {
        failed.add(src)
        setError(true)
      }}
    />
  )
}
