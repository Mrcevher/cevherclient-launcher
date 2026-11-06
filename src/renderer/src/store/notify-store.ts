import { create } from 'zustand'
import type { UserFacingError } from '../lib/errors'

export type NoticeKind = 'info' | 'success' | 'warning' | 'error'

export interface Notice {
  id: number
  kind: NoticeKind
  title: string
  message?: string
}

interface CriticalState {
  error: UserFacingError
  retry?: () => void
}

interface NotifyStore {
  notices: Notice[]
  critical: CriticalState | null
  /** SAO sistem mesajı tarzı pop-up gösterir; birkaç saniye sonra kendiliğinden kapanır. */
  push(notice: Omit<Notice, 'id'>, durationMs?: number): void
  dismiss(id: number): void
  showCritical(error: UserFacingError, retry?: () => void): void
  clearCritical(): void
}

let nextId = 1
const MAX_VISIBLE = 4

export const useNotifyStore = create<NotifyStore>((set, get) => ({
  notices: [],
  critical: null,

  push(notice, durationMs = 4500) {
    const id = nextId++
    set((s) => ({ notices: [...s.notices, { ...notice, id }].slice(-MAX_VISIBLE) }))
    setTimeout(() => get().dismiss(id), durationMs)
  },

  dismiss(id) {
    set((s) => ({ notices: s.notices.filter((n) => n.id !== id) }))
  },

  showCritical(error, retry) {
    set({ critical: { error, retry } })
  },

  clearCritical() {
    set({ critical: null })
  }
}))
