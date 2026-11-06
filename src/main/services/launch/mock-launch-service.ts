import { randomUUID } from 'node:crypto'
import { AppError } from '@shared/errors'
import { requiredJavaMajor } from '@shared/utils'
import type { LaunchSession, LaunchStage, LaunchTarget, ServerProfile } from '@shared/types'
import type { EventSink } from '../../utils/events'
import { JsonStore } from '../../utils/json-store'
import { delay, maybeFail, randomInt } from '../mock/mock-utils'
import { versionKey } from '../versions/mock-version-service'
import type { Services, LaunchService } from '../types'

/** Her aşamanın toplam ilerlemedeki payı [başlangıç, bitiş]. */
const STAGE_RANGE: Record<LaunchStage, [number, number]> = {
  preparing: [0, 0.08],
  java: [0.08, 0.16],
  installing: [0.16, 0.55],
  modpack: [0.55, 0.85],
  starting: [0.85, 1]
}

/** Mock oyunun "açık kaldığı" süre; ardından launch:exit olayı gönderilir. */
const MOCK_SESSION_MS = 12_000

type Deps = Pick<Services, 'auth' | 'config' | 'status' | 'versions' | 'install' | 'modpack' | 'java' | 'settings'>

/**
 * Başlatma orkestrasyonu. Akış gerçek implementasyonla aynıdır, yalnızca son adımda
 * process başlatmak yerine argümanlar loglanır:
 *   hazırla → Java seç → sürümü kur → modpack senkronla → başlat (Quick Play ile sunucuya bağlan)
 */
export class MockLaunchService implements LaunchService {
  private readonly history = new JsonStore<Record<string, string>>('launch-history.json', {})
  private running: LaunchSession | null = null

  constructor(
    private readonly deps: Deps,
    private readonly events: EventSink
  ) {}

  getHistory(): Promise<Record<string, string>> {
    return this.history.read()
  }

  isGameRunning(): boolean {
    return this.running !== null
  }

  async launch(target: LaunchTarget): Promise<LaunchSession> {
    if (this.running) throw new AppError('LAUNCH_FAILED', 'Oyun zaten çalışıyor', { reason: 'alreadyRunning' })
    const { auth, config, status, versions, install, modpack, java, settings } = this.deps

    // 1) Hazırlık: hedefi çöz, hesap ve sunucu durumunu kontrol et.
    this.report('preparing', 0)
    const account = await auth.requireAccount()
    let server: ServerProfile | null = null
    let mcVersion: string
    let key: string

    if (target.kind === 'server') {
      server = await config.getServer(target.serverId)
      const serverStatus = await status.getStatus(server)
      if (!serverStatus.online) {
        throw new AppError('SERVER_OFFLINE', server.address, { server: server.address })
      }
      mcVersion = server.minecraftVersion
      key = versionKey(server.loader.type, server.minecraftVersion, server.loader.version)
    } else {
      const version = await versions.find(target.versionKey)
      if (!version) throw new AppError('LAUNCH_FAILED', `Bilinmeyen sürüm: ${target.versionKey}`)
      mcVersion = version.minecraftVersion
      key = version.key
    }
    this.report('preparing', 1)

    // 2) Java seçimi (26.x → Java 25, 1.20.5+ → Java 21 …)
    this.report('java', 0)
    const javaInstall = await java.resolve(requiredJavaMajor(mcVersion))
    this.report('java', 1)

    // 3) Sürüm (ve loader) kurulumu
    await install.ensureInstalled(key, (p) => this.report('installing', p))

    // 4) Modpack senkronu (yalnızca sunucu profillerinde)
    if (server) {
      await modpack.sync(server, (p) => this.report('modpack', p))
    } else {
      this.report('modpack', 1)
    }

    // 5) Başlatma
    this.report('starting', 0)
    const s = await settings.get()
    const args = [
      `-Xmx${s.memoryMb}M`,
      ...s.jvmArgs.split(/\s+/).filter(Boolean),
      '<main-class>',
      '--username', account.username,
      '--uuid', account.uuid,
      '--width', String(s.resolution.width),
      '--height', String(s.resolution.height),
      ...(s.resolution.fullscreen ? ['--fullscreen'] : []),
      // Faz 2: Quick Play ile doğrudan sunucuya bağlan.
      ...(server ? ['--quickPlayMultiplayer', `${server.address}:${server.port}`] : [])
    ]
    console.info('[launch:mock]', javaInstall.path, args.join(' '))
    await delay(700)
    maybeFail('LAUNCH_FAILED')
    this.report('starting', 1)

    const session: LaunchSession = {
      id: randomUUID(),
      target,
      startedAt: new Date().toISOString(),
      pid: randomInt(2000, 60000)
    }
    this.running = session

    const historyKey = target.kind === 'server' ? target.serverId : target.versionKey
    const history = await this.history.read()
    await this.history.write({ ...history, [historyKey]: session.startedAt })

    setTimeout(() => {
      this.running = null
      this.events.emit('launch:exit', { sessionId: session.id, exitCode: 0 })
    }, MOCK_SESSION_MS)

    return session
  }

  private report(stage: LaunchStage, stageProgress: number): void {
    const [from, to] = STAGE_RANGE[stage]
    this.events.emit('launch:progress', { stage, progress: from + (to - from) * stageProgress })
  }
}
