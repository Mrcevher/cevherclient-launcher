import { existsSync } from 'node:fs'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import type { PerformancePackState, ServerProfile } from '@shared/types'
import { instanceDir } from '../minecraft/paths'
import type { PerformancePackService, ProgressCallback } from '../types'
import {
  downloadModFiles,
  latestFabricVersion,
  primaryFile,
  removeRecordedFile,
  versionById,
  type ModrinthFile,
  type ModrinthVersion
} from './modrinth'

/**
 * Paketteki modlar. `id` sabit Modrinth proje kimliğidir: slug yeniden atansa bile başka bir
 * projenin dosyası kurulmaz; dönen sürümün `project_id`'si eşleşmezse mod "kullanılamaz" sayılır. Açıklamalar arayüzün çeviri dosyasında.
 * Performans modlarının yanında cihaz kalitesi seçiminin ihtiyaç duyduğu Iris (shader yükleyici) ve
 * oyun içi kolaylık modları da burada; hepsi aynı kurulum/güncelleme akışından geçer.
 */
export const PERFORMANCE_MODS: ReadonlyArray<{ slug: string; id: string; name: string }> = [
  { slug: 'fabric-api', id: 'P7dR8mSH', name: 'Fabric API' },
  { slug: 'sodium', id: 'AANobbMI', name: 'Sodium' },
  { slug: 'lithium', id: 'gvQqBUqZ', name: 'Lithium' },
  { slug: 'ferrite-core', id: 'uXXizFIs', name: 'FerriteCore' },
  { slug: 'immediatelyfast', id: '5ZwdcRci', name: 'ImmediatelyFast' },
  { slug: 'entityculling', id: 'NNAgCjsB', name: 'EntityCulling' },
  { slug: 'threadtweak', id: 'vSEH1ERy', name: 'ThreadTweak' },
  { slug: 'dynamic-fps', id: 'LQ3K71Q1', name: 'Dynamic FPS' },
  { slug: 'iris', id: 'YL57xq9U', name: 'Iris Shaders' },
  { slug: 'modmenu', id: 'mOgUt4GM', name: 'Mod Menu' },
  { slug: 'shulkerboxtooltip', id: '2M01OLQq', name: 'Shulker Box Tooltip' },
  { slug: 'lambdynamiclights', id: 'yBW8D80W', name: 'LambDynamicLights' },
  { slug: 'smoothplayeranimations', id: 'ZdlGpqTS', name: 'SmoothPlayerAnimations' },
  { slug: 'appleskin', id: 'EsAfCjCV', name: 'AppleSkin' },
  { slug: 'xaeros-world-map', id: 'NcUtCpym', name: "Xaero's World Map" },
  { slug: 'entitytexturefeatures', id: 'BVzZfTc1', name: 'Entity Texture Features' }
]

/**
 * Oyunu açılışta çökerten, bilinen bozuk mod sürümleri. Paket bunları hiç seçmez (bir önceki kararlı sürüme
 * düşer), diskte bulursa siler ve "kurulu" saymaz; böylece zaten indirmiş oyuncular da bir sonraki
 * açılışta/kalite seçiminde kendiliğinden onarılır. Yazar düzeltilmiş yeni sürüm yayımlayınca o seçilir.
 */
const BROKEN_VERSIONS: ReadonlyArray<{ slug: string; version: string; file: string; reason: string }> = [
  {
    slug: 'smoothplayeranimations',
    version: '2.0.1+1.21.11',
    file: 'SmoothPlayerAnimations_Fabric_1.21.11_2.0.1.jar',
    // LivingEntityRendererMixin: @At(target) Mojang adıyla, method intermediary adıyla yazılmış ve refmap yok →
    // "Scanned 0 target(s)" ile oyun açılışta (LAUNCH_FAILED) çöker.
    reason: 'mixin hedefi bulunamıyor, oyun açılışta çöküyor'
  }
]

const isBrokenVersion = (slug: string, versionNumber: string): boolean =>
  BROKEN_VERSIONS.some((b) => b.slug === slug && b.version === versionNumber)

interface PackFile {
  minecraftVersion: string
  mods: Record<string, { file: string; version: string }>
  /** Paket modlarının zorunlu bağımlılıkları (Modrinth proje kimliği → dosya). */
  deps: Record<string, { file: string; version: string }>
  unavailable: string[]
}

interface Resolved {
  slug: string
  version: ModrinthVersion | null
}

