/**
 * Ana sayfadaki "Sistem gereksinimleri" kartının verisi. Her hücre bir veya birkaç satırdır;
 * satır düz metinse olduğu gibi yazılır, `{ t }` ise `home.requirements.values.*` çevirisidir.
 */

export type RequirementPlatform = 'windows' | 'mac' | 'linux'

export type RequirementRowKey =
  | 'os'
  | 'arch'
  | 'cpu'
  | 'cpuFeatures'
  | 'gpu'
  | 'gpuFeatures'
  | 'vram'
  | 'storage'
  | 'ram'
  | 'graphics'
  | 'resolution'

export type RequirementLine = string | { t: 'low' | 'high' | 'unifiedMemory' }

/** [minimum, önerilen] */
type Spec = Record<RequirementRowKey, [RequirementLine[], RequirementLine[]]>

export const REQUIREMENT_PLATFORMS: { id: RequirementPlatform; label: string }[] = [
  { id: 'windows', label: 'Windows' },
  { id: 'mac', label: 'Mac' },
  { id: 'linux', label: 'Linux' }
]

export const REQUIREMENT_ROWS: RequirementRowKey[] = [
  'os',
  'arch',
  'cpu',
  'cpuFeatures',
  'gpu',
  'gpuFeatures',
  'vram',
  'storage',
  'ram',
  'graphics',
  'resolution'
]

/** Kart kapalıyken görünen satır sayısı; gerisi "Daha fazla göster" ile açılır. */
export const COLLAPSED_ROW_COUNT = 5

/** Hedef FPS rozetleri: [minimum, önerilen] */
export const REQUIREMENT_FPS: [number, number] = [30, 60]

const LOW = { t: 'low' } as const
const HIGH = { t: 'high' } as const
const UNIFIED = { t: 'unifiedMemory' } as const

const PC_CPU: Spec['cpu'] = [
  ['Intel Core i5-4460', 'AMD Ryzen 3 1200'],
  ['Intel Core i5-10400', 'AMD Ryzen 5 3600']
]
const PC_GPU: Spec['gpu'] = [
  ['NVIDIA GeForce GTX 750 Ti', 'AMD Radeon RX 460', 'Intel UHD Graphics 630'],
  ['NVIDIA GeForce GTX 1660 Super', 'AMD Radeon RX 5600 XT']
]

export const REQUIREMENTS: Record<RequirementPlatform, Spec> = {
  windows: {
    os: [['Windows 10'], ['Windows 11']],
    arch: [['x64'], ['x64']],
    cpu: PC_CPU,
    cpuFeatures: [['SSE4.2'], ['AVX2']],
    gpu: PC_GPU,
    gpuFeatures: [['OpenGL 4.4'], ['OpenGL 4.6']],
    vram: [['2 GB'], ['6 GB']],
    storage: [['8 GB HDD'], ['12 GB SSD']],
    ram: [['8 GB'], ['16 GB']],
    graphics: [[LOW], [HIGH]],
    resolution: [['1280×720'], ['1920×1080']]
  },
  mac: {
    os: [['macOS 12 Monterey'], ['macOS 14 Sonoma']],
    arch: [['Apple Silicon / Intel'], ['Apple Silicon']],
    cpu: [
      ['Apple M1', 'Intel Core i5-8259U'],
      ['Apple M2 Pro', 'Apple M3']
    ],
    cpuFeatures: [['ARM64 / x64'], ['ARM64']],
    gpu: [
      ['Apple M1', 'Intel Iris Plus Graphics 655'],
      ['Apple M2 Pro', 'Apple M3']
    ],
    gpuFeatures: [['OpenGL 4.1'], ['OpenGL 4.1']],
    vram: [[UNIFIED], [UNIFIED]],
    storage: [['8 GB SSD'], ['12 GB SSD']],
    ram: [['8 GB'], ['16 GB']],
    graphics: [[LOW], [HIGH]],
    resolution: [['1280×800'], ['2560×1600']]
  },
  linux: {
    os: [['Ubuntu 22.04 / glibc 2.35'], ['Ubuntu 24.04']],
    arch: [['x64'], ['x64']],
    cpu: PC_CPU,
    cpuFeatures: [['SSE4.2'], ['AVX2']],
    gpu: PC_GPU,
    gpuFeatures: [['OpenGL 4.4'], ['OpenGL 4.6']],
    vram: [['2 GB'], ['6 GB']],
    storage: [['8 GB HDD'], ['12 GB SSD']],
    ram: [['8 GB'], ['16 GB']],
    graphics: [[LOW], [HIGH]],
    resolution: [['1280×720'], ['1920×1080']]
  }
}

/** Launcher'ın çalıştığı işletim sistemi varsayılan sekme olur. */
export function platformTab(platform: string | undefined): RequirementPlatform {
  if (platform === 'darwin') return 'mac'
  if (platform === 'linux') return 'linux'
  return 'windows'
}
