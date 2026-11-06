import { rm } from 'node:fs/promises'
import { join } from 'node:path'

/**
 * GIF Cape modu (mod kimliği gifcape) launcher'dan kaldırıldı: oyuncunun kendi pelerinini GIF yapıyordu ve
 * istemci modunun pelerin kozmetiğinin üstüne yazıyordu. Pelerinler artık cevher_client'ın Kozmetikler
 * sayfasında. Eski sürümlerin instance'a kopyaladığı jar burada silinir; config/gifcape klasörüne dokunulmaz.
 */

/** Eski sürümlerin instance'ın mods/ klasörüne koyduğu sabit dosya adı. */
const INSTALLED_NAME = 'gifcape.jar'

export async function removeGifCapeMod(gamePath: string): Promise<void> {
  await rm(join(gamePath, 'mods', INSTALLED_NAME), { force: true })
}
