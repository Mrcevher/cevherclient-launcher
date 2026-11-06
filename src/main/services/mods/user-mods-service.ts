import { mkdir, readdir, readFile, rm, writeFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { join } from 'node:path'
import { AppError } from '@shared/errors'
import type { InstalledMod, ModKind, ModSearchResult, ModSide, ServerProfile, UserModsState } from '@shared/types'
import { instanceDir } from '../minecraft/paths'
import type { PerformancePackService, UserModsService } from '../types'
import {
  downloadModFiles,
  getProjects,
  latestFabricVersion,
  latestShaderVersion,
  primaryFile,
  searchMods,
  type ModrinthFile
} from './modrinth'

interface UserModsFile {
  minecraftVersion: string
  mods: Record<string, InstalledMod>
}

interface PlanItem {
  projectId: string
  version: string
  file: ModrinthFile
  dependency: boolean
  kind: ModKind
}

const SIDES: readonly string[] = ['required', 'optional', 'unsupported']

/** Shader paketlerini yükleyen Iris'in sabit Modrinth proje kimliği (slug değil: yeniden atanamaz). */
const IRIS = 'YL57xq9U'

/** Shader paketi çalıştırabilen modların Modrinth slug'ları. */
const SHADER_MOD_SLUGS: ReadonlySet<string> = new Set(['iris', 'oculus', 'optifine', 'optifabric', 'canvas-renderer'])

/** Oyuncunun (ya da modpack'in) kendi koyduğu shader modlarını dosya adından tanır. */
const SHADER_MOD_JAR = /^(iris|oculus|optifine|optifabric|canvas)/i

/**
 * Oyuncunun launcher'dan Modrinth'te arayıp kurduğu modlar ve shader paketleri. Modlar sunucunun
 * oyun klasörüne (instances/<id>/mods), shader paketleri instances/<id>/shaderpacks'e kurulur;
 * zorunlu bağımlılıklar da otomatik eklenir. Shader kurulurken oyunda hiç shader modu yoksa Iris
 * da bağımlılık olarak eklenir; varsa (Iris, Oculus, OptiFine …) dokunulmaz.
 * Kayıt instances/<id>/.cevher/user-mods.json'dadır; yalnızca buradaki dosyalar silinir.
 */
export class ModrinthUserModsService implements UserModsService {
  constructor(private readonly performance: PerformancePackService) {}

  async search(server: ServerProfile, query: string, offset: number, kind: ModKind): Promise<ModSearchResult> {
    const res = await searchMods(query.slice(0, 100), server.minecraftVersion, offset, kind)
    return {
      total: res?.total_hits ?? 0,
      offset: res?.offset ?? offset,
      hits: (res?.hits ?? []).map((h) => ({
        projectId: h.project_id,
        slug: h.slug,
        title: h.title,
        description: h.description,
        author: h.author,
        iconUrl: h.icon_url || null,
        downloads: h.downloads,
        serverSide: (SIDES.includes(h.server_side) ? h.server_side : 'unknown') as ModSide
      }))
    }
  }

  async list(server: ServerProfile): Promise<UserModsState> {
    const file = await this.read(server)
    const mods = Object.values(file.mods)
      .filter((m) => existsSync(join(this.dir(server, m.kind), m.file)))
      .sort((a, b) => Number(a.dependency) - Number(b.dependency) || a.title.localeCompare(b.title))
    const pack = await this.performance.getState(server)
    return {
      serverId: server.id,
      minecraftVersion: server.minecraftVersion,
      mods,
      shaderMod: await this.detectShaderMod(server, file),
      packSlugs: pack.mods.filter((m) => m.installedVersion).map((m) => m.slug)
    }
  }

  async install(server: ServerProfile, projectId: string, kind: ModKind): Promise<UserModsState> {
    const mc = server.minecraftVersion
    const state = await this.read(server)
    const pack = await this.performance.getState(server)
    // Pakette kurulu olanların proje kimlikleri: bağımlılık olarak ikinci kez indirilmesin.
    const packProjects = await getProjects(pack.mods.filter((m) => m.installedVersion).map((m) => m.slug))
    const present = new Set([...Object.keys(state.mods), ...packProjects.map((p) => p.id), ...pack.dependencies])

    const plan: PlanItem[] = []
    const queue: string[] = []
    if (kind === 'shader') {
      const version = await latestShaderVersion(projectId, mc)
      const file = version && primaryFile(version)
      if (!version || !file) {
        throw new AppError('DOWNLOAD_FAILED', `Bu shader Minecraft ${mc} için yayınlanmamış`, { reason: 'noVersion' })
      }
      plan.push({ projectId: version.project_id, version: version.version_number, file, dependency: false, kind })
      // Shader'ı çalıştıracak bir mod yoksa Iris eklenir; varsa oyuncunun seçimine dokunulmaz.
      if (!(await this.detectShaderMod(server, state))) queue.push(IRIS)
    } else {
      queue.push(projectId)
    }

    // Modu (veya Iris'i) ve zorunlu bağımlılıklarını (özyinelemeli) çöz.
    const seen = new Set<string>()
    while (queue.length > 0) {
      const id = queue.shift()!
      if (seen.has(id) || (id !== projectId && present.has(id))) continue
      seen.add(id)
      let version = await latestFabricVersion(id, mc)
      // Iris sabit proje kimliğiyle çözülür; başka bir projenin sürümü gelirse yok sayılır.
      if (id === IRIS && version && version.project_id !== IRIS) version = null
      if (!version) {
        if (id === projectId) {
          throw new AppError('DOWNLOAD_FAILED', `Bu mod Minecraft ${mc} (Fabric) için yayınlanmamış`, { reason: 'noVersion' })
        }
        console.warn(`[mods] ${id} bağımlılığı ${mc} için yok, atlandı`)
        continue
      }
      const file = primaryFile(version)
      if (!file) continue
      plan.push({
        projectId: version.project_id,
        version: version.version_number,
        file,
        dependency: id !== projectId,
        kind: 'mod'
      })
      for (const dep of version.dependencies) {
        if (dep.dependency_type === 'required' && dep.project_id) queue.push(dep.project_id)
      }
    }

    await mkdir(this.dir(server, 'mod'), { recursive: true })
    await mkdir(this.dir(server, 'shader'), { recursive: true })
    await downloadModFiles(plan.map((p) => ({ path: join(this.dir(server, p.kind), p.file.filename), file: p.file })))

    const projects = new Map((await getProjects(plan.map((p) => p.projectId))).map((p) => [p.id, p]))
    for (const p of plan) {
      const old = state.mods[p.projectId]
      if (old && old.file !== p.file.filename) await rm(join(this.dir(server, old.kind), old.file), { force: true })
      const info = projects.get(p.projectId)
      state.mods[p.projectId] = {
        projectId: p.projectId,
        slug: info?.slug ?? p.projectId,
        title: info?.title ?? p.file.filename,
        version: p.version,
        file: p.file.filename,
        iconUrl: info?.icon_url ?? null,
        kind: p.kind,
        // Daha önce elle kurulmuş bir mod bağımlılık olarak güncellenirse "elle kurulmuş" kalır.
        dependency: p.dependency && (old ? old.dependency : true),
        requiredBy: p.dependency ? [...new Set([...(old?.requiredBy ?? []), projectId])] : old?.requiredBy
      }
    }
    // Iris daha önce başka bir shader için bağımlılık olarak kurulduysa bu shader da ona bağlanır:
    // ilk shader kaldırılınca Iris, hâlâ kullanılan ikinci shader'ın altından çekilmesin.
    if (kind === 'shader') {
      for (const m of Object.values(state.mods)) {
        if (m.kind === 'mod' && m.dependency && SHADER_MOD_SLUGS.has(m.slug)) {
          m.requiredBy = [...new Set([...(m.requiredBy ?? []), projectId])]
        }
      }
    }
    state.minecraftVersion = mc
    await this.write(server, state)
    return this.list(server)
  }

  async remove(server: ServerProfile, projectId: string): Promise<UserModsState> {
    const state = await this.read(server)
    const mod = state.mods[projectId]
    if (mod) {
      await rm(join(this.dir(server, mod.kind), mod.file), { force: true })
      delete state.mods[projectId]
      // Yalnızca bu mod için kurulmuş bağımlılıklar sahipsiz kaldıysa onları da kaldır.
      for (const dep of Object.values(state.mods)) {
        if (!dep.dependency || !dep.requiredBy?.includes(projectId)) continue
        dep.requiredBy = dep.requiredBy.filter((id) => id !== projectId)
        if (dep.requiredBy.length === 0) {
          await rm(join(this.dir(server, dep.kind), dep.file), { force: true })
          delete state.mods[dep.projectId]
        }
      }
      await this.write(server, state)
    }
    return this.list(server)
  }

  // ── iç işleyiş ──────────────────────────────────────────────────────────────

  private dir(server: ServerProfile, kind: ModKind): string {
    return join(instanceDir(server.id), kind === 'shader' ? 'shaderpacks' : 'mods')
  }

  /**
   * Oyunda shader paketi çalıştırabilen bir mod var mı? Önce launcher'ın kaydına, sonra mods
   * klasöründeki jar adlarına bakar (modpack'in ya da oyuncunun elle koyduğu Iris/OptiFine da sayılsın).
   * Varsa görünen adını, yoksa null döndürür.
   */
  private async detectShaderMod(server: ServerProfile, state: UserModsFile): Promise<string | null> {
    const modsDir = this.dir(server, 'mod')
    const known = Object.values(state.mods).find(
      (m) => m.kind === 'mod' && SHADER_MOD_SLUGS.has(m.slug) && existsSync(join(modsDir, m.file))
    )
    if (known) return known.title
    try {
      const jar = (await readdir(modsDir)).find((name) => /\.jar$/i.test(name) && SHADER_MOD_JAR.test(name))
      return jar ? jar.replace(/\.jar$/i, '') : null
    } catch {
      return null
    }
  }

  private stateFile(server: ServerProfile): string {
    return join(instanceDir(server.id), '.cevher', 'user-mods.json')
  }

  private async read(server: ServerProfile): Promise<UserModsFile> {
    try {
      const raw = JSON.parse(await readFile(this.stateFile(server), 'utf8')) as Partial<UserModsFile>
      const mods: Record<string, InstalledMod> = raw.mods && typeof raw.mods === 'object' ? raw.mods : {}
      // Shader desteğinden önceki kayıtlarda "kind" yok: hepsi moddur.
      for (const m of Object.values(mods)) m.kind = m.kind === 'shader' ? 'shader' : 'mod'
      return {
        minecraftVersion: typeof raw.minecraftVersion === 'string' ? raw.minecraftVersion : server.minecraftVersion,
        mods
      }
    } catch {
      return { minecraftVersion: server.minecraftVersion, mods: {} }
    }
  }

  private async write(server: ServerProfile, state: UserModsFile): Promise<void> {
    await mkdir(join(instanceDir(server.id), '.cevher'), { recursive: true })
    await writeFile(this.stateFile(server), JSON.stringify(state, null, 2), 'utf8')
  }
}
