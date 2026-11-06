import { app } from 'electron'
import { copyFile, mkdir, rm } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { join } from 'node:path'
import type { ServerProfile } from '@shared/types'

/**
 * Cevherclient oturum modu (game-mods/cevher-session): oyuncu sunucudan ayrılınca
 * (ESC → Bağlantıyı Kes, atılma, bağlantı kopması) oyunu ana menüye düşürmek yerine
 * kapatır. Yalnızca Fabric/Quilt profillerinde çalışabilir; vanilla'da mod yüklenemez.
 *
 * Mod Minecraft sürümüne bağlıdır; her sürüm için ayrı jar derlenip
 * resources/mods/cevher-session-<mc>.jar olarak eklenir (bkz. scripts/build-session-mod.mjs).
 */

/** Instance'ın mods/ klasöründeki sabit dosya adı (modpack senkronu bu dosyaya dokunmaz). */
const INSTALLED_NAME = 'cevherclient-session.jar'

/** Modu etkinleştiren JVM özelliği; başka launcher'larda mod hiçbir şey yapmaz. */
const ENABLE_ARG = '-Dcevherclient.exitOnLeave=true'

function bundledModsDir(): string {
  return app.isPackaged ? join(process.resourcesPath, 'mods') : join(app.getAppPath(), 'resources', 'mods')
}

/**
 * Sunucu profili için modu instance'a yerleştirir ve gereken JVM argümanlarını döndürür.
 * Uygun jar yoksa (vanilla sunucu ya da desteklenmeyen sürüm) eski kopyayı kaldırır ve
 * boş liste döner: oyun yine doğrudan sunucuya bağlanır, yalnızca çıkışta kapanmaz.
 */
export async function prepareSessionMod(server: ServerProfile, gamePath: string): Promise<string[]> {
  const modsDir = join(gamePath, 'mods')
  const target = join(modsDir, INSTALLED_NAME)
  const source = join(bundledModsDir(), `cevher-session-${server.minecraftVersion}.jar`)
  const loaderOk = server.loader.type === 'fabric' || server.loader.type === 'quilt'

  if (!loaderOk || !existsSync(source)) {
    // Başka bir sürüm için kalmış jar oyunu çökertmesin.
    await rm(target, { force: true })
    console.warn(
      `[session-mod] ${server.id}: ${loaderOk ? `Minecraft ${server.minecraftVersion} için jar yok` : `${server.loader.type} mod yükleyemez`}; ` +
        'oyun sunucudan çıkınca kapanmayacak'
    )
    return []
  }

  await mkdir(modsDir, { recursive: true })
  await copyFile(source, target)
  return [ENABLE_ARG]
}
