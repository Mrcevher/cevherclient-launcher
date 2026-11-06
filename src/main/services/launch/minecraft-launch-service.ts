import { app } from 'electron'
import { randomUUID } from 'node:crypto'
import { createWriteStream } from 'node:fs'
import { mkdir } from 'node:fs/promises'
import { join } from 'node:path'
import type { ChildProcess } from 'node:child_process'
import { createMinecraftProcessWatcher, launch, MinecraftFolder, Version, type ResolvedVersion } from '@xmcl/core'
import { AppError } from '@shared/errors'
import { APP_NAME } from '@shared/constants'
import type { LaunchSession, LaunchStage, LaunchTarget, ServerProfile } from '@shared/types'
import type { EventSink } from '../../utils/events'
import { JsonStore } from '../../utils/json-store'
import { gameDirs, instanceDir } from '../minecraft/paths'
import { versionKey } from '../minecraft/version-key'
import type { LaunchCredentials, LaunchService, Services } from '../types'
import { prepareSessionMod } from './session-mod'
import { prepareUiMod } from './ui-mod'
import { removeGifCapeMod } from './gifcape-mod'
import { prepareClientMod } from './client-mod'
import { fitToScreen } from './window-size'

/** Her aşamanın toplam ilerlemedeki payı [başlangıç, bitiş]. */
const STAGE_RANGE: Record<LaunchStage, [number, number]> = {
  preparing: [0, 0.05],
  java: [0.05, 0.2],
  installing: [0.2, 0.75],
  modpack: [0.75, 0.85],
  starting: [0.85, 1]
}

/**
 * Process başladıktan sonra pencerenin hazır olduğu (log'dan anlaşılır) ya da bu süre
 * dolana kadar beklenir. Bu sürede çöken oyun LAUNCH_FAILED olarak raporlanır.
 */
const READY_TIMEOUT_MS = 45_000

/**
 * Oyun penceresinin açıldığını gösteren log satırları (sürümler arasında değişir). "Backend library:
 * LWJGL" bilerek yok: pencere oluşmadan yazılır. O anda launcher gizlenirse ön plan başka uygulamaya
 * geçer ve Windows yeni açılan oyun penceresinin öne gelmesine izin vermez.
 */
const READY_PATTERN = /LWJGL Version: |OpenAL initialized|Sound engine started|Reloading ResourceManager/

type Deps = Pick<Services, 'auth' | 'config' | 'status' | 'versions' | 'install' | 'modpack' | 'java' | 'settings' | 'performance'>

/** Quick Play (23w14a / 1.20+) destekleyen sürümler bunu argüman şablonlarında taşır. */
function supportsQuickPlay(version: ResolvedVersion): boolean {
  return JSON.stringify(version.arguments.game).includes('quickPlayMultiplayer')
}

/** Ayarlardaki ek JVM argümanları (boşlukla ayrılmış). */
function splitArgs(value: string): string[] {
  return value.split(/\s+/).filter(Boolean)
}

/**
 * Gerçek başlatma orkestrasyonu:
 *   hazırla (hedef, hesap, sunucu durumu, Java gereksinimi) → Java seç/indir →
 *   sürümü kur/doğrula → modpack senkronla → başlat (Quick Play ile sunucuya bağlan)
 *
 * Offline modda (Faz 2: geliştirme/test) oyun, hesabın kullanıcı adı ve
 * "OfflinePlayer:<isim>" UUID'si ile, gerçek bir erişim belirteci olmadan başlatılır.
 */
export class MinecraftLaunchService implements LaunchService {
  private readonly history = new JsonStore<Record<string, string>>('launch-history.json', {})
  private running: { session: LaunchSession; process: ChildProcess } | null = null
  private starting = false

  constructor(
    private readonly deps: Deps,
    private readonly events: EventSink
  ) {}

  getHistory(): Promise<Record<string, string>> {
    return this.history.read()
  }

  isGameRunning(): boolean {
    // launch:exit kaçırılsa bile ölü process "açık" sayılmasın: launcher gizli kalakalmaz.
    const child = this.running?.process
    return !!child && child.exitCode === null && child.signalCode === null
  }

  async launch(target: LaunchTarget): Promise<LaunchSession> {
    if (this.running || this.starting) {
      throw new AppError('LAUNCH_FAILED', 'Oyun zaten çalışıyor', { reason: 'alreadyRunning' })
    }
    this.starting = true
    try {
      return await this.doLaunch(target)
    } finally {
      this.starting = false
    }
  }

