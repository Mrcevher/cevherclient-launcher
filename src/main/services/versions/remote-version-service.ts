import { join } from 'node:path'
import { readFile } from 'node:fs/promises'
import { AppError } from '@shared/errors'
import type { GameVersion, LoaderType, VersionChannel } from '@shared/types'
import { cachedJson, fetchJson } from '../minecraft/http'
import { gameDirs } from '../minecraft/paths'
import { parseVersionKey, versionKey } from '../minecraft/version-key'
import type { JavaRequirement, VersionService } from '../types'

const MOJANG_MANIFEST = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'
const FABRIC_META = 'https://meta.fabricmc.net/v2'
const QUILT_META = 'https://meta.quiltmc.org/v3'
const FORGE_PROMOTIONS = 'https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json'
const NEOFORGE_VERSIONS = 'https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge'

/** Listeler bu süre boyunca bellekte tutulur; ağ yoksa diskteki son kopya kullanılır. */
const LIST_TTL_MS = 10 * 60_000

interface MojangVersion {
  id: string
  type: 'release' | 'snapshot' | 'old_beta' | 'old_alpha'
  url: string
  releaseTime: string
  sha1?: string
}

interface MojangManifest {
  latest: { release: string; snapshot: string }
  versions: MojangVersion[]
}

interface MetaVersion {
  version: string
  stable?: boolean
}

/** Vanilla sürüm metadata'sının (version JSON) konumu ve doğrulaması. */
export interface VanillaVersionInfo {
  id: string
  url: string
  sha1?: string
  releaseTime: string
}

function cacheFile(name: string): string {
  return join(gameDirs.root, 'meta', name)
}

function isUnstable(version: string): boolean {
  return /alpha|beta|pre|rc|snapshot/i.test(version)
}

/**
 * NeoForge sürümünden Minecraft sürümünü türetir.
 *   21.10.42   → 1.21.10     21.0.167 → 1.21
 *   26.1.2.31  → 26.1.2      26.3.0.89 → 26.3   (26.x numaralandırması: 4 parça)
 */
export function neoforgeToMinecraft(version: string): string | undefined {
  const parts = version.split('-')[0]!.split('.')
  if (!parts.every((p) => /^\d+$/.test(p))) return undefined
  if (parts.length === 4) {
    const [major, minor, patch] = parts
    return patch === '0' ? `${major}.${minor}` : `${major}.${minor}.${patch}`
  }
  if (parts.length === 3 && Number(parts[0]) < 26) {
    const [minor, patch] = parts
    return patch === '0' ? `1.${minor}` : `1.${minor}.${patch}`
  }
  return undefined
}

/**
 * Gerçek sürüm listesi: Mojang version manifest (v2, SHA1'li) + loader meta API'leri.
 * 26.x ve 1.x numaralandırması bir arada olduğundan sıralama her zaman Mojang'ın
 * releaseTime alanına göre yapılır; loader sürümleri bağlı oldukları Minecraft
 * sürümünün tarihini alır.
 */
export class RemoteVersionService implements VersionService {
  private readonly requirements = new Map<string, JavaRequirement>()

