/**
 * Oyuncu adından türetilen 8×8 piksel Minecraft tarzı kafa. Lisanssız hesapların gerçek
 * skin'i olmadığı için ad aynı kaldıkça her yerde aynı yüz çizilir; harici görsel yok.
 */

function hash(name: string): number {
  let h = 2166136261
  for (const ch of name.toLowerCase()) h = Math.imul(h ^ ch.charCodeAt(0), 16777619)
  return h >>> 0
}

const SKIN = ['#f1c7a1', '#e2b08a', '#c98d64', '#a8714b', '#7d5236', '#f6d3b8']
const HAIR = ['#2b1d14', '#5a3a22', '#8a5a2b', '#c9a15a', '#e8d27a', '#7a2f2a', '#c45a9a', '#e4e4e4', '#2a3550', '#3c6e3a']
const EYES = ['#3b6fd8', '#2f8f5b', '#6a4a2a', '#7c4bc4', '#2aa7a7']

export function PixelAvatar({ name, size = 32 }: { name: string; size?: number }): React.JSX.Element {
  const h = hash(name || '?')
  const skin = SKIN[h % SKIN.length]!
  const hair = HAIR[(h >>> 4) % HAIR.length]!
  const eye = EYES[(h >>> 9) % EYES.length]!
  const fringe = (h >>> 13) % 3 // saç kesimi: düz / yandan / uzun

  // Satır satır piksel haritası: H saç, S ten, W göz akı, E göz, M ağız, D gölge
  const rows = [
    'HHHHHHHH',
    'HHHHHHHH',
    fringe === 0 ? 'HSSSSSSH' : fringe === 1 ? 'HHHSSSSH' : 'HHSSSSHH',
    'SSSSSSSS',
    'SWESSEWS',
    'SSSDDSSS',
    'SSMMMMSS',
    fringe === 2 ? 'HSSSSSSH' : 'SSSSSSSS'
  ]
  const color: Record<string, string> = { H: hair, S: skin, W: '#ffffff', E: eye, M: '#7a3b2e', D: 'rgba(0,0,0,0.12)' }

  return (
    <svg width={size} height={size} viewBox="0 0 8 8" shapeRendering="crispEdges" aria-hidden className="shrink-0 rounded-[3px]">
      <rect width="8" height="8" fill={skin} />
      {rows.flatMap((row, y) =>
        [...row].map((c, x) => (c === 'S' ? null : <rect key={`${x}-${y}`} x={x} y={y} width="1" height="1" fill={color[c]} />))
      )}
    </svg>
  )
}
