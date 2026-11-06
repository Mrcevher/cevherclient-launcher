import { safeStorage } from 'electron'
import { randomUUID } from 'node:crypto'
import { AppError } from '@shared/errors'
import { USERNAME_PATTERN } from '@shared/constants'
import type { Account, AuthState } from '@shared/types'
import { JsonStore } from '../../utils/json-store'
import type { AuthService, LaunchCredentials, RemoteConfigService } from '../types'
import { offlineUuid } from './offline-uuid'

interface AuthFile {
  account: Account | null
  lastUsername: string | null
  /** Hesap sunucusu oturum belirteci, safeStorage ile şifreli (base64). */
  token?: string
}

/** CevherAuth eklentisinin hata kodları → launcher'daki AUTH_FAILED nedenleri (i18n anahtarı). */
const SERVER_ERRORS: Record<string, string> = {
  WRONG_CREDENTIALS: 'wrongCredentials',
  USERNAME_TAKEN: 'usernameTaken',
  INVALID_USERNAME: 'invalidUsername',
  WEAK_PASSWORD: 'weakPassword',
  PROTECTED_NAME: 'protectedName',
  TOO_MANY_ACCOUNTS: 'tooManyAccounts',
  TOO_MANY_ATTEMPTS: 'tooManyAttempts',
  SESSION_EXPIRED: 'sessionExpired'
}

/** CevherAuth mağaza köprüsünün hata kodları → STORE_FAILED nedenleri. */
const STORE_ERRORS: Record<string, string> = {
  SITE_NOT_LINKED: 'notLinked',
  INSUFFICIENT_BALANCE: 'insufficientBalance',
  OUT_OF_STOCK: 'outOfStock',
  PRODUCT_NOT_FOUND: 'productNotFound',
  PURCHASE_IN_PROGRESS: 'inProgress',
  TOO_MANY_ATTEMPTS: 'tooManyAttempts',
  STORE_DISABLED: 'disabled',
  STORE_UNAVAILABLE: 'unavailable',
  // Eski eklenti sürümü bu uç noktaları tanımıyor.
  NOT_FOUND: 'pluginOutdated'
}

function sanitize(raw: unknown): AuthFile {
  const r = (typeof raw === 'object' && raw !== null ? raw : {}) as Record<string, unknown>
  const account = r.account as Account | null | undefined
  const valid = !!account && account.type === 'offline' && typeof account.username === 'string' && USERNAME_PATTERN.test(account.username)
  return {
    account: valid ? account : null,
    lastUsername: typeof r.lastUsername === 'string' ? r.lastUsername : valid ? account.username : null,
    token: valid && typeof r.token === 'string' ? r.token : undefined
  }
}

function makeAccount(username: string): Account {
  return {
    id: `offline-${username.toLowerCase()}`,
    username,
    uuid: offlineUuid(username),
    type: 'offline',
    createdAt: new Date().toISOString()
  }
}

/**
 * Launcher hesabı.
 *
 * Config'de `authServerUrl` varsa oyuncu kullanıcı adı + şifreyle sunucudaki CevherAuth
 * eklentisinde kayıt olur / giriş yapar. Dönen oturum belirteci safeStorage ile şifrelenip
 * saklanır; şifre hiçbir yere yazılmaz. Oyna'ya basınca bu oturumla tek kullanımlık bir giriş
 * bileti alınır ve oyuncu oyunda şifre yazmadan sunucuya girer.
 *
 * `authServerUrl` yoksa yalnızca kullanıcı adı istenir (lisanssız, korumasız).
 * UUID her iki durumda da Minecraft'ın offline türetmesidir (OfflinePlayer:<isim>).
 */
export class AccountAuthService implements AuthService {
  private readonly store = new JsonStore<AuthFile>('auth.json', { account: null, lastUsername: null }, sanitize)
  /** safeStorage yoksa belirteç yalnızca bu oturum boyunca bellekte tutulur. */
  private memoryToken: string | null = null