  async list(loader: LoaderType): Promise<GameVersion[]> {
    const manifest = await this.manifest()
    const byId = new Map(manifest.versions.map((v) => [v.id, v]))
    const out: GameVersion[] = []

    const push = (mc: MojangVersion, loaderVersion?: string): void => {
      out.push({
        key: versionKey(loader, mc.id, loaderVersion),
        loader,
        minecraftVersion: mc.id,
        loaderVersion,
        channel: mc.type as VersionChannel,
        releaseTime: mc.releaseTime
      })
    }

    const listed = manifest.versions.filter((v) => v.type === 'release' || v.type === 'snapshot')

    switch (loader) {
      case 'vanilla':
        for (const v of listed) push(v)
        break

      case 'fabric':
      case 'quilt': {
        const base = loader === 'fabric' ? FABRIC_META : QUILT_META
        const [games, loaders] = await Promise.all([
          cachedJson<MetaVersion[]>(`${base}/versions/game`, cacheFile(`${loader}-game.json`), LIST_TTL_MS),
          cachedJson<MetaVersion[]>(`${base}/versions/loader`, cacheFile(`${loader}-loader.json`), LIST_TTL_MS)
        ])
        // Fabric "stable" alanı verir; Quilt'te beta olmayan en yeni sürüm.
        const latest = loaders.find((l) => l.stable !== false && !isUnstable(l.version)) ?? loaders[0]
        if (!latest) break
        const supported = new Set(games.map((g) => g.version))
        for (const v of listed) if (supported.has(v.id)) push(v, latest.version)
        break
      }

      case 'forge': {
        const { promos } = await cachedJson<{ promos: Record<string, string> }>(
          FORGE_PROMOTIONS,
          cacheFile('forge-promotions.json'),
          LIST_TTL_MS
        )
        for (const v of listed) {
          const forge = promos[`${v.id}-recommended`] ?? promos[`${v.id}-latest`]
          if (forge) push(v, forge)
        }
        break
      }

      case 'neoforge': {
        const { versions } = await cachedJson<{ versions: string[] }>(
          NEOFORGE_VERSIONS,
          cacheFile('neoforge-versions.json'),
          LIST_TTL_MS
        )
        // Liste eskiden yeniye; her Minecraft sürümü için en yeni kararlı, yoksa en yeni beta.
        const best = new Map<string, string>()
        for (const nv of versions) {
          const mc = neoforgeToMinecraft(nv)
          if (!mc || !byId.has(mc)) continue
          const current = best.get(mc)
          if (!current || !isUnstable(nv) || isUnstable(current)) best.set(mc, nv)
        }
        for (const v of listed) {
          const nv = best.get(v.id)
          if (nv) push(v, nv)
        }
        break
      }
    }

    return out.sort((a, b) => Date.parse(b.releaseTime) - Date.parse(a.releaseTime))
  }

  async find(key: string): Promise<GameVersion | undefined> {
    let spec
    try {
      spec = parseVersionKey(key)
    } catch {
      return undefined
    }
    const listedMatch = (await this.list(spec.loader).catch(() => [])).find((v) => v.key === key)
    if (listedMatch) return listedMatch

    // Listede olmayan ama geçerli bir anahtar (ör. config'de sabitlenmiş eski loader sürümü).
    const mc = await this.getVanillaInfo(spec.minecraftVersion).catch(() => undefined)
    if (!mc) return undefined
    const manifest = await this.manifest()
    const type = manifest.versions.find((v) => v.id === mc.id)?.type
    return {
      key,
      loader: spec.loader,
      minecraftVersion: mc.id,
      loaderVersion: spec.loaderVersion,
      channel: type === 'release' ? 'release' : 'snapshot',
      releaseTime: mc.releaseTime
    }
  }

  async getVanillaInfo(minecraftVersion: string): Promise<VanillaVersionInfo> {
    const manifest = await this.manifest()
    const found = manifest.versions.find((v) => v.id === minecraftVersion)
    if (!found) throw new AppError('DOWNLOAD_FAILED', `Bilinmeyen Minecraft sürümü: ${minecraftVersion}`)
    return { id: found.id, url: found.url, sha1: found.sha1, releaseTime: found.releaseTime }
  }

  async getJavaRequirement(minecraftVersion: string): Promise<JavaRequirement> {
    const cached = this.requirements.get(minecraftVersion)
    if (cached) return cached

    // Önce kurulu version JSON'a bak (çevrimdışı başlatma), yoksa metadata'yı indir.
    const local = join(gameDirs.root, 'versions', minecraftVersion, `${minecraftVersion}.json`)
    let json = await readFile(local, 'utf8')
      .then((t) => JSON.parse(t) as { javaVersion?: JavaRequirement })
      .catch(() => undefined)
    if (!json) {
      const info = await this.getVanillaInfo(minecraftVersion)
      json = await fetchJson<{ javaVersion?: JavaRequirement }>(info.url)
    }
    // javaVersion alanı olmayan çok eski sürümler Java 8 ile çalışır.
    const requirement = json.javaVersion ?? { component: 'jre-legacy', majorVersion: 8 }
    this.requirements.set(minecraftVersion, requirement)
    return requirement
  }

  private manifest(): Promise<MojangManifest> {
    return cachedJson<MojangManifest>(MOJANG_MANIFEST, cacheFile('version_manifest_v2.json'), LIST_TTL_MS)
  }
}
