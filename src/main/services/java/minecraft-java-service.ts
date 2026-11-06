import { execFile } from 'node:child_process'
import { existsSync } from 'node:fs'
import { lstat, readdir, realpath, stat } from 'node:fs/promises'
import { homedir } from 'node:os'
import { basename, dirname, join, parse, sep } from 'node:path'
import {
  createJavaRuntimeInstallWorkflow,
  getPotentialJavaLocations,
  parseJavaVersion,
  type JavaRuntimeTarget
} from '@xmcl/installer'
import { AppError } from '@shared/errors'
import type { JavaInstallation } from '@shared/types'
import { DownloadController, runWorkflow } from '../minecraft/downloader'
import { fetchJson } from '../minecraft/http'
import { gameDirs } from '../minecraft/paths'
import type { JavaRequirement, JavaService, ProgressCallback, SettingsService } from '../types'

const RUNTIME_INDEX =
  'https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json'

const isWin = process.platform === 'win32'
const JAVA_EXE = isWin ? 'java.exe' : 'java'

interface JavaProbeInfo {
  majorVersion: number
  vendor: string
  /** java.home özelliği; aynı kurulumun farklı yollarını tekilleştirmek için. */
  home?: string
}

interface Probe {
  mtimeMs: number
  info: JavaProbeInfo | null
}

/** Mojang runtime index'inde bu makinenin platform anahtarı. */
function runtimePlatform(): string | undefined {
  const { platform, arch } = process
  if (platform === 'win32') return arch === 'arm64' ? 'windows-arm64' : arch === 'ia32' ? 'windows-x86' : 'windows-x64'
  if (platform === 'darwin') return arch === 'arm64' ? 'mac-os-arm64' : 'mac-os'
  if (platform === 'linux') return arch === 'ia32' ? 'linux-i386' : arch === 'x64' ? 'linux' : undefined
  return undefined
}

/** Launcher'ın indirdiği runtime'ın çalıştırılabilir dosyası. */
function runtimeExecutable(component: string): string {
  const home = join(gameDirs.runtimes, component)
  if (process.platform === 'darwin') return join(home, 'jre.bundle', 'Contents', 'Home', 'bin', 'java')
  return join(home, 'bin', JAVA_EXE)
}

/** Windows'ta konsol penceresi açmamak için javaw.exe tercih edilir. */
function preferWindowed(javaPath: string): string {
  if (!isWin || basename(javaPath).toLowerCase() !== 'java.exe') return javaPath
  const javaw = join(dirname(javaPath), 'javaw.exe')
  return existsSync(javaw) ? javaw : javaPath
}

/** Yaygın kurulum klasörlerinin altındaki JDK/JRE ana dizinleri. */
async function commonJavaHomes(): Promise<string[]> {
  const roots: string[] = []
  const env = process.env
  if (isWin) {
    for (const base of [env.ProgramFiles, env['ProgramFiles(x86)'], env.LOCALAPPDATA && join(env.LOCALAPPDATA, 'Programs')]) {
      if (!base) continue
      for (const vendor of ['Java', 'Eclipse Adoptium', 'Eclipse Foundation', 'Microsoft', 'Zulu', 'BellSoft', 'Amazon Corretto', 'Semeru', 'GraalVM']) {
        roots.push(join(base, vendor))
      }
    }
  } else if (process.platform === 'darwin') {
    roots.push('/Library/Java/JavaVirtualMachines', join(homedir(), 'Library/Java/JavaVirtualMachines'))
  } else {
    roots.push('/usr/lib/jvm', '/usr/java', '/opt/java', join(homedir(), '.sdkman/candidates/java'), join(homedir(), '.jdks'))
  }

  const homes: string[] = []
  for (const root of roots) {
    const entries = await readdir(root, { withFileTypes: true }).catch(() => [])
    for (const entry of entries) {
      if (!entry.isDirectory()) continue
      const home = join(root, entry.name)
      homes.push(process.platform === 'darwin' ? join(home, 'Contents', 'Home') : home)
    }
  }
  return homes
}

