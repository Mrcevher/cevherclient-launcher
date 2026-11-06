import { create } from 'zustand'
import type { AuthState } from '@shared/types'
import { call } from '../lib/api'
import { reportError } from '../lib/errors'

type SessionStatus = 'loading' | 'signedOut' | 'signedIn'

interface SessionStore {
  status: SessionStatus
  auth: AuthState | null
  /** Giriş isteği sürüyor mu (buton durumu için). */
  busy: boolean
  init(): Promise<void>
  /** Giriş: hesap sunucusu varsa şifreyle, yoksa yalnızca kullanıcı adıyla. */
  login(username: string, password?: string): Promise<void>
  /** Hesap sunucusunda yeni hesap açar ve giriş yapar. */
  register(username: string, password: string): Promise<void>
  logout(): Promise<void>
  /** Şifre değiştirir; başarılıysa true. */
  changePassword(currentPassword: string, newPassword: string): Promise<boolean>
}

function statusOf(auth: AuthState): SessionStatus {
  return auth.account ? 'signedIn' : 'signedOut'
}

export const useSessionStore = create<SessionStore>((set) => ({
  status: 'loading',
  auth: null,
  busy: false,

  async init() {
    try {
      const auth = await call('auth:getState')
      set({ auth, status: statusOf(auth) })
    } catch (err) {
      set({ status: 'signedOut' })
      reportError(err)
    }
  },

  async login(username, password) {
    set({ busy: true })
    try {
      const auth = await call('auth:login', username, password)
      set({ auth, status: statusOf(auth) })
    } catch (err) {
      reportError(err)
    } finally {
      set({ busy: false })
    }
  },

  async register(username, password) {
    set({ busy: true })
    try {
      const auth = await call('auth:register', username, password)
      set({ auth, status: statusOf(auth) })
    } catch (err) {
      reportError(err)
    } finally {
      set({ busy: false })
    }
  },

  async changePassword(currentPassword, newPassword) {
    try {
      const auth = await call('auth:changePassword', currentPassword, newPassword)
      set({ auth, status: statusOf(auth) })
      return true
    } catch (err) {
      reportError(err)
      return false
    }
  },

  async logout() {
    try {
      const auth = await call('auth:logout')
      set({ auth, status: statusOf(auth) })
    } catch (err) {
      reportError(err)
    }
  }
}))
