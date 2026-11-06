import homeBg from '../../assets/home-bg.webp'
import homeBgVideo from '../../assets/home-bg.mp4'

/**
 * Ana sayfa / giriş ekranının arka planı: sessiz, sonsuz döngülü video. Video yüklenene kadar
 * (ve oynatılamazsa) aynı sahnenin durağan görseli (poster) görünür.
 */
export function BackdropVideo({ className = '' }: { className?: string }): React.JSX.Element {
  return (
    <video
      src={homeBgVideo}
      poster={homeBg}
      autoPlay
      loop
      muted
      playsInline
      disablePictureInPicture
      preload="auto"
      draggable={false}
      className={`pointer-events-none absolute inset-0 size-full object-cover ${className}`}
    />
  )
}
