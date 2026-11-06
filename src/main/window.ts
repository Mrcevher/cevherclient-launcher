import { BrowserWindow, shell } from 'electron'
import { join } from 'node:path'
import { isAppUrl } from './ipc/handle'

/** Açık ana pencere; açılış penceresi gibi diğer pencerelerle karışmasın diye ayrıca tutulur. */
let mainWindow: BrowserWindow | null = null

/**
 * Ana pencere: çerçevesiz, özel başlık çubuğu renderer'da çizilir. showWhenReady false ise
 * yüklenince kendiliğinden görünmez; açılış akışı (splash.ts) çubuk dolunca gösterir.
 */
export function createMainWindow({ showWhenReady = true }: { showWhenReady?: boolean } = {}): BrowserWindow {
  const win = new BrowserWindow({
    width: 1280,
    height: 760,
    minWidth: 1100,
    minHeight: 680,
    frame: false,
    // Launcher sabit boyutlu bir pencere: büyütme (başlığa çift tıklama, Win+↑) ve tam ekran kapalı.
    maximizable: false,
    fullscreenable: false,
    show: false,
    backgroundColor: '#05080f',
    title: 'Cevherclient',
    webPreferences: {
      preload: join(__dirname, '../preload/index.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webSecurity: true,
      spellcheck: false
    }
  })

  if (showWhenReady) win.once('ready-to-show', () => win.show())
  mainWindow = win
  win.on('closed', () => {
    if (mainWindow === win) mainWindow = null
  })

  // Çerçevesiz pencerede bazı sistem kısayolları yine de büyütebilir/tam ekran yapabilir: geri al.
  win.on('maximize', () => win.unmaximize())
  win.on('enter-full-screen', () => win.setFullScreen(false))

  // Yeni pencere açılmasın; web bağlantıları sistem tarayıcısında açılsın.
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:\/\//i.test(url)) void shell.openExternal(url)
    return { action: 'deny' }
  })

  // Uygulama sayfasının dışına gezinmeyi engelle.
  win.webContents.on('will-navigate', (event, url) => {
    if (!isAppUrl(url)) event.preventDefault()
  })

  if (process.env.ELECTRON_RENDERER_URL) {
    void win.loadURL(process.env.ELECTRON_RENDERER_URL)
  } else {
    void win.loadFile(join(__dirname, '../renderer/index.html'))
  }
  return win
}

/**
 * Gizlenmiş ya da simge durumundaki ana pencereyi geri getirip öne alır: oyun kapanınca
 * (launch:exit) ve launcher ikinci kez açılmak istendiğinde.
 */
export function showMainWindow(): void {
  const win = mainWindow
  if (!win || win.isDestroyed()) return
  if (win.isMinimized()) win.restore()
  win.show()
  win.focus()
}
