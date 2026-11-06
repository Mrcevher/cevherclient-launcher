import { app } from 'electron'
import { autoUpdater } from 'electron-updater'
import type { UpdateStatus } from '@shared/types'
import type { EventSink } from './utils/events'

/** Açılıştan sonra bu aralıkla yeniden kontrol edilir. */
const CHECK_INTERVAL_MS = 4 * 60 * 60_000

/**
 * Faz 5: GitHub Releases üzerinden otomatik güncelleme (electron-updater).
 * Yalnızca paketlenmiş sürümde çalışır; güncelleme arka planda indirilir, renderer'a
 * `update:status` ile bildirilir ve kullanıcı "Yeniden başlat ve kur" dediğinde kurulur. Yayın adresi electron-builder.yml `publish` alanından
 * (paket içindeki app-update.yml) gelir.
 */
export class LauncherUpdater {
  private status: UpdateStatus = { state: 'none' }
  private started = false
  /** İlk denetim sonuçlanınca start()'ın döndürdüğü sözü çözer (açılış penceresi bekler). */
  private settleFirstCheck: ((status: UpdateStatus) => void) | null = null

  constructor(private readonly events: EventSink) {}

  get enabled(): boolean {
    return app.isPackaged && process.env.CEVHER_DISABLE_UPDATES !== '1'
  }

  /** Denetimi başlatır; ilk denetimin sonucuyla çözülür. Güncelleme kapalıysa hemen null döner. */
  start(): Promise<UpdateStatus | null> {
    if (!this.enabled || this.started) return Promise.resolve(null)
    this.started = true
    const firstCheck = new Promise<UpdateStatus>((resolve) => (this.settleFirstCheck = resolve))

    autoUpdater.autoDownload = true
    // Kurulum yalnızca kullanıcı "Yeniden başlat ve kur" deyince yapılır; kapanışta sessizce kurulmaz.
    autoUpdater.autoInstallOnAppQuit = false
    autoUpdater.logger = null

    autoUpdater.on('checking-for-update', () => this.set({ state: 'checking' }))
    autoUpdater.on('update-not-available', () => this.set({ state: 'none' }))
    autoUpdater.on('update-available', (info) => this.set({ state: 'available', version: info.version }))
    autoUpdater.on('download-progress', (p) =>
      this.set({ state: 'downloading', version: this.status.version, progress: p.percent / 100 })
    )
    autoUpdater.on('update-downloaded', (info) => this.set({ state: 'downloaded', version: info.version, progress: 1 }))
    autoUpdater.on('error', (err) => {
      // Ağ yok / yayın yok gibi durumlar oyuncuyu rahatsız etmez; yalnızca loglanır.
      console.warn('[updater]', String(err?.message ?? err).split('\n')[0])
      if (this.status.state !== 'downloaded') this.set({ state: 'error', version: this.status.version })
    })

    const check = (): void => {
      if (this.status.state === 'downloading' || this.status.state === 'downloaded') return
      autoUpdater.checkForUpdates().catch(() => undefined)
    }
    check()
    setInterval(check, CHECK_INTERVAL_MS).unref()
    return firstCheck
  }

  getStatus(): UpdateStatus {
    return this.status
  }

  /**
   * İndirilmiş güncellemeyi kurmak için launcher'ı kapatıp yeniden açar. Kurulum sessiz yapılır
   * (sihirbaz çıkmaz); açılışta açılış penceresi "Güncellendi" yazar (bkz. last-version.ts).
   */
  install(): void {
    if (this.status.state !== 'downloaded') return
    setImmediate(() => autoUpdater.quitAndInstall(true, true))
  }

  private set(status: UpdateStatus): void {
    this.status = status
    this.events.emit('update:status', status)
    if (status.state !== 'checking') {
      this.settleFirstCheck?.(status)
      this.settleFirstCheck = null
    }
  }
}
