import { resolveSrv } from 'node:dns/promises'
import { isIP, Socket } from 'node:net'
import type { ServerProfile, ServerStatus } from '@shared/types'
import type { ServerStatusService } from '../types'

const DEFAULT_PORT = 25565
const TIMEOUT_MS = 5000
/** Durum yanıtı için kabul edilen en büyük veri (favicon dahil birkaç on KB gelir). */
const MAX_RESPONSE_BYTES = 512 * 1024
/** Durum isteğinde "herhangi bir sürüm" anlamına gelir; sunucu kendi protokolünü döndürür. */
const PROTOCOL_ANY = -1

// ── Protokol yardımcıları (VarInt, String, paket çerçeveleme) ─────────────────

function varInt(value: number): Buffer {
  const bytes: number[] = []
  let v = value >>> 0
  do {
    let byte = v & 0x7f
    v >>>= 7
    if (v !== 0) byte |= 0x80
    bytes.push(byte)
  } while (v !== 0)
  return Buffer.from(bytes)
}

function mcString(value: string): Buffer {
  const data = Buffer.from(value, 'utf8')
  return Buffer.concat([varInt(data.length), data])
}

function packet(id: number, ...fields: Buffer[]): Buffer {
  const body = Buffer.concat([varInt(id), ...fields])
  return Buffer.concat([varInt(body.length), body])
}

/** Tampondan VarInt okur; tampon yetmiyorsa undefined. */
function readVarInt(buf: Buffer, offset: number): { value: number; size: number } | undefined {
  let value = 0
  for (let i = 0; i < 5; i++) {
    if (offset + i >= buf.length) return undefined
    const byte = buf[offset + i]!
    value |= (byte & 0x7f) << (7 * i)
    if ((byte & 0x80) === 0) return { value, size: i + 1 }
  }
  throw new Error('VarInt çok uzun')
}

interface PingResult {
  players: { online: number; max: number }
  pingMs: number
}

/**
 * Modern (1.7+) Server List Ping: Handshake(next=1) → Status Request → Status Response
 * (JSON) → Ping(payload) → Pong. Ping süresi Ping/Pong gidiş-dönüşüdür; sunucu Pong
 * göndermezse durum yanıtının süresi kullanılır.
 */
function ping(host: string, port: number, handshakeHost: string): Promise<PingResult> {
  return new Promise((resolve, reject) => {
    const socket = new Socket()
    let buffer = Buffer.alloc(0)
    let status: { players?: { online?: number; max?: number } } | undefined
    let statusAt = 0
    let pingSentAt = 0
    let requestAt = 0

    const finish = (err: Error | null, result?: PingResult): void => {
      socket.destroy()
      if (err) reject(err)
      else resolve(result!)
    }
    const result = (pingMs: number): PingResult => ({
      players: { online: status?.players?.online ?? 0, max: status?.players?.max ?? 0 },
      pingMs: Math.max(1, Math.round(pingMs))
    })

    socket.setTimeout(TIMEOUT_MS, () => {
      if (status) finish(null, result(statusAt - requestAt))
      else finish(new Error('Zaman aşımı'))
    })
    socket.once('error', (err) => {
      if (status) finish(null, result(statusAt - requestAt))
      else finish(err)
    })
    socket.once('close', () => {
      if (status) finish(null, result(statusAt - requestAt))
      else finish(new Error('Bağlantı kapandı'))
    })

    socket.connect(port, host, () => {
      const portBuf = Buffer.alloc(2)
      portBuf.writeUInt16BE(port)
      requestAt = performance.now()
      socket.write(
        Buffer.concat([
          packet(0x00, varInt(PROTOCOL_ANY), mcString(handshakeHost), portBuf, varInt(1)),
          packet(0x00)
        ])
      )
    })

    // Sonsuz/dev veri akıtan bir sunucu belleği şişirmesin; boşta süresi her veriyle sıfırlandığı için toplam süre de sınırlı.
    const deadline = setTimeout(() => finish(new Error('Toplam süre aşıldı')), TIMEOUT_MS * 2)
    socket.once('close', () => clearTimeout(deadline))

    socket.on('data', (chunk) => {
      if (buffer.length + chunk.length > MAX_RESPONSE_BYTES) return finish(new Error('Yanıt çok büyük'))
      buffer = Buffer.concat([buffer, chunk])
      try {
        for (;;) {
          const len = readVarInt(buffer, 0)
          if (!len || buffer.length < len.size + len.value) return
          const frame = buffer.subarray(len.size, len.size + len.value)
          buffer = buffer.subarray(len.size + len.value)
          const id = readVarInt(frame, 0)
          if (!id) throw new Error('Bozuk paket')

          if (id.value === 0x00 && !status) {
            const strLen = readVarInt(frame, id.size)
            if (!strLen) throw new Error('Bozuk durum yanıtı')
            const start = id.size + strLen.size
            status = JSON.parse(frame.subarray(start, start + strLen.value).toString('utf8'))
            statusAt = performance.now()
            const payload = Buffer.alloc(8)
            payload.writeBigInt64BE(BigInt(Date.now()))
            pingSentAt = performance.now()
            socket.write(packet(0x01, payload))
          } else if (id.value === 0x01 && status) {
            finish(null, result(performance.now() - pingSentAt))
            return
          }
        }
      } catch (err) {
        finish(err as Error)
      }
    })
  })
}

/** Port belirtilmemiş (varsayılan) bir alan adı için Minecraft istemcisi gibi SRV kaydına bakar. */
async function resolveTarget(address: string, port: number): Promise<{ host: string; port: number }> {
  if (port !== DEFAULT_PORT || isIP(address) !== 0) return { host: address, port }
  try {
    const [record] = await resolveSrv(`_minecraft._tcp.${address}`)
    if (record) return { host: record.name, port: record.port }
  } catch {
    // SRV kaydı yok: doğrudan adrese bağlan.
  }
  return { host: address, port }
}

/** Main process'te gerçek Server List Ping. Ulaşılamayan sunucu çevrimdışı sayılır. */
export class SlpServerStatusService implements ServerStatusService {
  async getStatus(server: ServerProfile): Promise<ServerStatus> {
    const checkedAt = (): string => new Date().toISOString()
    try {
      const target = await resolveTarget(server.address, server.port)
      const { players, pingMs } = await ping(target.host, target.port, server.address)
      return { serverId: server.id, online: true, players, pingMs, checkedAt: checkedAt() }
    } catch {
      return { serverId: server.id, online: false, players: { online: 0, max: 0 }, pingMs: null, checkedAt: checkedAt() }
    }
  }
}
