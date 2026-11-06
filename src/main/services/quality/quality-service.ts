import { totalmem } from 'node:os'
import { existsSync } from 'node:fs'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { AppError } from '@shared/errors'
import { LEGACY_JVM_ARGS, OPTIMIZED_JVM_ARGS } from '@shared/types'
import type { DeviceTier, QualityResult, ServerProfile } from '@shared/types'
import { instanceDir } from '../minecraft/paths'
import { downloadModFiles, getProjects, latestShaderVersion, primaryFile, removeRecordedFile } from '../mods/modrinth'
import type { PerformancePackService, ProgressCallback, QualityService, SettingsService } from '../types'

/**
 * Kalite seviyeleri arasındaki TEK fark shader'dır; performans paketi, modlar, JVM ve oyun ayarları
 * üç seviyede de aynıdır.
 */
const SHADERS: Record<DeviceTier, string> = {
  low: 'super-duper-vanilla',
  medium: 'photon-shader',
  high: 'complementary-unbound'
}

interface QualityFile {
  tier: DeviceTier | null
  /** Bu servisin kurduğu shader dosyası (shaderpacks/ altında); seviye değişince silinir. */
  shaderFile: string | null
  /** Oyun ayarları (options.txt) ilk seçimde bir kez optimize edilir; sonra oyuncunun elinde kalır. */
  optionsTuned: boolean
}

/**
 * options.txt anahtarları. Hepsi performansı artıran, görüntüyü gözle fark edilir bozmayan değerler:
 * VSync kapalı + yüksek FPS sınırı, makul çizim/simülasyon mesafesi, az parçacık, gölge/çapraz karışım
 * maliyetleri düşük. Shader açıkken bulut ve yaprak kalitesini shader zaten yönetir.
 */
const OPTIONS: Record<string, string> = {
  renderDistance: '10',
  simulationDistance: '8',
  entityDistanceScaling: '0.75',
  particles: '1',
  entityShadows: 'false',
  biomeBlendRadius: '1',
  mipmapLevels: '2',
  enableVsync: 'false',
  maxFps: '260',
  useNativeTransport: 'true'
}

/** Sodium: oyuncu elle ayarlamadıysa (dosya yoksa) performans odaklı varsayılanlar. */
const SODIUM_OPTIONS = {
  performance: {
    chunk_builder_threads: 0,
    always_defer_chunk_updates_v2: true,
    animate_only_visible_textures: true,
    use_entity_culling: true,
    use_fog_occlusion: true,
    use_block_face_culling: true,
    use_no_error_g_l_context: true
  }
}

/** Iris: seçilen shader otomatik açılsın, gölge mesafesi makul tutulsun. */
const IRIS_OPTIONS = {
  enableShaders: 'true',
  maxShadowRenderDistance: '12',
  disableUpdateMessage: 'true'
}

/**
 * Cihaz kalitesini uygular: Performans Paketini (Iris ve oyun içi modlar dahil) kurar, seçilen
 * seviyenin shader'ını indirip Iris'e seçtirir, optimize ayarları yazar ve seçimi ayarlara kaydeder.
 * Kendi kaydı instances/<id>/.cevher/quality.json; yalnızca kendi koyduğu shader dosyasını siler.
 */
export class ModrinthQualityService implements QualityService {
  constructor(
    private readonly performance: PerformancePackService,
    private readonly settings: SettingsService
  ) {}

  async apply(server: ServerProfile, tier: DeviceTier, onProgress: ProgressCallback, remember = true): Promise<QualityResult> {
    if (!(tier in SHADERS)) throw new AppError('UNKNOWN', `Bilinmeyen kalite: ${tier}`)
    if (server.loader.type === 'vanilla') {
      throw new AppError('UNKNOWN', 'Kalite seçimi Fabric gerektirir', { reason: 'noLoader' })
    }
    const mc = server.minecraftVersion
    const dir = instanceDir(server.id)
    onProgress(0)

    // 1) Performans Paketi + Iris + oyun içi modlar (%0–65).
    await this.performance.install(server, (p) => onProgress(p * 0.65))

    // 2) Seviyenin shader'ı (%65–90). Yazar bu Minecraft sürümünü etiketlememişse en yeni sürüm alınır.
    const slug = SHADERS[tier]
    const version = (await latestShaderVersion(slug, mc)) ?? (await latestShaderVersion(slug, mc, true))
    const file = version && primaryFile(version)
    if (!version || !file) {
      throw new AppError('DOWNLOAD_FAILED', `Shader bulunamadı: ${slug}`, { reason: 'noVersion' })
    }
    const shaderDir = join(dir, 'shaderpacks')
    await mkdir(shaderDir, { recursive: true })
    await downloadModFiles([{ path: join(shaderDir, file.filename), file }], (p) => onProgress(0.65 + p * 0.25))

    // 3) Eski seviyenin shader'ını kaldır, ayarları yaz (%90–100).
    const state = await this.read(server)
    if (state.shaderFile && state.shaderFile !== file.filename) {
      await removeRecordedFile(shaderDir, state.shaderFile)
    }
    await this.writeIris(dir, file.filename)
    await this.writeSodium(dir)
    if (!state.optionsTuned) await this.tuneOptions(dir)
    await this.write(server, { tier, shaderFile: file.filename, optionsTuned: true })
    onProgress(0.95)

    await this.tuneLauncher(remember ? tier : null)
    onProgress(1)
    const title = (await getProjects([slug]))[0]?.title ?? slug
    return { tier, shaderTitle: title }
  }

