import { app } from 'electron'
import { dirname, join, resolve } from 'node:path'

/**
 * Launcher'ın oyun dosyası yerleşimi (userData altında):
 *   minecraft/   versions, libraries, assets — tüm sürümlerin ortak kaynakları
 *   instances/   sunucu başına oyun klasörü (saves, options.txt, mods …)
 *   runtimes/    launcher'ın indirdiği Java runtime'ları (bileşen adına göre)
 *   logs/        oyun process çıktıları
 */
export const gameDirs = {
  get root(): string {
    return join(app.getPath('userData'), 'minecraft')
  },
  get instances(): string {
    return join(app.getPath('userData'), 'instances')
  },
  get runtimes(): string {
    return join(app.getPath('userData'), 'runtimes')
  },
  get logs(): string {
    return join(app.getPath('userData'), 'logs')
  }
}

/** Sunucu profili için oyun klasörü; sonuç her zaman instances/ altında kalır. */
export function instanceDir(serverId: string): string {
  if (!/^[A-Za-z0-9_-]{1,64}$/.test(serverId)) {
    throw new Error(`Geçersiz sunucu kimliği: ${serverId.slice(0, 80)}`)
  }
  const base = resolve(gameDirs.instances)
  const dir = resolve(base, serverId)
  if (dirname(dir) !== base) throw new Error(`Sunucu klasörü instances/ dışına çıkıyor: ${serverId}`)
  return dir
}
