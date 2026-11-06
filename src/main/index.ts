/**
 * Cevherclient main process giriş noktası.
 * Dosya, ağ ve process işlemleri yalnızca burada (servisler aracılığıyla) yapılır.
 */

import { app, BrowserWindow, session } from 'electron'
import { registerIpc } from './ipc/register'
import { consumeUpdatedFlag } from './last-version'
import { createServices } from './services/container'
import type { Services } from './services/types'
import { runStartup } from './splash'
import { LauncherUpdater } from './updater'
import { windowEventSink, type EventSink } from './utils/events'
import { createMainWindow, showMainWindow } from './window'

// Tek örnek: ikinci kez açılırsa mevcut pencereyi öne getir.
if (!app.requestSingleInstanceLock()) {
  app.quit()
} else {
  let services: Services | null = null

  // Oyun açıkken launcher gizli kalır (görev çubuğu/masaüstü kısayoluna tıklansa da); oyun kapanınca
  // launch:exit ile kendiliğinden geri gelir.
  app.on('second-instance', () => {
    if (services?.launch.isGameRunning()) return
    showMainWindow()
  })

  void app.whenReady().then(async () => {
    app.setAppUserModelId('net.cevherclient.launcher')

    // Renderer'ın kamera/mikrofon/bildirim vb. izin isteklerini reddet.
    session.defaultSession.setPermissionRequestHandler((_wc, _permission, callback) => callback(false))

    // Oyun penceresi açılınca launcher gizlenir (bkz. launch:start); oyun kapanınca geri gelir.
    const events: EventSink = {
      emit(event, payload) {
        if (event === 'launch:exit') showMainWindow()
        windowEventSink.emit(event, payload)
      }
    }

    const updater = new LauncherUpdater(windowEventSink)
    services = createServices(events)
    registerIpc(services, updater)

    // Önce açılış penceresi (logo + çubuk) gelir; ana pencere arkada yüklenir, çubuk dolunca görünür.
    const main = createMainWindow({ showWhenReady: false })
    const mainReady = new Promise<void>((resolve) => main.once('ready-to-show', () => resolve()))
    const language = await services.settings.get().then(
      (s) => s.language,
      () => 'tr'
    )
    const justUpdated = await consumeUpdatedFlag()
    await runStartup({ main, mainReady, language, updateCheck: updater.start(), justUpdated })

    app.on('activate', () => {
      if (BrowserWindow.getAllWindows().length === 0) createMainWindow()
    })
  })

  app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') app.quit()
  })
}
