// Uygulama ikonunu (build/icon.png, 512×512) CevherCraft logosundan üretir: logo saydam
// kenarlarından kırpılır ve saydam kare tuvalin ortasına sığdırılır. electron-builder
// Windows .ico / macOS .icns dosyalarını bu PNG'den kendisi oluşturur.
//
//   npm run build:icon
const { app, BrowserWindow } = require('electron')
const { mkdirSync, readFileSync, writeFileSync } = require('node:fs')
const { join } = require('node:path')

const SIZE = 512
/** Logonun tuval kenarına bıraktığı boşluk (px). */
const PADDING = 6
const LOGO = join(__dirname, '..', 'src', 'renderer', 'src', 'assets', 'cevhercraft-logo.png')

app.disableHardwareAcceleration()
app.whenReady().then(async () => {
  const win = new BrowserWindow({ show: false, webPreferences: { offscreen: true } })
  await win.loadURL('data:text/html,<canvas></canvas>')
  const logo = 'data:image/png;base64,' + readFileSync(LOGO).toString('base64')
  const png = await win.webContents.executeJavaScript(`new Promise((resolve, reject) => {
    const img = new Image()
    img.onerror = () => reject(new Error('logo okunamadı'))
    img.onload = () => {
      // Saydam olmayan piksellerin sınırları
      const src = document.createElement('canvas')
      src.width = img.width; src.height = img.height
      const sctx = src.getContext('2d')
      sctx.drawImage(img, 0, 0)
      const { data } = sctx.getImageData(0, 0, img.width, img.height)
      let minX = img.width, minY = img.height, maxX = -1, maxY = -1
      for (let y = 0; y < img.height; y++)
        for (let x = 0; x < img.width; x++)
          if (data[(y * img.width + x) * 4 + 3] > 8) {
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
          }
      const w = maxX - minX + 1, h = maxY - minY + 1
      const scale = Math.min((${SIZE} - 2 * ${PADDING}) / w, (${SIZE} - 2 * ${PADDING}) / h)
      const dw = w * scale, dh = h * scale

      const out = document.querySelector('canvas')
      out.width = ${SIZE}; out.height = ${SIZE}
      const ctx = out.getContext('2d')
      ctx.imageSmoothingEnabled = true
      ctx.imageSmoothingQuality = 'high'
      ctx.drawImage(img, minX, minY, w, h, (${SIZE} - dw) / 2, (${SIZE} - dh) / 2, dw, dh)
      resolve({ png: out.toDataURL('image/png'), crop: [minX, minY, w, h] })
    }
    img.src = ${JSON.stringify(logo)}
  })`)
  const out = join(__dirname, '..', 'build')
  mkdirSync(out, { recursive: true })
  writeFileSync(join(out, 'icon.png'), Buffer.from(png.png.split(',')[1], 'base64'))
  console.log(`✓ build/icon.png ${SIZE}×${SIZE} (logo kırpımı: ${png.crop.join(', ')})`)
  app.quit()
})
