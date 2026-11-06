import type { DeviceTier, QualityResult, ServerProfile } from '@shared/types'
import { delay } from '../mock/mock-utils'
import type { ProgressCallback, QualityService, SettingsService } from '../types'

const TITLES: Record<DeviceTier, string> = {
  low: 'Super Duper Vanilla',
  medium: 'Photon Shaders',
  high: 'Complementary Shaders - Unbound'
}

/** CEVHER_MOCK_SERVICES=1: ağ ve disk olmadan, yalnızca ilerleme gösterip seçimi kaydeder. */
export class MockQualityService implements QualityService {
  constructor(private readonly settings: SettingsService) {}

  async apply(_server: ServerProfile, tier: DeviceTier, onProgress: ProgressCallback, remember = true): Promise<QualityResult> {
    for (let i = 0; i <= 10; i++) {
      onProgress(i / 10)
      await delay(120)
    }
    await this.settings.update({ deviceTier: remember ? tier : null })
    return { tier, shaderTitle: TITLES[tier] }
  }
}
