import type { GameVersion, LoaderType, VersionChannel } from '@shared/types'
import { delay } from '../mock/mock-utils'
import { requiredJavaMajor } from '@shared/utils'
import { versionKey } from '../minecraft/version-key'
import type { JavaRequirement, VersionService } from '../types'

/**
 * Faz 1 mock verisi. Faz 2'de Mojang version manifest ve loader meta API'lerinden
 * gerçek liste alınacak. 26.x numaralandırması ile eski 1.x numaralandırması bir arada
 * bulunduğu için sıralama her zaman releaseTime'a göre yapılır.
 */
const VANILLA: Array<[id: string, channel: VersionChannel, releaseTime: string]> = [
  ['26.3-snapshot-2', 'snapshot', '2026-10-14T10:00:00Z'],
  ['26.3-snapshot-1', 'snapshot', '2026-10-07T10:00:00Z'],
  ['26.2', 'release', '2026-09-16T10:00:00Z'],
  ['26.2-pre-1', 'snapshot', '2026-09-02T10:00:00Z'],
  ['26.1.2', 'release', '2026-06-10T10:00:00Z'],
  ['26.1.1', 'release', '2026-04-22T10:00:00Z'],
  ['26.1', 'release', '2026-03-24T10:00:00Z'],
  ['26.1-snapshot-6', 'snapshot', '2026-02-24T10:00:00Z'],
  ['1.21.11', 'release', '2025-12-09T10:00:00Z'],
  ['1.21.10', 'release', '2025-10-07T10:00:00Z'],
  ['1.21.9', 'release', '2025-09-30T10:00:00Z'],
  ['1.21.8', 'release', '2025-07-17T10:00:00Z'],
  ['1.21.5', 'release', '2025-03-25T10:00:00Z'],
  ['1.21.4', 'release', '2024-12-03T10:00:00Z'],
  ['1.21.1', 'release', '2024-08-08T10:00:00Z'],
  ['1.20.6', 'release', '2024-04-29T10:00:00Z'],
  ['1.20.4', 'release', '2023-12-07T10:00:00Z'],
  ['1.20.1', 'release', '2023-06-12T10:00:00Z'],
  ['1.19.4', 'release', '2023-03-14T10:00:00Z'],
  ['1.18.2', 'release', '2022-02-28T10:00:00Z'],
  ['1.16.5', 'release', '2021-01-15T10:00:00Z'],
  ['1.12.2', 'release', '2017-09-18T10:00:00Z'],
  ['1.8.9', 'release', '2015-12-09T10:00:00Z']
]

/** Forge/NeoForge yalnızca belirli kararlı sürümleri destekler (mock eşleme). */
const FORGE: Record<string, string> = {
  '26.2': '64.0.3',
  '26.1.2': '62.0.21',
  '1.21.10': '60.1.0',
  '1.21.8': '58.1.0',
  '1.21.1': '52.1.0',
  '1.20.1': '47.4.0',
  '1.19.4': '45.4.0',
  '1.18.2': '40.3.0',
  '1.16.5': '36.2.42',
  '1.12.2': '14.23.5.2860',
  '1.8.9': '11.15.1.2318'
}

const NEOFORGE: Record<string, string> = {
  '26.2': '26.2.0.14-beta',
  '26.1.2': '26.1.2.31',
  '1.21.10': '21.10.42',
  '1.21.8': '21.8.47',
  '1.21.4': '21.4.150',
  '1.21.1': '21.1.200',
  '1.20.6': '20.6.130',
  '1.20.4': '20.4.248'
}

const FABRIC_LOADER = '0.17.2'
const QUILT_LOADER = '0.29.1'

/** Fabric/Quilt 1.14 ve sonrasını (snapshot'lar dahil) destekler. */
function supportsModernLoader(mcVersion: string): boolean {
  const [major = 0, minor = 0] = mcVersion.split(/[.-]/).map((p) => parseInt(p, 10) || 0)
  return major >= 26 || (major === 1 && minor >= 14)
}

export { versionKey }

function build(loader: LoaderType): GameVersion[] {
  const out: GameVersion[] = []
  for (const [mc, channel, releaseTime] of VANILLA) {
    let loaderVersion: string | undefined
    if (loader === 'fabric' && supportsModernLoader(mc)) loaderVersion = FABRIC_LOADER
    else if (loader === 'quilt' && supportsModernLoader(mc)) loaderVersion = QUILT_LOADER
    else if (loader === 'forge') loaderVersion = FORGE[mc]
    else if (loader === 'neoforge') loaderVersion = NEOFORGE[mc]
    else if (loader !== 'vanilla') continue

    if (loader !== 'vanilla' && !loaderVersion) continue
    out.push({
      key: versionKey(loader, mc, loaderVersion),
      loader,
      minecraftVersion: mc,
      loaderVersion,
      channel,
      releaseTime
    })
  }
  return out.sort((a, b) => Date.parse(b.releaseTime) - Date.parse(a.releaseTime))
}

export class MockVersionService implements VersionService {
  private readonly cache = new Map<LoaderType, GameVersion[]>()

  async list(loader: LoaderType): Promise<GameVersion[]> {
    await delay(200)
    let list = this.cache.get(loader)
    if (!list) {
      list = build(loader)
      this.cache.set(loader, list)
    }
    return list
  }

  async find(key: string): Promise<GameVersion | undefined> {
    const loader = key.split(':')[0] as LoaderType
    const list = await this.list(loader)
    return list.find((v) => v.key === key)
  }

  async getJavaRequirement(minecraftVersion: string): Promise<JavaRequirement> {
    return { component: 'mock', majorVersion: requiredJavaMajor(minecraftVersion) }
  }
}
