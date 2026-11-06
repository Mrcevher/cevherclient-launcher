import { app } from 'electron'
import { copyFile, mkdir, rm } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { join } from 'node:path'
import type { ServerProfile } from '@shared/types'

/**
 * Cevherclient istemci modu (mod kimliği cevher_client): Sağ Shift ile açılan mod menüsü,
 * HUD modülleri (FPS, CPS, tuş vuruşları…) ve HUD düzenleyici. Yalnızca istemci tarafında çalışır;
 * sunucuya bir şey gerekmez. Ayarlar instances/<id>/config/cevherclient/client.json içinde durur,
 * launcher ona dokunmaz. Yalnızca Fabric/Quilt profillerinde çalışır ve Fabric API ister.
 *
 * Mod Minecraft sürümüne bağlıdır; her sürüm için ayrı jar
 * resources/mods/cevher-client-<mc>.jar olarak eklenir (kaynak: game-mods/cevher-client,
 * derleme: npm run build:client-mod).
 */

/** Instance'ın mods/ klasöründeki sabit dosya adı (eski sürümün üzerine yazılır). */
const INSTALLED_NAME = 'cevherclient-client.jar'

function bundledModsDir(): string {
  return app.isPackaged ? join(process.resourcesPath, 'mods') : join(app.getAppPath(), 'resources', 'mods')
}

/**
 * Sunucu profili için istemci modunu instance'a yerleştirir. Uygun jar yoksa (vanilla sunucu ya da
 * desteklenmeyen sürüm) kopyayı kaldırır; oyun modsuz açılır.
 */
export async function prepareClientMod(server: ServerProfile, gamePath: string): Promise<void> {
  const modsDir = join(gamePath, 'mods')
  const target = join(modsDir, INSTALLED_NAME)
  const source = join(bundledModsDir(), `cevher-client-${server.minecraftVersion}.jar`)
  const loaderOk = server.loader.type === 'fabric' || server.loader.type === 'quilt'

  if (!loaderOk || !existsSync(source)) {
    // Başka bir sürüm için kalmış jar oyunu çökertmesin.
    await rm(target, { force: true })
    console.warn(
      `[client-mod] ${server.id}: ${loaderOk ? `Minecraft ${server.minecraftVersion} için jar yok` : `${server.loader.type} mod yükleyemez`}; ` +
        'mod menüsü yüklenmeyecek'
    )
    return
  }

  await mkdir(modsDir, { recursive: true })
  await copyFile(source, target)
}