/**
 * Performans Paketi: Sodium, Lithium vb. istemci optimizasyon modlarını Modrinth'ten, sunucu
 * profilinin Minecraft sürümüne uygun Fabric sürümleriyle indirir (SHA1 doğrulamalı) ve
 * sunucunun oyun klasörüne (instances/<id>/mods) kurar.
 *
 * Kendi kaydını instances/<id>/.cevher/performance-pack.json'da tutar; yalnızca kendi
 * koyduğu dosyaları günceller/siler. Modpack senkronu ve oyuncunun kendi modları etkilenmez.
 */
export class ModrinthPerformancePackService implements PerformancePackService {
  async getState(server: ServerProfile): Promise<PerformancePackState> {
    const pack = await this.read(server)
    const sameVersion = pack.minecraftVersion === server.minecraftVersion
    const mods = PERFORMANCE_MODS.map(({ slug, name }) => {
      const entry = sameVersion ? pack.mods[slug] : undefined
      // Bozuk sürüm "kurulu" sayılmaz: kurulum yeniden çalışıp onu düzgün sürümle değiştirir.
      const present = entry && !isBrokenVersion(slug, entry.version) && existsSync(join(this.modsDir(server), entry.file))
      return { slug, name, installedVersion: present ? entry.version : null }
    })
    const unavailable = sameVersion ? pack.unavailable : []
    const expected = mods.filter((m) => !unavailable.includes(m.slug))
    // Paketten çıkarılmış bir mod (Krypton gibi) hâlâ kayıtlıysa paket "kurulu" sayılmaz: kurulum
    // yeniden çalışıp o dosyayı diskten siler.
    const hasRetired = Object.keys(pack.mods).some((slug) => !PERFORMANCE_MODS.some((m) => m.slug === slug))
    return {
      serverId: server.id,
      minecraftVersion: server.minecraftVersion,
      mods,
      installed: !hasRetired && expected.length > 0 && expected.every((m) => m.installedVersion !== null),
      unavailable,
      dependencies: sameVersion ? Object.keys(pack.deps) : []
    }
  }

