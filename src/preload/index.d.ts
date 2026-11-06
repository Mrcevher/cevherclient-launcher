import type { CevherApi } from '../shared/ipc'

declare global {
  interface Window {
    /** Preload'un açtığı tipli IPC API'si — renderer'ın main ile tek iletişim yolu. */
    cevher: CevherApi
  }
}

export {}
