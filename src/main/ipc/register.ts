import { app, dialog, shell } from 'electron'
import { totalmem } from 'node:os'
import { AppError } from '@shared/errors'
import { DEVICE_TIERS, LOADER_TYPES } from '@shared/types'
import type { LaunchTarget, LoaderType, SettingsPatch } from '@shared/types'
import { requiredJavaMajor } from '@shared/utils'
import type { Services } from '../services/types'
import type { LauncherUpdater } from '../updater'
import { windowEventSink } from '../utils/events'
import { assertString, handle } from './handle'

/** Tüm IPC kanallarını servis kabına bağlar. Handler'lar ince tutulur: iş servislerde. */
export function registerIpc(services: Services, updater: LauncherUpdater): void {
  const { auth, site, config, status, versions, install, java, settings, launch, performance, userMods, quality } = services

  // ── Pencere / sistem ──────────────────────────────────────────────────────
  handle('window:minimize', ({ window }) => window?.minimize())
  handle('window:close', ({ window }) => window?.close())

  handle('system:getInfo', () => ({
    platform: process.platform,
    arch: process.arch,
    totalMemoryMb: Math.floor(totalmem() / 1024 / 1024),
    launcherVersion: app.getVersion(),
    isDev: !app.isPackaged
  }))

  handle('shell:openExternal', async (_ctx, url) => {
    assertString(url, 'url')
    // Yalnızca web bağlantıları dışarıda açılır.
    if (!/^https?:\/\//i.test(url)) throw new AppError('UNKNOWN', `İzin verilmeyen bağlantı: ${url}`)
    await shell.openExternal(url)
  })

  handle('dialog:pickJava', async ({ window }) => {
    const options: Electron.OpenDialogOptions = {
      properties: ['openFile'],
      filters:
        process.platform === 'win32'
          ? [{ name: 'Java', extensions: ['exe'] }]
          : [{ name: 'Java', extensions: ['*'] }]
    }
    const result = window ? await dialog.showOpenDialog(window, options) : await dialog.showOpenDialog(options)
    return result.canceled ? null : (result.filePaths[0] ?? null)
  })

  // ── Auth ──────────────────────────────────────────────────────────────────
  handle('auth:getState', () => auth.getState())
  handle('auth:login', (_ctx, username, password) => {
    assertString(username, 'username')
    if (password !== undefined) assertString(password, 'password')
    return auth.login(username, password)
  })
  handle('auth:register', (_ctx, username, password) => {
    assertString(username, 'username')
    assertString(password, 'password')
    return auth.register(username, password)
  })
  handle('auth:logout', () => auth.logout())
  handle('auth:changePassword', (_ctx, current, next) => {
    assertString(current, 'currentPassword')
    assertString(next, 'newPassword')
    return auth.changePassword(current, next)
  })

  // ── Web sitesi (Mağaza / Profil / Haberler) ───────────────────────────────
  handle('site:profile', () => site.profile())
  handle('store:listing', () => site.listing())
  handle('store:buy', (_ctx, productId) => {
    assertString(productId, 'productId')
    return site.buy(productId)
  })
  handle('site:fetchPage', (_ctx, url) => {
    assertString(url, 'url')
    return site.fetchPage(url)
  })

  // ── Performans Paketi ─────────────────────────────────────────────────────
  handle('mods:getPack', async (_ctx, serverId) => {
    assertString(serverId, 'serverId')
    return performance.getState(await config.getServer(serverId))
  })
  handle('mods:installPack', async (_ctx, serverId) => {
    assertString(serverId, 'serverId')
    return performance.install(await config.getServer(serverId), (progress) =>
      windowEventSink.emit('mods:progress', { serverId, progress })
    )
  })
  handle('mods:search', async (_ctx, serverId, query, offset, kind) => {
    assertString(serverId, 'serverId')
    assertString(query, 'query')
    return userMods.search(await config.getServer(serverId), query, Number(offset) || 0, kind === 'shader' ? 'shader' : 'mod')
  })
  handle('mods:listUser', async (_ctx, serverId) => {
    assertString(serverId, 'serverId')
    return userMods.list(await config.getServer(serverId))
  })
  handle('mods:installUser', async (_ctx, serverId, projectId, kind) => {
    assertString(serverId, 'serverId')
    assertString(projectId, 'projectId')
    return userMods.install(await config.getServer(serverId), projectId, kind === 'shader' ? 'shader' : 'mod')
  })
  handle('mods:removeUser', async (_ctx, serverId, projectId) => {
    assertString(serverId, 'serverId')
    assertString(projectId, 'projectId')
    return userMods.remove(await config.getServer(serverId), projectId)
  })
  handle('mods:removePack', async (_ctx, serverId) => {
    assertString(serverId, 'serverId')
    return performance.remove(await config.getServer(serverId))
  })

  // ── Cihaz kalitesi ────────────────────────────────────────────────────────
  handle('quality:apply', async (_ctx, serverId, tier, remember) => {
    assertString(serverId, 'serverId')
    if (!(DEVICE_TIERS as readonly unknown[]).includes(tier)) throw new AppError('UNKNOWN', `Bilinmeyen kalite: ${String(tier)}`)
    return quality.apply(
      await config.getServer(serverId),
      tier,
      (progress) => windowEventSink.emit('mods:progress', { serverId, progress }),
      remember !== false
    )
  })

  // ── Config / durum / sürümler ─────────────────────────────────────────────
  handle('config:get', (_ctx, refresh) => config.get(refresh === true))

  handle('status:get', async (_ctx, serverId) => {
    assertString(serverId, 'serverId')
    return status.getStatus(await config.getServer(serverId))
  })

  handle('versions:list', (_ctx, loader) => {
    if (!(LOADER_TYPES as readonly string[]).includes(loader)) throw new AppError('UNKNOWN', `Bilinmeyen loader: ${loader}`)
    return versions.list(loader as LoaderType)
  })

  // ── Kurulum ───────────────────────────────────────────────────────────────
  handle('install:start', (_ctx, key) => {
    assertString(key, 'versionKey')
    return install.start(key)
  })
  handle('install:pause', (_ctx, id) => {
    assertString(id, 'taskId')
    return install.pause(id)
  })
  handle('install:resume', (_ctx, id) => {
    assertString(id, 'taskId')
    return install.resume(id)
  })
  handle('install:cancel', (_ctx, id) => {
    assertString(id, 'taskId')
    return install.cancel(id)
  })
  handle('install:listTasks', () => install.listTasks())
  handle('install:listInstalled', () => install.listInstalled())

  // ── Java ──────────────────────────────────────────────────────────────────
  handle('java:detect', () => java.detect())
  handle('java:getActive', async (_ctx, mcVersion) => {
    assertString(mcVersion, 'minecraftVersion')
    try {
      return await java.resolve(requiredJavaMajor(mcVersion))
    } catch {
      return null
    }
  })

  // ── Ayarlar ───────────────────────────────────────────────────────────────
  handle('settings:get', () => settings.get())
  handle('settings:update', (_ctx, patch: SettingsPatch) => {
    if (typeof patch !== 'object' || patch === null) throw new AppError('UNKNOWN', 'Geçersiz ayar yaması')
    return settings.update(patch)
  })

  // ── Başlatma ──────────────────────────────────────────────────────────────
  handle('launch:start', async ({ window }, target: LaunchTarget) => {
    if (target?.kind === 'server') assertString(target.serverId, 'serverId')
    else if (target?.kind === 'free') assertString(target.versionKey, 'versionKey')
    else throw new AppError('UNKNOWN', 'Geçersiz başlatma hedefi')
    const session = await launch.launch(target)
    // Oyun penceresi açıldı: launcher gizlenir, oyun kapanınca (launch:exit) geri gelir.
    window?.hide()
    return session
  })
  handle('launch:history', () => launch.getHistory())

  // ── Launcher güncellemesi ─────────────────────────────────────────────────
  handle('update:getStatus', () => updater.getStatus())
  handle('update:install', () => updater.install())
}
