import { readdir, rm } from 'node:fs/promises'
import { join } from 'node:path'
import { open, readEntry, walkEntriesGenerator } from '@xmcl/unzip'

/**
 * Oyuncunun oyun klasöründe hiç bulunmaması gereken modlar. Her açılışta mods/ klasörü taranır ve bunlar
 * silinir: kayıtta olsun olmasın, dosya adı ne olursa olsun (jar'daki fabric.mod.json kimliğine bakılır).
 *
 * Krypton: sıkıştırılmış ağ paketlerini kendi çözücüsüyle açıyor ve Velocity arkasında büyük chunk
 * paketlerinde "Received a deflate stream that was too large" / "invalid distance too far back" ile
 * bağlantıyı koparıyor. 4.8.17 onu paketten çıkarırken dosyayı silmeden kayıttan düşürdüğü için kayda bakan
 * temizlik onu bulamıyordu; bu tarama kayıttan bağımsızdır.
 */
const BLOCKED_MODS: ReadonlyArray<{ modId: string; projectId: string }> = [
  { modId: 'krypton', projectId: 'fQEb0iXm' }
]

/** Modrinth proje kimliği engelli bir moda mı ait (Mod Ara'da gösterilmez, kurulmaz). */
export function isBlockedModProject(projectId: string): boolean {
  return BLOCKED_MODS.some((m) => m.projectId === projectId)
}

/** Jar'ın fabric.mod.json'daki mod kimliği; okunamazsa null. */
async function fabricModId(jarPath: string): Promise<string | null> {
  const zip = await open(jarPath, { lazyEntries: true, autoClose: false })
  try {
    for await (const entry of walkEntriesGenerator(zip)) {
      if (entry.fileName !== 'fabric.mod.json') continue
      const json = JSON.parse((await readEntry(zip, entry)).toString('utf8')) as { id?: unknown }
      return typeof json.id === 'string' ? json.id : null
    }
    return null
  } finally {
    zip.close()
  }
}

/** mods/ klasöründeki engelli modları siler; silinen dosya adlarını döner. */
export async function removeBlockedMods(gamePath: string): Promise<string[]> {
  const modsDir = join(gamePath, 'mods')
  let names: string[]
  try {
    names = await readdir(modsDir)
  } catch {
    return []
  }
  const removed: string[] = []
  for (const name of names) {
    if (!name.toLowerCase().endsWith('.jar')) continue
    const path = join(modsDir, name)
    let id: string | null = null
    try {
      id = await fabricModId(path)
    } catch {
      // Okunamayan jar: kimlik yerine dosya adına bakılır.
    }
    const lower = name.toLowerCase()
    const blocked = id !== null ? BLOCKED_MODS.some((m) => m.modId === id) : BLOCKED_MODS.some((m) => lower.startsWith(m.modId))
    if (!blocked) continue
    try {
      await rm(path, { force: true })
      removed.push(name)
    } catch (err) {
      console.warn(`[blocked-mods] ${name} silinemedi:`, (err as Error).message)
    }
  }
  if (removed.length > 0) console.info('[blocked-mods] kaldırıldı:', removed.join(', '))
  return removed
}
