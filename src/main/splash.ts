import { BrowserWindow } from 'electron'
import { join } from 'node:path'
import type { SplashStage, UpdateStatus } from '@shared/types'

/** Çok hızlı açılışta yanıp sönmesin diye açılış penceresinin en az görünür kalacağı süre. */
const MIN_VISIBLE_MS = 1800
/** Güncelleme denetimi bundan uzun sürerse beklenmez (ağ yavaş / yok). */
const UPDATE_TIMEOUT_MS = 4000
/** Ana pencere bundan uzun sürede yüklenmezse yine de gösterilir. */
const MAIN_TIMEOUT_MS = 15_000
/** Çubuğun %100'e dolma süresi (renderer'daki CSS geçişi 450ms) + küçük pay. */
const FILL_MS = 500
/** Açılış penceresinin sönme süresi (splash.css fade-out 220ms) + küçük pay. */
const FADE_MS = 250

const delay = (ms: number): Promise<void> => new Promise((r) => setTimeout(r, ms))
const withTimeout = <T>(promise: Promise<T>, ms: number): Promise<T | null> =>
  Promise.race([promise, delay(ms).then(() => null)])

/** İlk güncelleme denetiminin sonucu → açılış penceresinde gösterilecek aşama. */
function stageOf(update: UpdateStatus | null, justUpdated: boolean): SplashStage {
  if (update?.state === 'available' || update?.state === 'downloading') return 'downloading'
  // Az önce güncellendiyse "güncel" / "hazır" yerine "Güncellendi" yazar
  if (justUpdated) return 'updated'
  if (update?.state === 'none') return 'upToDate'
  // Denetim kapalı (geliştirme), başarısız (ağ yok / yayın yok) ya da zaman aşımı
  return 'ready'
}

/** Saydam, çerçevesiz açılış penceresi: masaüstünün üstünde yalnızca logo, yazı ve çubuk görünür. */
function createSplashWindow(language: string): BrowserWindow {
  const win = new BrowserWindow({
    width: 520,
    height: 320,
    frame: false,
    transparent: true,
    backgroundColor: '#00000000',
    hasShadow: false,
    resizable: false,
    maximizable: false,
    minimizable: false,
    fullscreenable: false,
    center: true,
    show: false,
    title: 'Cevherclient',
    webPreferences: {
      preload: join(__dirname, '../preload/index.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      spellcheck: false
    }
  })
  if (process.env.ELECTRON_RENDERER_URL) {
    void win.loadURL(`${process.env.ELECTRON_RENDERER_URL}/splash.html?lang=${encodeURIComponent(language)}`)
  } else {
    void win.loadFile(join(__dirname, '../renderer/splash.html'), { query: { lang: language } })
  }
  return win
}

/**
 * Açılış akışı: önce logo + çubuk gelir; güncelleme denetlenir ve ana pencere arkada yüklenir.
 * İkisi de bitince çubuk dolar, ana pencere görünür ve açılış penceresi üstünde sönerek kapanır.
 * Bir şey ters giderse ana pencere yine de gösterilir.
 */
export async function runStartup(options: {
  main: BrowserWindow
  mainReady: Promise<void>
  language: string
  updateCheck: Promise<UpdateStatus | null>
  /** Önceki açılıştan beri sürüm değiştiyse true (güncelleme kurulup yeniden açıldı). */
  justUpdated: boolean
}): Promise<void> {
  const { main, mainReady, language, updateCheck, justUpdated } = options
  const splash = createSplashWindow(language)
  const send = (stage: SplashStage, progress: number, closing = false): void => {
    if (!splash.isDestroyed()) splash.webContents.send('splash:status', { stage, progress, closing })
  }

  try {
    await withTimeout(new Promise<void>((r) => splash.once('ready-to-show', () => r())), 5000)
    if (!splash.isDestroyed()) splash.show()
    const shownAt = Date.now()

    send('starting', 0.12)
    await delay(250)
    send('checking', 0.4)
    const stage = stageOf(await withTimeout(updateCheck, UPDATE_TIMEOUT_MS), justUpdated)
    send(stage, 0.72)

    await withTimeout(mainReady, MAIN_TIMEOUT_MS)
    await delay(Math.max(0, MIN_VISIBLE_MS - (Date.now() - shownAt)))
    send(stage, 1)
    await delay(FILL_MS)

    // Açılış penceresi ana pencerenin üstünde sönsün
    if (!splash.isDestroyed()) splash.setAlwaysOnTop(true)
    if (!main.isDestroyed()) main.show()
    send(stage, 1, true)
    await delay(FADE_MS)
  } finally {
    if (!splash.isDestroyed()) splash.destroy()
    if (!main.isDestroyed() && !main.isVisible()) main.show()
  }
}
