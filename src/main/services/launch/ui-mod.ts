import { app } from 'electron'
import { copyFile, mkdir, rm } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { join } from 'node:path'
import type { ServerProfile } from '@shared/types'

/**
 * Cevherclient UI modu (SAO arayüzü, mod kimliği cevher_ui): sunucudaki CevherUIBridge
 * eklentisi izin verdiğinde SAO tarzı arayüzü açar; izin yoksa oyun vanilla davranır.
 * Yalnızca Fabric/Quilt profillerinde çalışır ve Fabric API ister.
 *
 * Mod Minecraft sürümüne bağlıdır; her sürüm için ayrı jar
 * resources/mods/cevher-ui-<mc>.jar olarak eklenir.
 */

/** Instance'ın mods/ klasöründeki sabit dosya adı (modpack senkronu bu dosyaya dokunmaz). */
const INSTALLED_NAME = 'cevherclient-ui.jar'

function bundledModsDir(): string {
  return app.isPackaged ? join(process.resourcesPath, 'mods') : join(app.getAppPath(), 'resources', 'mods')
}

/**
 * Sunucu profili için UI modunu instance'a yerleştirir. Uygun jar yoksa (vanilla sunucu ya da
 * desteklenmeyen sürüm) eski kopyayı kaldırır; oyun modsuz açılır.
 */
export async function prepareUiMod(server: ServerProfile, gamePath: string): Promise<void> {
  const modsDir = join(gamePath, 'mods')
  const target = join(modsDir, INSTALLED_NAME)
  const source = join(bundledModsDir(), `cevher-ui-${server.minecraftVersion}.jar`)
  const loaderOk = server.loader.type === 'fabric' || server.loader.type === 'quilt'

  if (!loaderOk || !existsSync(source)) {
    // Başka bir sürüm için kalmış jar oyunu çökertmesin.
    await rm(target, { force: true })
    console.warn(
      `[ui-mod] ${server.id}: ${loaderOk ? `Minecraft ${server.minecraftVersion} için jar yok` : `${server.loader.type} mod yükleyemez`}; ` +
        'SAO arayüzü yüklenmeyecek'
    )
    return
  }

  await mkdir(modsDir, { recursive: true })
  await copyFile(source, target)
}