  constructor(private readonly config: RemoteConfigService) {}

  async getState(): Promise<AuthState> {
    const file = await this.store.read()
    const passwordRequired = !!(await this.authUrl())
    // Hesap sunucusu varken belirteci olmayan (ör. eski sürümden kalan) oturum geçersizdir.
    const account = passwordRequired && !this.readToken(file) ? null : file.account
    return { account, lastUsername: file.lastUsername, passwordRequired }
  }

  async requireAccount(): Promise<Account> {
    const { account } = await this.getState()
    if (!account) throw new AppError('AUTH_FAILED', 'Oturum açılmamış', { reason: 'sessionExpired' })
    return account
  }

  async getLaunchCredentials(): Promise<LaunchCredentials> {
    const account = await this.requireAccount()
    // Lisanssız sunucuda Minecraft belirteci kullanılmaz; zorunlu argüman olduğu için rastgele.
    return { account, accessToken: randomUUID().replace(/-/g, ''), userType: 'legacy' }
  }

  async prepareServerJoin(): Promise<void> {
    if (!(await this.authUrl())) return
    const file = await this.store.read()
    const token = this.readToken(file)
    if (!file.account || !token) {
      await this.clear()
      throw new AppError('AUTH_FAILED', 'Oturum belirteci yok', { reason: 'sessionExpired' })
    }
    try {
      await this.api('/api/join', { token })
    } catch (err) {
      if (err instanceof AppError && err.meta?.reason === 'sessionExpired') await this.clear()
      throw err
    }
  }

  async login(username: string, password?: string): Promise<AuthState> {
    const name = this.checkName(username)
    if (!(await this.authUrl())) {
      await this.store.write({ account: makeAccount(name), lastUsername: name })
      return this.getState()
    }
    if (!password) throw new AppError('AUTH_FAILED', 'Şifre boş', { reason: 'wrongCredentials' })
    const res = await this.api('/api/login', { username: name, password })
    return this.saveSession(res)
  }

  async register(username: string, password: string): Promise<AuthState> {
    const name = this.checkName(username)
    if (!(await this.authUrl())) return this.login(name)
    const res = await this.api('/api/register', { username: name, password })
    return this.saveSession(res)
  }

  async changePassword(currentPassword: string, newPassword: string): Promise<AuthState> {
    if (!(await this.authUrl())) throw new AppError('AUTH_FAILED', 'Hesap sunucusu yok', { reason: 'serverUnreachable' })
    const token = this.readToken(await this.store.read())
    if (!token) {
      await this.clear()
      throw new AppError('AUTH_FAILED', 'Oturum belirteci yok', { reason: 'sessionExpired' })
    }
    try {
      // Sunucu diğer oturumları kapatır ve bu cihaz için yeni belirteç verir.
      return await this.saveSession(await this.api('/api/password', { token, currentPassword, newPassword }))
    } catch (err) {
      if (err instanceof AppError && err.meta?.reason === 'sessionExpired') await this.clear()
      throw err
    }
  }

  async logout(): Promise<AuthState> {
    const file = await this.store.read()
    const token = this.readToken(file)
    if (token && (await this.authUrl())) {
      // Sunucudaki oturumu da kapat; sunucuya ulaşılamasa bile yerelde çıkış yapılır.
      await this.api('/api/logout', { token }).catch(() => undefined)
    }
    await this.clear()
    return this.getState()
  }

  async siteRequest(path: string, body: Record<string, string> = {}): Promise<Record<string, unknown>> {
    if (!(await this.authUrl())) throw new AppError('STORE_FAILED', 'authServerUrl tanımlı değil', { reason: 'disabled' })
    const token = this.readToken(await this.store.read())
    if (!token) {
      await this.clear()
      throw new AppError('AUTH_FAILED', 'Oturum belirteci yok', { reason: 'sessionExpired' })
    }
    try {
      return await this.api(path, { ...body, token }, 'STORE_FAILED')
    } catch (err) {
      if (err instanceof AppError && err.code === 'STORE_FAILED' && err.meta?.reason === 'sessionExpired') {
        await this.clear()
        throw new AppError('AUTH_FAILED', String(err.detail), { reason: 'sessionExpired' })
      }
      throw err
    }
  }