  async install(server: ServerProfile, onProgress: ProgressCallback): Promise<PerformancePackState> {
    const mc = server.minecraftVersion
    const modsDir = this.modsDir(server)
    await mkdir(modsDir, { recursive: true })
    onProgress(0)

    // 1) Her mod için bu sürüme uygun en yeni (tercihen kararlı) Fabric dosyasını bul.
    const resolved: Resolved[] = await Promise.all(
      PERFORMANCE_MODS.map(async ({ slug, id }) => {
        const version = await latestFabricVersion(id, mc, (v) => isBrokenVersion(slug, v.version_number))
        return { slug, version: version?.project_id === id ? version : null }
      })
    )
    // Bir mod başka bir paket modunun belirli sürümüne bağlıysa (Iris → Sodium) o sürüm kullanılır;
    // en yeni beta ile uyumsuz kalmasın.
    for (const r of resolved) {
      for (const dep of r.version?.dependencies ?? []) {
        if (dep.dependency_type !== 'required' || !dep.project_id || !dep.version_id) continue
        const target = resolved.find((o) => o.version?.project_id === dep.project_id)
        if (!target?.version || target.version.id === dep.version_id) continue
        const pinned = await versionById(dep.version_id)
        if (pinned?.project_id === target.version.project_id && pinned.game_versions?.includes(mc) && !isBrokenVersion(target.slug, pinned.version_number)) {
          target.version = pinned
        }
      }
    }
    const unavailable = resolved.filter((r) => !r.version).map((r) => r.slug)
    const toInstall = resolved.flatMap((r) => {
      if (!r.version) return []
      const file = primaryFile(r.version)
      return file ? [{ slug: r.slug, versionNumber: r.version.version_number, file }] : []
    })
    onProgress(0.05)

    // 2) Paket dışında kalan zorunlu bağımlılıkları (Mod Menu → Text Placeholder API …) çöz.
    const known = new Set(resolved.flatMap((r) => (r.version ? [r.version.project_id] : [])))
    const depInstall: Array<{ projectId: string; versionNumber: string; file: ModrinthFile }> = []
    const queue = resolved.flatMap((r) => r.version?.dependencies ?? [])
    while (queue.length > 0) {
      const dep = queue.shift()!
      if (dep.dependency_type !== 'required' || !dep.project_id || known.has(dep.project_id)) continue
      known.add(dep.project_id)
      const version = dep.version_id ? await versionById(dep.version_id) : await latestFabricVersion(dep.project_id, mc)
      const file = version && primaryFile(version)
      if (!version || !file) {
        console.warn(`[performance-pack] ${dep.project_id} bağımlılığı ${mc} için bulunamadı, atlandı`)
        continue
      }
      depInstall.push({ projectId: version.project_id, versionNumber: version.version_number, file })
      queue.push(...version.dependencies)
    }
    onProgress(0.1)

    // 3) İndir (eksik/bozuk olanlar; SHA1 doğrulamalı, kesilirse kaldığı yerden).
    await downloadModFiles(
      [...toInstall, ...depInstall].map(({ file }) => ({ path: join(modsDir, file.filename), file })),
      (p) => onProgress(0.1 + 0.85 * p)
    )

    // 4) Güncellenen modların eski dosyalarını kaldır, kaydı yaz.
    // Eski dosya silinemezse (kilitli vb.) kurulum yarıda kalıp kayıt yazılmamasın diye hata yutulur.
    const removeOld = (file: string): Promise<void> =>
      removeRecordedFile(modsDir, file).catch((err: Error) => {
        console.warn(`[performance-pack] ${file} silinemedi:`, err.message)
      })
    const previous = await this.read(server)
    const next: PackFile = { minecraftVersion: mc, mods: {}, deps: {}, unavailable }
    for (const { slug, versionNumber, file } of toInstall) {
      const old = previous.mods[slug]
      if (old && old.file !== file.filename) await removeOld(old.file)
      next.mods[slug] = { file: file.filename, version: versionNumber }
    }
    for (const { projectId, versionNumber, file } of depInstall) {
      next.deps[projectId] = { file: file.filename, version: versionNumber }
    }
    // Artık gerekmeyen / sürümü değişen eski bağımlılık dosyaları ve paketten çıkarılmış modların
    // (Krypton gibi) kayıtlı dosyaları.
    const keep = new Set([...Object.values(next.mods), ...Object.values(next.deps)].map((m) => m.file))
    const retired = Object.entries(previous.mods)
      .filter(([slug]) => !PERFORMANCE_MODS.some((m) => m.slug === slug))
      .map(([, entry]) => entry)
    for (const old of [...Object.values(previous.deps), ...retired]) {
      if (!keep.has(old.file)) await removeOld(old.file)
    }
    // Bilinen bozuk sürüm dosyaları, kayıtta yer almasalar bile kaldırılır.
    for (const { file } of BROKEN_VERSIONS) if (!keep.has(file)) await removeOld(file)
    await this.write(server, next)
    onProgress(1)
    return this.getState(server)
  }

  async remove(server: ServerProfile): Promise<PerformancePackState> {
    const pack = await this.read(server)
    for (const { file } of [...Object.values(pack.mods), ...Object.values(pack.deps)]) {
      await removeRecordedFile(this.modsDir(server), file)
    }
    await this.write(server, { minecraftVersion: server.minecraftVersion, mods: {}, deps: {}, unavailable: [] })
    return this.getState(server)
  }

  // ── iç işleyiş ──────────────────────────────────────────────────────────────

  private modsDir(server: ServerProfile): string {
    return join(instanceDir(server.id), 'mods')
  }

  private stateFile(server: ServerProfile): string {
    return join(instanceDir(server.id), '.cevher', 'performance-pack.json')
  }

  private async read(server: ServerProfile): Promise<PackFile> {
    try {
      const raw = JSON.parse(await readFile(this.stateFile(server), 'utf8')) as Partial<PackFile>
      return {
        minecraftVersion: typeof raw.minecraftVersion === 'string' ? raw.minecraftVersion : '',
        mods: raw.mods && typeof raw.mods === 'object' ? raw.mods : {},
        deps: raw.deps && typeof raw.deps === 'object' ? raw.deps : {},
        unavailable: Array.isArray(raw.unavailable) ? raw.unavailable : []
      }
    } catch {
      return { minecraftVersion: '', mods: {}, deps: {}, unavailable: [] }
    }
  }

  private async write(server: ServerProfile, pack: PackFile): Promise<void> {
    await mkdir(join(instanceDir(server.id), '.cevher'), { recursive: true })
    await writeFile(this.stateFile(server), JSON.stringify(pack, null, 2), 'utf8')
  }
}
