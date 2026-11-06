import { app } from 'electron'
import { readFile } from 'node:fs/promises'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { AppError } from '@shared/errors'
import { isSecureOrLoopbackUrl, parseRemoteConfig } from '@shared/config-schema'
import { compareVersions } from '@shared/utils'
import type { RemoteConfigSnapshot, ServerProfile } from '@shared/types'
import type { RemoteConfigService, SettingsService } from '../types'

/** Uygulamayla gelen mock config dosyası (Faz 1 varsayılanı). */
function bundledConfigPath(): string {
  return app.isPackaged
    ? join(process.resourcesPath, 'mock', 'remote-config.json')
    : join(app.getAppPath(), 'resources', 'mock', 'remote-config.json')
}

/**
 * Tek JSON dosyasından uzaktan config okur.
 * Kaynak önceliği: settings.remoteConfigUrl → CEVHER_CONFIG_URL → yerleşik mock dosya.
 * Kaynak http(s) URL, file:// URL veya düz dosya yolu olabilir.
 */
export class JsonRemoteConfigService implements RemoteConfigService {
  private cached: RemoteConfigSnapshot | null = null

  constructor(private readonly settings: SettingsService) {}

  async get(refresh = false): Promise<RemoteConfigSnapshot> {
    if (this.cached && !refresh) return this.cached

    const source = (await this.settings.get()).remoteConfigUrl ?? process.env.CEVHER_CONFIG_URL ?? bundledConfigPath()
    const raw = await this.load(source)
    const config = parseRemoteConfig(raw)

    this.cached = {
      config,
      source,
      loadedAt: new Date().toISOString(),
      launcherUpToDate: compareVersions(app.getVersion(), config.minLauncherVersion) >= 0
    }
    return this.cached
  }

  async getServer(serverId: string): Promise<ServerProfile> {
    const { config } = await this.get()
    const server = config.servers.find((s) => s.id === serverId)
    if (!server) throw new AppError('CONFIG_INVALID', `Bilinmeyen sunucu: ${serverId}`)
    return server
  }

  private async load(source: string): Promise<unknown> {
    if (/^https?:\/\//i.test(source)) {
      // Config sunucu adreslerini ve indirme kaynaklarını belirler: düz http yalnızca yerel makinede.
      if (!isSecureOrLoopbackUrl(source)) throw new AppError('CONFIG_INVALID', `${source}: https adresi bekleniyordu`)
      let response: Response
      try {
        response = await fetch(source, { signal: AbortSignal.timeout(8000), cache: 'no-store' })
      } catch (err) {
        throw new AppError('NO_INTERNET', `${source}: ${(err as Error).message}`)
      }
      if (!response.ok) throw new AppError('CONFIG_INVALID', `${source}: HTTP ${response.status}`)
      try {
        return await response.json()
      } catch {
        throw new AppError('CONFIG_INVALID', `${source}: geçersiz JSON`)
      }
    }

    const path = source.startsWith('file:') ? fileURLToPath(source) : source
    let text: string
    try {
      text = await readFile(path, 'utf8')
    } catch (err) {
      throw new AppError('CONFIG_INVALID', `${path}: ${(err as Error).message}`)
    }
    try {
      return JSON.parse(text)
    } catch {
      throw new AppError('CONFIG_INVALID', `${path}: geçersiz JSON`)
    }
  }
}
