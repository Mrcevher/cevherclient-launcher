import { AppError } from '@shared/errors'
import { LOADER_TYPES, type LoaderType } from '@shared/types'

/** Sürüm anahtarının çözülmüş hali: "<loader>:<mcVersion>[:<loaderVersion>]" */
export interface VersionSpec {
  loader: LoaderType
  minecraftVersion: string
  loaderVersion?: string
}

export function versionKey(loader: LoaderType, mcVersion: string, loaderVersion?: string): string {
  return loaderVersion ? `${loader}:${mcVersion}:${loaderVersion}` : `${loader}:${mcVersion}`
}

/** Geçersiz anahtarda DOWNLOAD_FAILED fırlatır. */
export function parseVersionKey(key: string): VersionSpec {
  const [loader, minecraftVersion, loaderVersion, ...rest] = key.split(':')
  const valid =
    (LOADER_TYPES as readonly string[]).includes(loader ?? '') &&
    !!minecraftVersion &&
    rest.length === 0 &&
    (loader === 'vanilla' ? loaderVersion === undefined : !!loaderVersion)
  if (!valid) throw new AppError('DOWNLOAD_FAILED', `Geçersiz sürüm anahtarı: ${key}`)
  return { loader: loader as LoaderType, minecraftVersion: minecraftVersion!, loaderVersion }
}