  private async doLaunch(target: LaunchTarget): Promise<LaunchSession> {
    const { auth, config, status, versions, install, modpack, java, settings, performance } = this.deps
    const prefs = await settings.get()

    // 1) Hazırlık: hedefi çöz, hesap ve sunucu durumunu kontrol et, Java gereksinimini oku.
    this.report('preparing', 0)
    const credentials = await auth.getLaunchCredentials()
    let server: ServerProfile | null = null
    let mcVersion: string
    let key: string

    if (target.kind === 'server') {
      server = await config.getServer(target.serverId)
      // Ayarlarda Fabric kapatıldıysa sunucu profili vanilla açılır (modlar ve oturum modu yüklenmez).
      if (!prefs.useFabric) server = { ...server, loader: { type: 'vanilla' } }
      const serverStatus = await status.getStatus(server)
      if (!serverStatus.online) throw new AppError('SERVER_OFFLINE', server.address, { server: server.address })
      mcVersion = server.minecraftVersion
      key = await this.serverVersionKey(server)
    } else {
      const version = await versions.find(target.versionKey)
      if (!version) throw new AppError('LAUNCH_FAILED', `Bilinmeyen sürüm: ${target.versionKey}`)
      mcVersion = version.minecraftVersion
      key = version.key
    }
    const requirement = await versions.getJavaRequirement(mcVersion)
    this.report('preparing', 1)

    // 2) Java: sürüm metadata'sındaki javaVersion.majorVersion (26.x → 25); yoksa indirilir.
    this.report('java', 0)
    const javaInstall = await java.ensure(requirement, (p) => this.report('java', p))
    this.report('java', 1)

    // 3) Sürüm (ve loader) kurulumu / doğrulaması
    this.report('installing', 0)
    const versionId = await install.ensureInstalled(key, (p) => this.report('installing', p))
    this.report('installing', 1)

    // 4) Modpack senkronu (yalnızca sunucu profillerinde)
    if (server) await modpack.sync(server, (p) => this.report('modpack', p))
    // Performans Paketi: "İlk açılışta otomatik kur" açıksa ve kurulu değilse kur. İsteğe bağlı bir
    // iyileştirme olduğu için hata oyunu engellemez.
    if (server && server.loader.type !== 'vanilla' && prefs.perfPackAutoInstall) {
      const srv = server
      try {
        if (!(await performance.getState(srv)).installed) await performance.install(srv, (p) => this.report('modpack', p))
      } catch (err) {
        console.warn('[launch] Performans Paketi kurulamadı, oyun paketsiz açılıyor:', (err as Error).message)
      }
    }
    this.report('modpack', 1)

    // 5) Başlatma
    this.report('starting', 0)
    const s = await settings.get()
    const resourcePath = gameDirs.root
    const gamePath = server ? instanceDir(server.id) : resourcePath
    await mkdir(gamePath, { recursive: true })
    // Sunucudan çıkınca oyunu kapatan oturum modu (yalnızca Fabric/Quilt sunucu profilleri).
    const sessionArgs = server ? await prepareSessionMod(server, gamePath) : []
    // SAO arayüzü modu (cevher_ui); sunucu izin vermedikçe oyun vanilla davranır.
    if (server) await prepareUiMod(server, gamePath)
    // Eski sürümlerin kurduğu GIF pelerin modu kaldırılır; pelerinler artık istemci modunun Kozmetikler'inde.
    if (server) await removeGifCapeMod(gamePath)
    // İstemci modu (cevher_client): mod menüsü, HUD modülleri ve HUD düzenleyici.
    if (server) await prepareClientMod(server, gamePath)

    let resolved: ResolvedVersion
    try {
      resolved = await Version.parse(MinecraftFolder.from(resourcePath), versionId)
    } catch (err) {
      throw new AppError('LAUNCH_FAILED', `Sürüm okunamadı (${versionId}): ${(err as Error).message}`)
    }

    // Hesap sunucusundan tek kullanımlık giriş bileti: oyuncu oyunda şifre yazmadan girer.
    // Oyun açılıp bağlanana kadar geçen süre kısa olsun diye process'ten hemen önce alınır.
    if (server) await this.deps.auth.prepareServerJoin()

    const serverAddress = server ? `${server.address}:${server.port}` : undefined
    // Pencereli modda oyun penceresi ekrandan taşmasın (ör. 1440p ekranda 2560×1440 seçiliyse).
    const windowSize = fitToScreen(s.resolution.width, s.resolution.height)
    if (windowSize.scaled && !s.resolution.fullscreen) {
      console.info(`[launch] çözünürlük ekrana sığdırıldı: ${s.resolution.width}x${s.resolution.height} → ${windowSize.width}x${windowSize.height}`)
    }

    let child: ChildProcess
    try {
      child = await launch({
        version: resolved,
        gamePath,
        resourcePath,
        javaPath: javaInstall.path,
        maxMemory: s.memoryMb,
        extraJVMArgs: [...splitArgs(s.jvmArgs), ...sessionArgs],
        resolution: s.resolution.fullscreen
          ? { fullscreen: true }
          : { width: windowSize.width, height: windowSize.height },
        ...this.authOptions(credentials),
        launcherName: APP_NAME,
        launcherBrand: app.getVersion(),
        // Doğrudan sunucuya bağlan: yeni sürümlerde Quick Play, eskilerde --server/--port.
        ...(server && serverAddress
          ? supportsQuickPlay(resolved)
            ? { quickPlayMultiplayer: serverAddress }
            : { server: { ip: server.address, port: server.port } }
          : {})
      })
    } catch (err) {
      throw new AppError('LAUNCH_FAILED', (err as Error).message)
    }
    this.report('starting', 0.3)

    const session: LaunchSession = {
      id: randomUUID(),
      target,
      startedAt: new Date().toISOString(),
      pid: child.pid ?? 0
    }
    await this.waitUntilReady(child, session)
    this.report('starting', 1)

    const historyKey = target.kind === 'server' ? target.serverId : target.versionKey
    const history = await this.history.read()
    await this.history.write({ ...history, [historyKey]: session.startedAt })
    return session
  }

