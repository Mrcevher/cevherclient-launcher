import { join } from 'node:path'
import { pathToFileURL } from 'node:url'
import { BrowserWindow, ipcMain, type IpcMainInvokeEvent } from 'electron'
import { AppError, type IpcResult } from '@shared/errors'
import type { InvokeArgs, InvokeChannel, InvokeReturn } from '@shared/ipc'

export interface HandlerContext {
  /** İsteği gönderen pencere. */
  window: BrowserWindow | null
}

type Handler<C extends InvokeChannel> = (
  ctx: HandlerContext,
  ...args: InvokeArgs<C>
) => InvokeReturn<C> | Promise<InvokeReturn<C>>

const normalizeFileUrl = (href: string): string => (process.platform === 'win32' ? href.toLowerCase() : href)

/** Paketlenmiş uygulamanın kendi sayfaları (tam dosya adresi eşleşmesi). */
const APP_PAGES = new Set(
  ['index.html', 'splash.html'].map((page) => normalizeFileUrl(pathToFileURL(join(__dirname, '../renderer', page)).href))
)

/** Adres uygulamanın kendi sayfası mı (paketlenmişte tam dosya eşleşmesi, geliştirmede dev sunucu kökeni). */
export function isAppUrl(href: string): boolean {
  let url: URL
  try {
    url = new URL(href)
  } catch {
    return false
  }
  const devUrl = process.env.ELECTRON_RENDERER_URL
  if (devUrl && url.protocol.startsWith('http') && url.origin === new URL(devUrl).origin) return true
  if (url.protocol !== 'file:') return false
  url.search = ''
  url.hash = ''
  return APP_PAGES.has(normalizeFileUrl(url.href))
}

/** Yalnızca uygulamanın kendi sayfasının ana çerçevesinden gelen istekleri kabul et. */
function isTrustedSender(event: IpcMainInvokeEvent): boolean {
  const frame = event.senderFrame
  return !!frame && frame === event.sender.mainFrame && isAppUrl(frame.url)
}

/**
 * Tipli ipcMain.handle sarmalayıcısı. Handler'ın fırlattığı her hata AppError'a
 * dönüştürülüp IpcResult zarfıyla döndürülür; renderer'a asla ham hata sızmaz.
 */
export function handle<C extends InvokeChannel>(channel: C, handler: Handler<C>): void {
  ipcMain.handle(channel, async (event, ...args): Promise<IpcResult<InvokeReturn<C>>> => {
    if (!isTrustedSender(event)) {
      return { ok: false, error: { code: 'UNKNOWN', detail: 'Güvenilmeyen gönderici' } }
    }
    try {
      const ctx: HandlerContext = { window: BrowserWindow.fromWebContents(event.sender) }
      const data = await handler(ctx, ...(args as InvokeArgs<C>))
      return { ok: true, data }
    } catch (err) {
      const appError = AppError.from(err)
      console.error(`[ipc] ${channel} →`, appError.code, appError.detail ?? '')
      return { ok: false, error: appError.toJSON() }
    }
  })
}

/** Renderer'dan gelen argümanları çalışma zamanında doğrulamak için. */
export function assertString(value: unknown, name: string): asserts value is string {
  if (typeof value !== 'string' || value.length > 4096) {
    throw new AppError('UNKNOWN', `Geçersiz argüman: ${name}`)
  }
}
