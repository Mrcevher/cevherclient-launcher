import { createHash } from 'node:crypto'
import { createReadStream, existsSync } from 'node:fs'
import { mkdir, readFile, readdir, rename, stat, writeFile } from 'node:fs/promises'
import { basename, join, relative } from 'node:path'
import { app } from 'electron'
import { open, readEntry, walkEntriesGenerator } from '@xmcl/unzip'
import type { QuarantinedMod } from '@shared/types'
import { isBlockedModProject } from './blocked-mods'

/**
 * Mod bütünlüğü: oyun açılmadan önce instance'ın mods/ klasöründeki her jar'ın SHA-1'i denetlenir.
 * Bir jar yalnızca şu durumlarda kalır:
 *   - sunucunun modpack'inden geldi (modpack senkronunun kaydındaki hash),
 *   - launcher'ın kendi modlarından biri (resources/mods içindekilerle bayt bayt aynı),
 *   - Modrinth'te yayımlanmış bir sürümün değiştirilmemiş dosyası (Mod Ara / Performans Paketi),
 *   - sunucunun mod politikasında (CevherAuth /api/mod-policy) izinli.
 *
 * Karar dosya adına ya da fabric.mod.json'daki kimliğe göre verilmez: bir hile modunun adını,
 * kimliğini veya paket yapısını değiştiren araçlar jar'ın içeriğini, dolayısıyla hash'ini de
 * değiştirir; değişmiş jar hiçbir listede bulunmaz. Kalmayan jar silinmez, nedeniyle birlikte
 * instance/.cevher/karantina/ altına taşınır.
 *
 * Jar'lar hiçbir koşulda çalıştırılmaz: yalnızca zip dizini (dosya adları) ve fabric.mod.json okunur.
 * Asıl karar sunucudadır (CevherAuth, oturum modunun bildirdiği hash'ler); bu adım oyuncuyu
 * erkenden uyarır ve hazır hile dosyalarını oyuna hiç yüklemez.
 */

const MODRINTH_API = 'https://api.modrinth.com/v2'
const QUARANTINE_DIR = join('.cevher', 'karantina')
const CACHE_FILE = join('.cevher', 'mod-hash-cache.json')
/** fabric.mod.json'u okumak için en fazla boyut (zip bombasına karşı). */
const MAX_META_BYTES = 256 * 1024

/**
 * Bilinen hile istemcilerinin paket/sınıf yolları. Yalnızca karantinaya alınan jar'ın risk
 * düzeyini ve raporunu belirler; izin kararı hash'e göredir (benzerlik tek başına kanıt sayılmaz).
 */
const CHEAT_SIGNATURES: ReadonlyArray<{ path: string; name: string }> = [
  { path: 'meteordevelopment/meteorclient/', name: 'Meteor Client' },
  { path: 'net/wurstclient/', name: 'Wurst' },
  { path: 'net/ccbluex/liquidbounce/', name: 'LiquidBounce' },
  { path: 'me/zeroeightsix/kami/', name: 'KAMI' },
  { path: 'org/bleachhack/', name: 'BleachHack' },
  { path: 'thunder/hack/', name: 'ThunderHack' },
  { path: 'dev/boze/', name: 'Boze' },
  { path: 'org/rusherhack/', name: 'RusherHack' },
  { path: 'com/aristois/', name: 'Aristois' },
  { path: 'autismclient', name: 'AutismClient' },
  { path: 'xynis', name: 'Xynis' },
  { path: 'net/mathax/', name: 'MatHax' },
  { path: 'xyz/jex/', name: 'Jex' }
]

/**
 * Hile anahtar kelimeleri (KRİTİK). Jar'daki sınıf/paket yolları, dosya adı ve fabric.mod.json'daki
 * kimlik/ad kelimelere bölünür (/, ., _, -, $ ve büyük harf geçişleri): "KillAura.class" → kill, aura.
 * Bitişik yazımlar da yakalanır ("killaura", "thunderhack", "vapeclient"): kelime parçanın içinde
 * geçmesi yeterlidir. Yanlış eşleşmesi çok olan kısa kelimeler (EXACT_ONLY) yalnızca tam kelimeyken
 * sayılır: "squares" içindeki "ares", "evaluate" içindeki "lua", "executor" içindeki "exec" sayılmaz.
 *
 * Yalnızca hash denetimini zaten geçemeyen (karantinaya giden) jar'lara uygulanır: "exec", "trigger",
 * "combat" gibi kelimeler meşru modlarda da geçer (Better Combat, JEI'nin "cheat" modu, Java'nın
 * Executor sınıfları); bu modlar Modrinth hash'iyle geçtiği için bu taramaya hiç girmez.
 */
