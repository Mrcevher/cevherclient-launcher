import { existsSync } from 'node:fs'
import { AppError } from '@shared/errors'
import type { JavaInstallation } from '@shared/types'
import { maybeFail } from '../mock/mock-utils'
import type { JavaRequirement, JavaService, ProgressCallback, SettingsService } from '../types'

/** Platforma göre gerçekçi görünen sahte Java yolları. */
function mockJavaPath(major: number): string {
  switch (process.platform) {
    case 'win32':
      return `C:\\Program Files\\Eclipse Adoptium\\jdk-${major}\\bin\\javaw.exe`
    case 'darwin':
      return `/Library/Java/JavaVirtualMachines/temurin-${major}.jdk/Contents/Home/bin/java`
    default:
      return `/usr/lib/jvm/temurin-${major}-jdk/bin/java`
  }
}

/**
 * Faz 1 mock'u: sistemde Java 21 ve 25 kurulu varsayılır. Kullanıcı özel bir yol
 * seçtiyse dosyanın gerçekten var olup olmadığı kontrol edilir (yoksa JAVA_NOT_FOUND).
 * Faz 2'de: yaygın kurulum dizinleri taranacak, `java -version` çalıştırılacak ve
 * gerekli sürüm yoksa (sürüm metadata'sındaki javaVersion.majorVersion) indirilecek.
 */
export class MockJavaService implements JavaService {
  constructor(private readonly settings: SettingsService) {}

  async detect(): Promise<JavaInstallation[]> {
    return [21, 25].map((major) => ({
      path: mockJavaPath(major),
      majorVersion: major,
      vendor: 'Eclipse Temurin',
      source: 'auto' as const
    }))
  }

  async ensure(requirement: JavaRequirement, onProgress: ProgressCallback): Promise<JavaInstallation> {
    const java = await this.resolve(requirement.majorVersion)
    onProgress(1)
    return java
  }

  async resolve(requiredMajor: number): Promise<JavaInstallation> {
    maybeFail('JAVA_NOT_FOUND', { version: requiredMajor })
    const { javaPath } = await this.settings.get()

    if (javaPath) {
      if (!existsSync(javaPath)) {
        throw new AppError('JAVA_NOT_FOUND', javaPath, { version: requiredMajor })
      }
      // Mock: özel yolun gerekli sürüm olduğu varsayılır.
      return { path: javaPath, majorVersion: requiredMajor, vendor: 'custom', source: 'custom' }
    }

    // Gereksinimi karşılayan en düşük majör sürümü seç.
    const candidates = (await this.detect())
      .filter((j) => j.majorVersion >= requiredMajor)
      .sort((a, b) => a.majorVersion - b.majorVersion)
    const match = candidates[0]
    if (!match) throw new AppError('JAVA_NOT_FOUND', `Java ${requiredMajor}+ bulunamadı`, { version: requiredMajor })
    return match
  }
}
