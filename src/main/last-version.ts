import { app } from 'electron'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { join } from 'node:path'

/**
 * Önceki açılıştan beri sürüm değiştiyse true döner (güncelleme sonrası açılış penceresi
 * "Güncellendi" yazsın diye). Her açılışta geçerli sürüm userData altına yazılır; dosya
 * yoksa (ilk kurulum) güncelleme sayılmaz.
 */
export async function consumeUpdatedFlag(): Promise<boolean> {
  const dir = app.getPath('userData')
  const file = join(dir, 'last-run-version.txt')
  const current = app.getVersion()
  let previous: string | null = null
  try {
    previous = (await readFile(file, 'utf8')).trim() || null
  } catch {
    // dosya yok → ilk açılış
  }
  if (previous !== current) {
    try {
      await mkdir(dir, { recursive: true })
      await writeFile(file, current, 'utf8')
    } catch {
      // yazılamazsa bir sonraki açılışta yine "güncellendi" görünebilir; kritik değil
    }
  }
  return previous !== null && previous !== current
}