/**
 * Aday Java dosyasını çalıştırmadan önce güvenilir bir konumda olduğunu doğrular; başka bir yerel
 * kullanıcının yazabildiği bir klasördeki sahte java çalıştırılmasın.
 * - Windows: yalnızca Program Files, kullanıcı profili ve LocalAppData/AppData altı (yönetici
 *   yazma yetkisi gerektiren veya kullanıcıya özel konumlar); kök dizin ve paylaşılan klasörler değil.
 * - macOS/Linux: dosya ve tüm üst klasörleri root'a ya da bu kullanıcıya ait ve grup/herkes tarafından
 *   yazılabilir olmamalı.
 * Ayarlardan elle seçilen yol kullanıcının kendi kararıdır, bu denetimden geçmez.
 */
async function isTrustedLocation(exe: string): Promise<boolean> {
  const real = await realpath(exe).catch(() => undefined)
  if (!real) return false
  if (isWin) {
    const env = process.env
    const allowed = [env.ProgramFiles, env['ProgramFiles(x86)'], env.ProgramW6432, env.LOCALAPPDATA, env.APPDATA, homedir()]
      .filter((p): p is string => !!p)
      .map((p) => p.toLowerCase().replace(/[\\/]+$/, '') + sep)
    const lower = real.toLowerCase()
    return allowed.some((prefix) => lower.startsWith(prefix))
  }
  const uid = process.getuid?.()
  let current = real
  for (;;) {
    const st = await lstat(current).catch(() => undefined)
    if (!st) return false
    if (st.uid !== 0 && st.uid !== uid) return false
    if (!st.isSymbolicLink() && (st.mode & 0o022) !== 0) return false
    const parent = dirname(current)
    if (parent === current || current === parse(current).root) return true
    current = parent
  }
}

/** Resmi Minecraft launcher'ının ve bu launcher'ın indirdiği runtime'lar. */
async function minecraftRuntimeExecutables(): Promise<string[]> {
  const out: string[] = []
  const ours = await readdir(gameDirs.runtimes, { withFileTypes: true }).catch(() => [])
  for (const entry of ours) if (entry.isDirectory()) out.push(runtimeExecutable(entry.name))

  const official: string[] = []
  if (isWin && process.env.APPDATA) official.push(join(process.env.APPDATA, '.minecraft', 'runtime'))
  if (isWin && process.env.LOCALAPPDATA) {
    official.push(join(process.env.LOCALAPPDATA, 'Packages', 'Microsoft.4297127D64EC6_8wekyb3d8bbwe', 'LocalCache', 'Local', 'runtime'))
  }
  if (process.platform === 'darwin') official.push(join(homedir(), 'Library/Application Support/minecraft/runtime'))
  if (process.platform === 'linux') official.push(join(homedir(), '.minecraft/runtime'))
  const platform = runtimePlatform()
  for (const root of official) {
    const components = await readdir(root, { withFileTypes: true }).catch(() => [])
    for (const c of components) {
      if (!c.isDirectory() || !platform) continue
      const home = join(root, c.name, platform, c.name)
      out.push(
        process.platform === 'darwin' ? join(home, 'jre.bundle', 'Contents', 'Home', 'bin', 'java') : join(home, 'bin', JAVA_EXE)
      )
    }
  }
  return out
}

