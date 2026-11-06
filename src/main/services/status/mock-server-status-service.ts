import type { ServerProfile, ServerStatus } from '@shared/types'
import { delay, randomInt } from '../mock/mock-utils'
import type { ServerStatusService } from '../types'

/**
 * Faz 1 mock'u: adresi ".invalid" ile biten sunucular çevrimdışı sayılır
 * (RFC 2606 ayrılmış TLD), diğerleri rastgele oyuncu sayısı ve ping ile çevrimiçidir.
 * Faz 2'de main process'te gerçek Server List Ping yapılacak.
 */
export class MockServerStatusService implements ServerStatusService {
  async getStatus(server: ServerProfile): Promise<ServerStatus> {
    await delay(randomInt(250, 650))
    const online = !server.address.endsWith('.invalid')
    return {
      serverId: server.id,
      online,
      players: online ? { online: randomInt(4, 38), max: 100 } : { online: 0, max: 0 },
      pingMs: online ? randomInt(18, 85) : null,
      checkedAt: new Date().toISOString()
    }
  }
}