const CHEAT_KEYWORDS: ReadonlySet<string> = new Set([
  'hack', 'hile', 'cheat', 'injector', 'trigger', 'lua', 'wurst', 'aristois', 'meteor', 'ares', 'combat',
  'mathax', 'vape', 'jex', 'liquidbounce', 'trainer', 'bleachhack', 'hacked', 'aura', 'impactclient',
  'doomsday', 'sigma', 'exec'
])

/** "net/foo/KillAuraModule$1.class" → ["net", "foo", "kill", "aura", "module", "1", "class"] */
function words(text: string): string[] {
  return text
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/([A-Z]+)([A-Z][a-z])/g, '$1 $2')
    .toLowerCase()
    .split(/[^a-z0-9ğüşıöç]+/)
    .filter((w) => w.length > 0)
}

/** Yalnızca tam kelimeyken sayılan kısa anahtar kelimeler. Biçim CevherAuth ModDogrulama ile aynı. */
const EXACT_ONLY: ReadonlySet<string> = new Set(['lua', 'ares', 'exec', 'jex', 'sigma'])

/** Metindeki hile anahtar kelimeleri. */
function keywordHits(text: string): string[] {
  const out = new Set<string>()
  for (const w of words(text)) {
    for (const k of CHEAT_KEYWORDS) {
      if (EXACT_ONLY.has(k) ? w === k : w.includes(k)) out.add(k)
    }
  }
  return [...out]
}

interface ModPolicy {
  allowedSha1: Set<string>
  blockedSha1: Set<string>
  blockedProjects: Set<string>
}

interface HashCache {
  /** sha1 → Modrinth proje kimliği (yalnızca bulunanlar saklanır; bulunmayanlar her açılışta yeniden sorulur). */
  modrinth: Record<string, string>
}

function bundledModsDir(): string {
  return app.isPackaged ? join(process.resourcesPath, 'mods') : join(app.getAppPath(), 'resources', 'mods')
}

function sha(path: string, algorithm: 'sha1' | 'sha256'): Promise<string> {
  return new Promise((resolve, reject) => {
    const h = createHash(algorithm)
    createReadStream(path)
      .on('data', (chunk) => h.update(chunk))
      .on('error', reject)
      .on('end', () => resolve(h.digest('hex')))
  })
}

/** mods/ altındaki (Fabric'in yükleyeceği) jar'lar; gizli dosya/klasörler Fabric gibi atlanır. */
async function listJars(dir: string): Promise<string[]> {
  let names: string[]
  try {
    names = await readdir(dir)
  } catch {
    return []
  }
  const out: string[] = []
  for (const name of names) {
    if (name.startsWith('.')) continue
    const path = join(dir, name)
    const info = await stat(path).catch(() => null)
    if (!info) continue
    if (info.isDirectory()) out.push(...(await listJars(path)))
    else if (info.isFile() && name.toLowerCase().endsWith('.jar')) out.push(path)
  }
  return out
}

async function modpackHashes(gamePath: string): Promise<Set<string>> {
  try {
    const raw = JSON.parse(await readFile(join(gamePath, '.cevher', 'modpack.json'), 'utf8')) as { files?: Record<string, unknown> }
    return new Set(Object.values(raw.files ?? {}).filter((v): v is string => typeof v === 'string').map((v) => v.toLowerCase()))
  } catch {
    return new Set()
  }
}

async function bundledHashes(): Promise<Set<string>> {
  const out = new Set<string>()
  for (const path of await listJars(bundledModsDir())) out.add(await sha(path, 'sha1'))
  return out
}

