import { BrowserWindow } from 'electron'
import type { EventChannel, IpcEventContract } from '@shared/ipc'

/** Servislerin renderer'a olay göndermek için kullandığı tipli arayüz. */
export interface EventSink {
  emit<E extends EventChannel>(event: E, payload: IpcEventContract[E]): void
}

/** Tüm açık pencerelere yayın yapan varsayılan implementasyon. */
export const windowEventSink: EventSink = {
  emit(event, payload) {
    for (const win of BrowserWindow.getAllWindows()) {
      if (!win.isDestroyed()) win.webContents.send(event, payload)
    }
  }
}