  // ── iç işleyiş ──────────────────────────────────────────────────────────────

  private checkName(username: string): string {
    const name = username.trim()
    if (!USERNAME_PATTERN.test(name)) {
      throw new AppError('AUTH_FAILED', `Geçersiz kullanıcı adı: ${name}`, { reason: 'invalidUsername' })
    }
    return name
  }

  private async saveSession(res: Record<string, unknown>): Promise<AuthState> {
    const username = typeof res.username === 'string' ? res.username : ''
    const token = typeof res.token === 'string' ? res.token : ''
    if (!USERNAME_PATTERN.test(username) || !token) {
      throw new AppError('AUTH_FAILED', 'Hesap sunucusundan geçersiz yanıt', { reason: 'serverUnreachable' })
    }
    // Sunucudaki yazım esas alınır ("arda" ile girilse de hesap "Arda" ise Arda olarak oynanır).
    await this.store.write({ account: makeAccount(username), lastUsername: username, token: this.encryptToken(token) })
    return this.getState()
  }

  private async clear(): Promise<void> {
    const { lastUsername } = await this.store.read()
    this.memoryToken = null
    await this.store.write({ account: null, lastUsername })
  }

  private encryptToken(token: string): string | undefined {
    if (safeStorage.isEncryptionAvailable()) return safeStorage.encryptString(token).toString('base64')
    this.memoryToken = token
    return undefined
  }

  private readToken(file: AuthFile): string | null {
    if (file.token && safeStorage.isEncryptionAvailable()) {
      try {
        return safeStorage.decryptString(Buffer.from(file.token, 'base64'))
      } catch {
        return null
      }
    }
    return this.memoryToken
  }

  private async authUrl(): Promise<string | undefined> {
    try {
      return (await this.config.get()).config.authServerUrl?.replace(/\/+$/, '')
    } catch {
      return undefined
    }
  }

  /** CevherAuth API çağrısı; hata kodunu AUTH_FAILED + i18n nedenine çevirir. */
  private async api(
    path: string,
    body: Record<string, string>,
    errorCode: 'AUTH_FAILED' | 'STORE_FAILED' = 'AUTH_FAILED'
  ): Promise<Record<string, unknown>> {
    const base = await this.authUrl()
    if (!base) throw new AppError(errorCode, 'authServerUrl tanımlı değil', { reason: 'serverUnreachable' })
    const reasons = errorCode === 'STORE_FAILED' ? { ...SERVER_ERRORS, ...STORE_ERRORS } : SERVER_ERRORS

    let response: Response
    try {
      response = await fetch(base + path, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
        // Satın alma sunucuda iki site isteği yapar; daha uzun beklenir.
        signal: AbortSignal.timeout(errorCode === 'STORE_FAILED' ? 30_000 : 10_000)
      })
    } catch (err) {
      throw new AppError(errorCode, `${base}: ${(err as Error).message}`, { reason: 'serverUnreachable' })
    }

    let json: Record<string, unknown>
    try {
      json = (await response.json()) as Record<string, unknown>
    } catch {
      throw new AppError(errorCode, `${base}${path}: HTTP ${response.status}, geçersiz yanıt`, { reason: 'serverUnreachable' })
    }
    if (json.ok !== true) {
      const code = typeof json.error === 'string' ? json.error : `HTTP ${response.status}`
      throw new AppError(errorCode, `${path}: ${code}`, { reason: reasons[code] ?? 'serverError' })
    }
    return json
  }
}