async function fetchPolicy(authUrl: string | undefined): Promise<ModPolicy> {
  const policy: ModPolicy = { allowedSha1: new Set(), blockedSha1: new Set(), blockedProjects: new Set() }
  if (!authUrl) return policy
  try {
    const res = await fetch(`${authUrl}/api/mod-policy`, { signal: AbortSignal.timeout(8_000) })
    if (!res.ok) return policy
    const body = (await res.json()) as { allowedSha1?: unknown; blockedSha1?: unknown; blockedProjects?: unknown }
    const add = (set: Set<string>, list: unknown): void => {
      if (Array.isArray(list)) for (const v of list) if (typeof v === 'string') set.add(v.toLowerCase())
    }
    add(policy.allowedSha1, body.allowedSha1)
    add(policy.blockedSha1, body.blockedSha1)
    add(policy.blockedProjects, body.blockedProjects)
  } catch (err) {
    console.warn('[mod-integrity] Mod politikası alınamadı:', (err as Error).message)
  }
  return policy
}

/**
 * Hash'leri Modrinth'te toplu arar: sha1 → proje kimliği. Modrinth'e ulaşılamazsa null döner;
 * o zaman daha önce doğrulanmamış jar'lar karantinaya alınır (önbellekteki doğrulanmış modlar
 * etkilenmez) ve Modrinth'e yeniden ulaşılınca kendiliğinden geri alınır.
 */
async function lookupModrinth(hashes: string[]): Promise<Map<string, string> | null> {
  const found = new Map<string, string>()
  if (hashes.length === 0) return found
  try {
    const res = await fetch(`${MODRINTH_API}/version_files`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'User-Agent': `Cevherclient/${app.getVersion()} (cevhercraft.com)` },
      body: JSON.stringify({ hashes, algorithm: 'sha1' }),
      signal: AbortSignal.timeout(15_000)
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    const body = (await res.json()) as Record<string, { project_id?: unknown }>
    for (const [hash, version] of Object.entries(body)) {
      if (typeof version?.project_id === 'string') found.set(hash.toLowerCase(), version.project_id)
    }
    return found
  } catch (err) {
    console.warn('[mod-integrity] Modrinth hash sorgusu yapılamadı:', (err as Error).message)
    return null
  }
}

async function readCache(gamePath: string): Promise<HashCache> {
  try {
    const raw = JSON.parse(await readFile(join(gamePath, CACHE_FILE), 'utf8')) as Partial<HashCache>
    return { modrinth: raw.modrinth && typeof raw.modrinth === 'object' ? raw.modrinth : {} }
  } catch {
    return { modrinth: {} }
  }
}

/** Zip dizinini okur (çalıştırmadan): imza eşleşmeleri ve fabric.mod.json kimliği. */
async function inspectJar(
  path: string
): Promise<{ modId: string | null; signatures: string[]; keywords: string[]; fabric: boolean; readable: boolean }> {
  const signatures = new Set<string>()
  /** kelime → ilk görüldüğü yer (rapor için). */
  const keywords = new Map<string, string>()
  const addKeywords = (text: string, where: string): void => {
    for (const k of keywordHits(text)) if (!keywords.has(k)) keywords.set(k, where)
  }
  addKeywords(basename(path).replace(/\.jar$/i, ''), 'dosya adı')
  let modId: string | null = null
  let fabric = false
  const result = (readable: boolean) => ({
    modId,
    signatures: [...signatures].slice(0, 5),
    keywords: [...keywords].slice(0, 8).map(([k, where]) => `${k} (${where})`),
    fabric,
    readable
  })
  try {
    const zip = await open(path, { lazyEntries: true, autoClose: false })
    try {
      for await (const entry of walkEntriesGenerator(zip)) {
        const name = entry.fileName.toLowerCase()
        for (const sig of CHEAT_SIGNATURES) {
          if (name.includes(sig.path)) signatures.add(`${sig.name} (${entry.fileName})`)
        }
        // Yalnızca kod ve yapılandırma yolları (resim/ses adları gürültüdür).
        if (/\.(class|json|lua|toml|properties)$/i.test(entry.fileName) || entry.fileName.endsWith('/')) {
          addKeywords(entry.fileName.replace(/\.(class|json|lua|toml|properties)$/i, ''), entry.fileName)
        }
        if (entry.fileName === 'fabric.mod.json' && entry.uncompressedSize <= MAX_META_BYTES) {
          fabric = true
          try {
            const json = JSON.parse((await readEntry(zip, entry)).toString('utf8')) as { id?: unknown; name?: unknown }
            if (typeof json.id === 'string') {
              modId = json.id
              addKeywords(json.id, 'mod kimliği')
            }
            if (typeof json.name === 'string') addKeywords(json.name, 'mod adı')
          } catch {
            // Bozuk fabric.mod.json: kimlik bilinmiyor.
          }
        }
      }
    } finally {
      zip.close()
    }
    return result(true)
  } catch {
    return result(false)
  }
}