  // ── ayar dosyaları ────────────────────────────────────────────────────────

  /** config/iris.properties: yalnızca ilgili anahtarlar güncellenir, gerisi korunur. */
  private async writeIris(dir: string, shaderFile: string): Promise<void> {
    await this.setLines(join(dir, 'config', 'iris.properties'), '=', { ...IRIS_OPTIONS, shaderPack: shaderFile })
  }

  private async writeSodium(dir: string): Promise<void> {
    const path = join(dir, 'config', 'sodium-options.json')
    if (existsSync(path)) return
    await mkdir(join(dir, 'config'), { recursive: true })
    await writeFile(path, JSON.stringify(SODIUM_OPTIONS, null, 2), 'utf8')
  }

  private tuneOptions(dir: string): Promise<void> {
    return this.setLines(join(dir, 'options.txt'), ':', OPTIONS)
  }

  /** "anahtar<ayraç>değer" satırlı dosyada verilen anahtarları ayarlar; yoksa ekler, diğerlerine dokunmaz. */
  private async setLines(path: string, sep: ':' | '=', values: Record<string, string>): Promise<void> {
    let text = ''
    try {
      text = await readFile(path, 'utf8')
    } catch {
      /* dosya henüz yok */
    }
    const eol = text.includes('\r\n') ? '\r\n' : '\n'
    const lines = text === '' ? [] : text.split(/\r?\n/)
    if (lines.at(-1) === '') lines.pop()
    const pending = new Map(Object.entries(values))
    const out = lines.map((line) => {
      const key = line.split(sep, 1)[0]!.trim()
      const value = pending.get(key)
      if (value === undefined) return line
      pending.delete(key)
      return `${key}${sep}${value}`
    })
    for (const [key, value] of pending) out.push(`${key}${sep}${value}`)
    await mkdir(join(path, '..'), { recursive: true })
    await writeFile(path, out.join(eol) + eol, 'utf8')
  }

  /**
   * Launcher ayarları: seçim kaydedilir; JVM bayrakları yalnızca oyuncu elle değiştirmediyse optimize
   * sete geçer; RAM de yalnızca varsayılandaysa makinenin belleğine göre yükseltilir. Fabric kapalıysa açılır.
   */
  private async tuneLauncher(tier: DeviceTier | null): Promise<void> {
    const current = await this.settings.get()
    const totalGb = totalmem() / 1024 ** 3
    const wanted = Math.min(8192, Math.max(3072, Math.floor((totalGb * 0.4 * 1024) / 512) * 512))
    await this.settings.update({
      deviceTier: tier,
      useFabric: true,
      jvmArgs: !current.jvmArgs.trim() || current.jvmArgs.trim() === LEGACY_JVM_ARGS ? OPTIMIZED_JVM_ARGS : current.jvmArgs,
      memoryMb: current.memoryMb === 4096 ? wanted : current.memoryMb
    })
  }

  // ── kayıt ─────────────────────────────────────────────────────────────────

  private stateFile(server: ServerProfile): string {
    return join(instanceDir(server.id), '.cevher', 'quality.json')
  }

  private async read(server: ServerProfile): Promise<QualityFile> {
    try {
      const raw = JSON.parse(await readFile(this.stateFile(server), 'utf8')) as Partial<QualityFile>
      return {
        tier: raw.tier === 'low' || raw.tier === 'medium' || raw.tier === 'high' ? raw.tier : null,
        shaderFile: typeof raw.shaderFile === 'string' ? raw.shaderFile : null,
        optionsTuned: raw.optionsTuned === true
      }
    } catch {
      return { tier: null, shaderFile: null, optionsTuned: false }
    }
  }

  private async write(server: ServerProfile, state: QualityFile): Promise<void> {
    await mkdir(join(instanceDir(server.id), '.cevher'), { recursive: true })
    await writeFile(this.stateFile(server), JSON.stringify(state, null, 2), 'utf8')
  }
}
