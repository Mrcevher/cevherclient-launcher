import { app } from 'electron'
import { copyFile, mkdir, rm, writeFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { join } from 'node:path'
import type { ServerProfile } from '@shared/types'

/**
 * Cevherclient oturum modu (game-mods/cevher-session): oyuncu sunucudan ayrılınca
 * (ESC → Bağlantıyı Kes, atılma, bağlantı kopması) oyunu ana menüye düşürmek yerine
 * kapatır ve bağlanırken CevherAuth'a giriş biletinin kanıtını gönderir (bkz. writeJoinTicket).
 * Yalnızca Fabric/Quilt profillerinde çalışabilir; vanilla'da mod yüklenemez.
 *
 * Mod Minecraft sürümüne bağlıdır; her sürüm için ayrı jar derlenip
 * resources/mods/cevher-session-<mc>.jar olarak eklenir (bkz. scripts/build-session-mod.mjs).
 */

/** Instance'ın mods/ klasöründeki sabit dosya adı (modpack senkronu bu dosyaya dokunmaz). */
const INSTALLED_NAME = 'cevherclient-session.jar'

/** Modu etkinleştiren JVM özelliği; başka launcher'larda mod hiçbir şey yapmaz. */
const ENABLE_ARG = '-Dcevherclient.exitOnLeave=true'

/**
 * Bilet sırrının oyuna teslim edildiği dosya (instance/.cevher/, modpack override'ları buraya
 * yazamaz). Oyun açılırken mod okuyup hemen siler; launcher da oyun kapanınca siler. Sır komut
 * satırında (JVM argümanı) taşınmaz, çünkü süreç listesinden diğer kullanıcılar görebilir.
 * Biçim game-mods/cevher-session/.../JoinProof.java ile aynı kalmalı.
 */
const JOIN_TICKET_FILE = join('.cevher', 'join-ticket.json')

/** Oyunun, bileti kendisinin aldığını sunucuya kanıtlaması için sırrı instance'a yazar. */
export async function writeJoinTicket(gamePath: string, username: string, secret: string): Promise<void> {
  const file = join(gamePath, JOIN_TICKET_FILE)
  await mkdir(join(gamePath, '.cevher'), { recursive: true })
  await rm(file, { force: true })
  // flag 'wx': var olan bir dosyanın (ör. bağlantı) üzerine yazılmaz; POSIX'te yalnızca kullanıcı okuyabilir.
  await writeFile(file, JSON.stringify({ v: 1, username, secret }), { encoding: 'utf8', mode: 0o600, flag: 'wx' })
}

/** Oyun kapanınca (veya açılamazsa) mod okumadan kalmış sırrı siler. */
export async function clearJoinTicket(gamePath: string): Promise<void> {
  await rm(join(gamePath, JOIN_TICKET_FILE), { force: true }).catch(() => undefined)
}

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