/** `java -XshowSettings:properties -version` çıktısından sürüm ve sağlayıcı. */
function runJavaVersion(javaPath: string): Promise<JavaProbeInfo | null> {
  return new Promise((resolve) => {
    execFile(
      javaPath,
      ['-XshowSettings:properties', '-version'],
      { timeout: 10_000, windowsHide: true },
      (_err, stdout, stderr) => {
        const text = `${stderr}\n${stdout}`
        const version = parseJavaVersion(stderr) ?? parseJavaVersion(stdout)
        const spec = /java\.specification\.version = (\S+)/.exec(text)?.[1]
        const specMajor = spec ? Number(spec.startsWith('1.') ? spec.slice(2) : spec) : NaN
        const majorVersion = Number.isFinite(specMajor) && specMajor > 0 ? specMajor : version?.majorVersion
        if (!majorVersion) return resolve(null)
        const vendor =
          /java\.vendor = (.+)/.exec(text)?.[1]?.trim() ??
          (/OpenJDK/i.test(text) ? 'OpenJDK' : /Java\(TM\)/.test(text) ? 'Oracle' : 'Java')
        const home = /java.home = (.+)/.exec(text)?.[1]?.trim()
        resolve({ majorVersion, vendor, home })
      }
    )
  })
}

/**
 * Gerçek Java servisi:
 * - detect(): JAVA_HOME, PATH (`where`/`which`), Windows kayıt defteri, yaygın kurulum
 *   klasörleri, resmi Minecraft launcher runtime'ları ve bu launcher'ın indirdikleri
 *   taranır; her aday `java -version` ile doğrulanır.
 * - resolve(major): önce tam eşleşen majör, yoksa en düşük üst sürüm (eski Minecraft
 *   sürümleri yeni Java'larla bozulabildiği için tam eşleşme önceliklidir).
 * - ensure(req): uygun Java yoksa sürüm metadata'sındaki runtime bileşeni Mojang'dan
 *   indirilir (ör. 26.x → java-runtime-epsilon = Java 25).
 */
export class MinecraftJavaService implements JavaService {
  private readonly probes = new Map<string, Probe>()
  private readonly downloads = new Map<string, Promise<JavaInstallation>>()

  constructor(private readonly settings: SettingsService) {}

  async detect(): Promise<JavaInstallation[]> {
    const candidates = new Set<string>()
    for (const home of await commonJavaHomes()) candidates.add(join(home, 'bin', JAVA_EXE))
    for (const exe of await getPotentialJavaLocations().catch(() => [])) candidates.add(exe)
    for (const exe of await minecraftRuntimeExecutables()) candidates.add(exe)

    const found: JavaInstallation[] = []
    const seen = new Set<string>()
    await Promise.all(
      [...candidates].map(async (exe) => {
        if (!(await isTrustedLocation(exe))) return
        const info = await this.probe(exe)
        if (!info) return
        // Aynı kurulumu farklı yollardan (PATH kısayolu, Oracle javapath) bir kez say;
        // mümkünse kurulumun kendi bin/ klasöründeki dosya gösterilir.
        const home = info.home ?? (await realHome(exe))
        if (seen.has(home.toLowerCase())) return
        seen.add(home.toLowerCase())
        const own = info.home ? join(info.home, 'bin', JAVA_EXE) : exe
        const path = existsSync(own) ? own : exe
        found.push({ path: preferWindowed(path), majorVersion: info.majorVersion, vendor: info.vendor, source: 'auto' })
      })
    )
    return found.sort((a, b) => b.majorVersion - a.majorVersion || a.path.localeCompare(b.path))
  }

  async resolve(requiredMajor: number): Promise<JavaInstallation> {
    const custom = await this.customJava(requiredMajor)
    if (custom) return custom
    const match = pick(await this.detect(), requiredMajor)
    if (!match) throw new AppError('JAVA_NOT_FOUND', `Java ${requiredMajor} bulunamadı`, { version: requiredMajor })
    return match
  }

  async ensure(requirement: JavaRequirement, onProgress: ProgressCallback): Promise<JavaInstallation> {
    const required = requirement.majorVersion
    const custom = await this.customJava(required)
    if (custom) {
      onProgress(1)
      return custom
    }

    const installs = await this.detect()
    const exact = installs.find((j) => j.majorVersion === required)
    if (exact) {
      onProgress(1)
      return exact
    }

    try {
      return await this.download(requirement, onProgress)
    } catch (err) {
      // İndirilemediyse (çevrimdışı, platform desteklenmiyor) uyumlu üst sürümle dene.
      const fallback = pick(installs, required)
      if (fallback) return fallback
      throw new AppError('JAVA_NOT_FOUND', (err as Error).message, { version: required })
    }
  }

