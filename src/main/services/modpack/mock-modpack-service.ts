import type { ModpackSyncResult, ServerProfile } from '@shared/types'
import { maybeFail, randomInt, simulateWork } from '../mock/mock-utils'
import type { ModpackService, ProgressCallback } from '../types'

/**
 * Faz 1 mock'u. Faz 3'te: sunucu profilindeki .mrpack manifest'i okunacak, modlar
 * SHA1 ile doğrulanarak indirilecek, launcher'ın kurduğu ama manifest'te olmayan
 * modlar kaldırılacak; options.txt ve config/ klasörü korunacak.
 */
export class MockModpackService implements ModpackService {
  async sync(server: ServerProfile, onProgress: ProgressCallback): Promise<ModpackSyncResult> {
    if (!server.modpackManifestUrl) {
      onProgress(1)
      return { serverId: server.id, added: 0, updated: 0, removed: 0, unchanged: 0 }
    }
    await simulateWork(1200, onProgress, 12)
    maybeFail('MODPACK_SYNC_FAILED', { server: server.id })
    return { serverId: server.id, added: 0, updated: randomInt(0, 2), removed: 0, unchanged: randomInt(18, 30) }
  }
}