/**
 * Önceki açılışlarda karantinaya alınmış jar'lardan artık doğrulananları (ör. Modrinth o sırada
 * erişilemiyordu) mods/ klasörüne geri koyar. Yalnızca hash'i güvenilir ya da Modrinth'te bulunan
 * (yasaklı olmayan) dosyalar döner; aynı adlı dosya varsa dokunulmaz.
 */
async function restoreVerified(
  gamePath: string,
  isAllowed: (sha1: string) => boolean,
  quarantinedJars: Array<{ path: string; sha1: string }>
): Promise<string[]> {
  const restored: string[] = []
  for (const q of quarantinedJars) {
    if (!isAllowed(q.sha1)) continue
    const target = join(gamePath, 'mods', basename(q.path))
    if (existsSync(target)) continue
    try {
      await rename(q.path, target)
      restored.push(basename(q.path))
    } catch (err) {
      console.warn(`[mod-integrity] ${basename(q.path)} geri yüklenemedi:`, (err as Error).message)
    }
  }
  if (restored.length > 0) console.info('[mod-integrity] doğrulanıp karantinadan geri alındı:', restored.join(', '))
  return restored
}

interface FabricMeta {
  id: string | null
  provides: string[]
  /** Zorunlu bağımlılık kimlikleri (fabric.mod.json "depends" anahtarları). */
  depends: string[]
}

/** Jar'ın fabric.mod.json'unu okur (çalıştırmadan); Fabric modu değilse ya da okunamazsa null. */
async function readMeta(path: string): Promise<FabricMeta | null> {
  try {
    const zip = await open(path, { lazyEntries: true, autoClose: false })
    try {
      for await (const entry of walkEntriesGenerator(zip)) {
        if (entry.fileName !== 'fabric.mod.json') continue
        if (entry.uncompressedSize > MAX_META_BYTES) return null
        const json = JSON.parse((await readEntry(zip, entry)).toString('utf8')) as {
          id?: unknown
          provides?: unknown
          depends?: unknown
        }
        const strings = (v: unknown): string[] => (Array.isArray(v) ? v.filter((x): x is string => typeof x === 'string') : [])
        return {
          id: typeof json.id === 'string' ? json.id : null,
          provides: strings(json.provides),
          depends: json.depends && typeof json.depends === 'object' ? Object.keys(json.depends as object) : []
        }
      }
      return null
    } finally {
      zip.close()
    }
  } catch {
    return null
  }
}

