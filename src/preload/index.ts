/**
 * Preload köprüsü. Renderer, Node/Electron API'lerine doğrudan erişemez; yalnızca
 * burada window.cevher olarak açılan, beyaz listeli ve tipli IPC API'sini kullanır.
 */

import { contextBridge, ipcRenderer, type IpcRendererEvent } from 'electron'
import { EVENT_CHANNELS, INVOKE_CHANNELS, type CevherApi } from '@shared/ipc'

const invokeAllowed = new Set<string>(INVOKE_CHANNELS)
const eventAllowed = new Set<string>(EVENT_CHANNELS)

const api: CevherApi = {
  invoke(channel, ...args) {
    if (!invokeAllowed.has(channel)) {
      return Promise.resolve({ ok: false, error: { code: 'UNKNOWN', detail: `İzin verilmeyen kanal: ${channel}` } })
    }
    return ipcRenderer.invoke(channel, ...args)
  },

  on(event, listener) {
    if (!eventAllowed.has(event)) throw new Error(`İzin verilmeyen olay: ${event}`)
    // IpcRendererEvent renderer'a sızdırılmaz; yalnızca yük iletilir.
    const wrapped = (_e: IpcRendererEvent, payload: unknown): void => listener(payload as never)
    ipcRenderer.on(event, wrapped)
    return () => {
      ipcRenderer.removeListener(event, wrapped)
    }
  },

  platform: process.platform
}

contextBridge.exposeInMainWorld('cevher', api)