  /**
   * Process'i izler: çıktıyı log dosyasına yazar, pencere hazır olunca (log'dan) veya
   * süre dolunca çözülür; hazır olmadan çıkarsa LAUNCH_FAILED fırlatır. Hazır olduktan
   * sonraki çıkış launch:exit olayı olarak bildirilir.
   */
  private async waitUntilReady(child: ChildProcess, session: LaunchSession): Promise<void> {
    await mkdir(gameDirs.logs, { recursive: true })
    const log = createWriteStream(join(gameDirs.logs, 'game-latest.log'), { flags: 'w' })
    const watcher = createMinecraftProcessWatcher(child)
    let tail = ''
    let settled = false

    await new Promise<void>((resolve, reject) => {
      let progress = 0.3
      const creep = setInterval(() => {
        progress = Math.min(0.95, progress + 0.02)
        this.report('starting', progress)
      }, 1000)
      const timer = setTimeout(() => settle(), READY_TIMEOUT_MS)

      const settle = (error?: AppError): void => {
        if (settled) return
        settled = true
        clearInterval(creep)
        clearTimeout(timer)
        if (error) return reject(error)
        this.running = { session, process: child }
        resolve()
      }

      // Çıktı tüketilmezse pipe dolar ve oyun donar; bu yüzden her iki akış da okunur.
      const capture = (data: Buffer): void => {
        log.write(data)
        const text = data.toString()
        tail = (tail + text).slice(-6000)
        if (READY_PATTERN.test(text)) settle()
      }
      child.stdout?.on('data', capture)
      child.stderr?.on('data', capture)

      watcher.on('minecraft-window-ready', () => settle())
      watcher.on('error', (err: Error) => settle(new AppError('LAUNCH_FAILED', err.message)))
      watcher.on('minecraft-exit', ({ code, crashReportLocation }) => {
        log.end()
        if (!settled) {
          const detail = [
            `Çıkış kodu ${code}`,
            crashReportLocation && `Çökme raporu: ${crashReportLocation}`,
            tail.trim().slice(-1500)
          ]
            .filter(Boolean)
            .join('\n')
          settle(new AppError('LAUNCH_FAILED', detail))
          return
        }
        this.running = null
        this.events.emit('launch:exit', { sessionId: session.id, exitCode: code ?? 0 })
      })
    })
  }

  /**
   * Sunucu profilinin sürüm anahtarı. Loader sürümü config'de belirtilmemişse o Minecraft
   * sürümü için önerilen/en yeni kararlı loader otomatik seçilir.
   */
  private async serverVersionKey(server: ServerProfile): Promise<string> {
    const { type, version } = server.loader
    if (type === 'vanilla' || version) return versionKey(type, server.minecraftVersion, version)
    const match = (await this.deps.versions.list(type)).find((v) => v.minecraftVersion === server.minecraftVersion)
    if (!match) {
      throw new AppError('LAUNCH_FAILED', `${type} için Minecraft ${server.minecraftVersion} sürümü bulunamadı`)
    }
    return match.key
  }

  /** Lisanssız giriş: kullanıcı adı + offline UUID, rastgele belirteç, "legacy" kullanıcı tipi. */
  private authOptions({ account, accessToken, userType }: LaunchCredentials): {
    gameProfile: { name: string; id: string }
    accessToken: string
    userType: 'legacy'
  } {
    return {
      gameProfile: { name: account.username, id: account.uuid.replace(/-/g, '') },
      accessToken,
      userType
    }
  }

  private report(stage: LaunchStage, stageProgress: number): void {
    const [from, to] = STAGE_RANGE[stage]
    this.events.emit('launch:progress', { stage, progress: from + (to - from) * Math.min(1, Math.max(0, stageProgress)) })
  }
}