function stamp(): string {
  const d = new Date()
  const p = (n: number): string => String(n).padStart(2, '0')
  return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}-${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}`
}

/**
 * mods/ klasörünü denetler; izinsiz jar'ları karantinaya taşır ve listesini döner.
 * Hata oyunu engellemez (karar sunucudadır); yalnızca günlüğe yazılır.
 */
export async function enforceModIntegrity(gamePath: string, authUrl: string | undefined): Promise<QuarantinedMod[]> {
  try {
    return await run(gamePath, authUrl)
  } catch (err) {
    console.warn('[mod-integrity] Denetim yapılamadı:', (err as Error).message)
    return []
  }
}

async function run(gamePath: string, authUrl: string | undefined): Promise<QuarantinedMod[]> {
  const modsDir = join(gamePath, 'mods')
  // Önceki karantinalar: artık doğrulananlar geri alınabilsin diye hash'leri de sorulur.
  const oldQuarantine: Array<{ path: string; sha1: string }> = []
  for (const path of await listJars(join(gamePath, QUARANTINE_DIR))) {
    oldQuarantine.push({ path, sha1: await sha(path, 'sha1') })
  }
  if ((await listJars(modsDir)).length === 0 && oldQuarantine.length === 0) return []

  const [pack, bundled, policy, cache] = await Promise.all([
    modpackHashes(gamePath),
    bundledHashes(),
    fetchPolicy(authUrl),
    readCache(gamePath)
  ])
  const trusted = (h: string): boolean => !policy.blockedSha1.has(h) && (pack.has(h) || bundled.has(h) || policy.allowedSha1.has(h))
  const verifiedOnModrinth = (h: string): boolean => {
    const project = cache.modrinth[h]
    return !!project && !policy.blockedSha1.has(h) && !policy.blockedProjects.has(project.toLowerCase()) && !isBlockedModProject(project)
  }

  const firstJars = await listJars(modsDir)
  const firstHashes = await Promise.all(firstJars.map((j) => sha(j, 'sha1')))
  const unknown = [...new Set([...firstHashes, ...oldQuarantine.map((q) => q.sha1)].filter((h) => !trusted(h) && !cache.modrinth[h]))]
  const looked = await lookupModrinth(unknown)
  if (looked) {
    for (const [h, project] of looked) cache.modrinth[h] = project
    await mkdir(join(gamePath, '.cevher'), { recursive: true })
    await writeFile(join(gamePath, CACHE_FILE), JSON.stringify(cache), 'utf8').catch(() => undefined)
  }
  // Doğrulanan jar'lar geri alınır; ama karantinada kalan bir moda bağımlıysa (ör. hile istemcisinin
  // eklentisi) geri alınmaz: alınsaydı oyun açılışta "gerekli mod yok" diye çökerdi. Zincirleme bağımlılık
  // için kalanlar listesi değişmeyene kadar tekrarlanır.
  const metaOf = new Map<string, FabricMeta | null>()
  for (const q of oldQuarantine) metaOf.set(q.path, await readMeta(q.path))
  const currentIds = new Set<string>()
  for (const jar of firstJars) {
    const m = await readMeta(jar)
    if (m?.id) currentIds.add(m.id)
    for (const p of m?.provides ?? []) currentIds.add(p)
  }
  const stayIds = new Set<string>()
  const addIds = (m: FabricMeta | null | undefined): void => {
    if (m?.id) stayIds.add(m.id)
    for (const p of m?.provides ?? []) stayIds.add(p)
  }
  let restorable = oldQuarantine.filter((q) => trusted(q.sha1) || verifiedOnModrinth(q.sha1))
  for (const q of oldQuarantine) if (!restorable.includes(q)) addIds(metaOf.get(q.path))
  for (let changed = true; changed; ) {
    changed = false
    for (const q of restorable) {
      if (metaOf.get(q.path)?.depends.some((d) => stayIds.has(d) && !currentIds.has(d))) {
        addIds(metaOf.get(q.path))
        restorable = restorable.filter((x) => x !== q)
        changed = true
      }
    }
  }
  await restoreVerified(gamePath, () => true, restorable)

  const jars = await listJars(modsDir)
  const hashes = new Map<string, string>()
  for (const jar of jars) hashes.set(jar, await sha(jar, 'sha1'))

  const quarantined: QuarantinedMod[] = []
  /** Bu açılışta karantinaya taşınan dosyaların yeni yolları. */
  const moved: string[] = []
  const dir = join(gamePath, QUARANTINE_DIR, stamp())
  for (const jar of jars) {
    const h = hashes.get(jar)!
    let reason: string | null = null
    let risk: QuarantinedMod['risk'] = 'MEDIUM'
    const project = cache.modrinth[h]
    if (policy.blockedSha1.has(h)) {
      reason = 'Sunucu bu dosyayı yasakladı'
      risk = 'CRITICAL'
    } else if (trusted(h)) {
      continue
    } else if (project) {
      if (!policy.blockedProjects.has(project.toLowerCase()) && !isBlockedModProject(project)) continue
      reason = `Sunucunun yasakladığı Modrinth projesi (${project})`
      risk = 'CRITICAL'
    } else if (!looked) {
      // Modrinth'e ulaşılamadı (kesinti ya da oyuncu erişimi engelledi): doğrulanamayan jar yüklenmez.
      // Daha önce doğrulanmış modlar önbellekte olduğu için etkilenmez; bu jar bir sonraki açılışta
      // Modrinth'te bulunursa karantinadan kendiliğinden geri alınır.
      reason = "Doğrulanamadı: Modrinth'e ulaşılamadı (bağlantı engellenmiş olabilir); doğrulanınca geri alınır"
    } else {
      reason = "Modpack'te ve Modrinth'te yok: değiştirilmiş, yeniden adlandırılmış ya da tanınmayan jar"
    }

    const info = await inspectJar(jar)
    if (info.signatures.length > 0) {
      risk = 'CRITICAL'
      reason = `Bilinen hile istemcisi kodu: ${info.signatures.join(', ')}`
    } else if (info.keywords.length > 0) {
      risk = 'CRITICAL'
      reason = `Hile anahtar kelimesi: ${info.keywords.join(', ')} — ${reason ?? ''}`
    } else if (!info.readable) {
      risk = 'HIGH'
      reason = (reason ?? '') + ' (jar okunamadı / bozuk)'
    } else if (!info.fabric && risk !== 'CRITICAL') {
      risk = 'HIGH'
      reason = (reason ?? '') + ' (Fabric modu değil)'
    }

    const name = relative(modsDir, jar)
    const entry: QuarantinedMod = {
      name,
      modId: info.modId,
      sha1: h,
      sha256: await sha(jar, 'sha256'),
      risk,
      reason: reason ?? ''
    }
    try {
      await mkdir(dir, { recursive: true })
      const target = join(dir, name.replaceAll(/[\\/]/g, '_'))
      await rename(jar, target)
      quarantined.push(entry)
      moved.push(target)
    } catch (err) {
      console.warn(`[mod-integrity] ${name} karantinaya taşınamadı:`, (err as Error).message)
    }
  }

  // Karantinaya alınan bir moda (ör. hile istemcisinin eklentisi ya da kütüphanesi) bağımlı kalan modlar:
  // bırakılırsa Fabric "X, Y'yi gerektiriyor" diyerek oyunu açılışta çökertir (LAUNCH_FAILED). Bunlar da
  // karantinaya alınır; zincirleme bağımlılıklar için kalan kalmayana kadar tekrarlanır.
  // Kaldırılmış sayılan kimlikler: bu açılışta ve önceki açılışlarda karantinada kalan tüm jar'lar.
  const removed = new Set<string>()
  for (const path of await listJars(join(gamePath, QUARANTINE_DIR))) {
    const m = await readMeta(path)
    if (m?.id) removed.add(m.id)
    for (const p of m?.provides ?? []) removed.add(p)
  }
  for (let round = 0; removed.size > 0 && round < 10; round++) {
    const metas = new Map<string, FabricMeta | null>()
    for (const jar of await listJars(modsDir)) metas.set(jar, await readMeta(jar))
    const available = new Set<string>()
    for (const m of metas.values()) {
      if (m?.id) available.add(m.id)
      for (const p of m?.provides ?? []) available.add(p)
    }
    let changed = false
    for (const [jar, m] of metas) {
      const missing = m?.depends.find((d) => removed.has(d) && !available.has(d))
      if (!m || !missing) continue
      const name = relative(modsDir, jar)
      const target = join(dir, name.replaceAll(/[\\/]/g, '_'))
      try {
        await mkdir(dir, { recursive: true })
        const sha1 = hashes.get(jar) ?? (await sha(jar, 'sha1'))
        const sha256 = await sha(jar, 'sha256')
        await rename(jar, target)
        quarantined.push({
          name,
          modId: m.id,
          sha1,
          sha256,
          risk: 'MEDIUM',
          reason: `Karantinadaki "${missing}" moduna bağımlı: bırakılsaydı oyun açılışta çökerdi`
        })
        if (m.id) removed.add(m.id)
        for (const p of m.provides) removed.add(p)
        changed = true
      } catch (err) {
        console.warn(`[mod-integrity] ${name} (bağımlı) karantinaya taşınamadı:`, (err as Error).message)
      }
    }
    if (!changed) break
  }

  if (quarantined.length > 0 && existsSync(dir)) {
    await writeFile(join(dir, 'rapor.json'), JSON.stringify({ tarih: new Date().toISOString(), dosyalar: quarantined }, null, 2), 'utf8')
    console.warn('[mod-integrity] karantinaya alındı:', quarantined.map((q) => `${q.name} [${q.risk}]`).join(', '))
  }
  return quarantined
}
