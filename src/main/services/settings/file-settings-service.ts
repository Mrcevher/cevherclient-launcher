import { totalmem } from 'node:os'
import { MIN_MEMORY_MB, maxMemoryFor } from '@shared/constants'
import { DEFAULT_SETTINGS, DEVICE_TIERS, LANGUAGES, THEME_IDS } from '@shared/types'
import type { Settings, SettingsPatch } from '@shared/types'
import { JsonStore } from '../../utils/json-store'
import type { SettingsService } from '../types'

const HEX_COLOR = /^#[0-9a-fA-F]{6}$/

function clampInt(value: unknown, min: number, max: number, fallback: number): number {
  const n = Math.round(Number(value))
  return Number.isFinite(n) ? Math.min(max, Math.max(min, n)) : fallback
}

/**
 * Bozuk/eksik/eski sürüm settings.json'u her zaman geçerli bir Settings'e dönüştürür.
 * Renderer'dan gelen yamalar da aynı süzgeçten geçer.
 */
export function sanitizeSettings(raw: unknown): Settings {
  const r = (typeof raw === 'object' && raw !== null ? raw : {}) as Partial<Record<keyof Settings, unknown>>
  const d = DEFAULT_SETTINGS
  const totalMb = Math.floor(totalmem() / 1024 / 1024)
  const res = (typeof r.resolution === 'object' && r.resolution !== null ? r.resolution : {}) as Record<string, unknown>

  return {
    javaPath: typeof r.javaPath === 'string' && r.javaPath.trim() ? r.javaPath.trim() : null,
    memoryMb: clampInt(r.memoryMb, MIN_MEMORY_MB, maxMemoryFor(totalMb), Math.min(d.memoryMb, maxMemoryFor(totalMb))),
    resolution: {
      width: clampInt(res.width, 640, 7680, d.resolution.width),
      height: clampInt(res.height, 480, 4320, d.resolution.height),
      fullscreen: typeof res.fullscreen === 'boolean' ? res.fullscreen : d.resolution.fullscreen
    },
    jvmArgs: typeof r.jvmArgs === 'string' ? r.jvmArgs.slice(0, 2000) : d.jvmArgs,
    theme: (THEME_IDS as readonly unknown[]).includes(r.theme) ? (r.theme as Settings['theme']) : d.theme,
    accentColor: typeof r.accentColor === 'string' && HEX_COLOR.test(r.accentColor) ? r.accentColor : null,
    language: (LANGUAGES as readonly unknown[]).includes(r.language) ? (r.language as Settings['language']) : d.language,
    selectedServerId: typeof r.selectedServerId === 'string' ? r.selectedServerId : null,
    remoteConfigUrl: typeof r.remoteConfigUrl === 'string' && r.remoteConfigUrl.trim() ? r.remoteConfigUrl.trim() : null,
    useFabric: typeof r.useFabric === 'boolean' ? r.useFabric : d.useFabric,
    perfPackAutoInstall: typeof r.perfPackAutoInstall === 'boolean' ? r.perfPackAutoInstall : d.perfPackAutoInstall,
    deviceTier: (DEVICE_TIERS as readonly unknown[]).includes(r.deviceTier) ? (r.deviceTier as Settings['deviceTier']) : null
  }
}

/** Ayarları userData/settings.json'da saklayan gerçek implementasyon. */
export class FileSettingsService implements SettingsService {
  private readonly store = new JsonStore<Settings>('settings.json', DEFAULT_SETTINGS, sanitizeSettings)

  get(): Promise<Settings> {
    return this.store.read()
  }

  async update(patch: SettingsPatch): Promise<Settings> {
    const current = await this.store.read()
    const next = sanitizeSettings({
      ...current,
      ...patch,
      resolution: { ...current.resolution, ...(patch.resolution ?? {}) }
    })
    await this.store.write(next)
    return next
  }
}