  // ── iç işleyiş ────────────────────────────────────────────────────────────

  /** Ayarlarda özel yol seçildiyse onu doğrular; seçilmediyse undefined. */
  private async customJava(requiredMajor: number): Promise<JavaInstallation | undefined> {
    const { javaPath } = await this.settings.get()
    if (!javaPath) return undefined
    const info = existsSync(javaPath) ? await this.probe(javaPath) : null
    if (!info) throw new AppError('JAVA_NOT_FOUND', javaPath, { version: requiredMajor })
    if (info.majorVersion < requiredMajor) {
      throw new AppError('JAVA_NOT_FOUND', `${javaPath}: Java ${info.majorVersion} < ${requiredMajor}`, {
        version: requiredMajor
      })
    }
    return { path: javaPath, majorVersion: info.majorVersion, vendor: info.vendor, source: 'custom' }
  }

  private async probe(exe: string): Promise<Probe['info']> {
    const st = await stat(exe).catch(() => undefined)
    if (!st?.isFile()) return null
    const cached = this.probes.get(exe)
    if (cached && cached.mtimeMs === st.mtimeMs) return cached.info
    const info = await runJavaVersion(exe)
    this.probes.set(exe, { mtimeMs: st.mtimeMs, info })
    return info
  }

  /** Aynı bileşen için eşzamanlı istekler tek indirmeyi paylaşır. */
  private download(requirement: JavaRequirement, onProgress: ProgressCallback): Promise<JavaInstallation> {
    const running = this.downloads.get(requirement.component)
    if (running) return running
    const job = this.downloadRuntime(requirement, onProgress).finally(() => this.downloads.delete(requirement.component))
    this.downloads.set(requirement.component, job)
    return job
  }

  private async downloadRuntime(requirement: JavaRequirement, onProgress: ProgressCallback): Promise<JavaInstallation> {
    const platform = runtimePlatform()
    if (!platform) throw new Error(`Bu platform için Mojang Java runtime'ı yok: ${process.platform}-${process.arch}`)
    const index = await fetchJson<Record<string, Record<string, JavaRuntimeTarget[]>>>(RUNTIME_INDEX)
    const target = index[platform]?.[requirement.component]?.[0]
    if (!target) throw new Error(`Runtime bulunamadı: ${requirement.component} (${platform})`)

    const ctl = new DownloadController()
    const poll = setInterval(() => {
      if (ctl.totalBytes > 0) onProgress(Math.min(0.99, ctl.downloadedBytes / ctl.totalBytes))
    }, 250)
    try {
      await runWorkflow(
        createJavaRuntimeInstallWorkflow({ target, destination: join(gameDirs.runtimes, requirement.component) }),
        ctl
      )
    } finally {
      clearInterval(poll)
    }

    const exe = runtimeExecutable(requirement.component)
    this.probes.delete(exe)
    const info = await this.probe(exe)
    if (!info) throw new Error(`İndirilen runtime çalıştırılamadı: ${exe}`)
    onProgress(1)
    return { path: preferWindowed(exe), majorVersion: info.majorVersion, vendor: info.vendor, source: 'auto' }
  }
}

/** Tam eşleşme, yoksa gereksinimi karşılayan en düşük majör. */
function pick(installs: JavaInstallation[], requiredMajor: number): JavaInstallation | undefined {
  return (
    installs.find((j) => j.majorVersion === requiredMajor) ??
    installs.filter((j) => j.majorVersion > requiredMajor).sort((a, b) => a.majorVersion - b.majorVersion)[0]
  )
}

/** Kurulumun kimliği: sembolik bağları çözülmüş Java ana dizini. */
async function realHome(exe: string): Promise<string> {
  const real = await realpath(exe).catch(() => exe)
  return dirname(dirname(real))
}
