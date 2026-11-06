/**
 * Servis kabı (composition root). Hangi implementasyonun kullanılacağına yalnızca
 * burada karar verilir; IPC katmanı ve renderer değişmez.
 *
 * Tüm servisler gerçek. Arayüzü ağ ve indirme olmadan geliştirmek
 * için Faz 1 mock'ları hâlâ seçilebilir:
 *   CEVHER_MOCK_SERVICES=1 npm run dev
 */

import type { EventSink } from '../utils/events'
import { AccountAuthService } from './auth/account-auth-service'
import { JsonRemoteConfigService } from './config/json-remote-config-service'
import { MinecraftInstallService } from './install/minecraft-install-service'
import { MockInstallService } from './install/mock-install-service'
import { MinecraftJavaService } from './java/minecraft-java-service'
import { MockJavaService } from './java/mock-java-service'
import { MinecraftLaunchService } from './launch/minecraft-launch-service'
import { MockLaunchService } from './launch/mock-launch-service'
import { MockModpackService } from './modpack/mock-modpack-service'
import { MrpackModpackService } from './modpack/mrpack-modpack-service'
import { ModrinthPerformancePackService } from './mods/performance-pack-service'
import { ModrinthUserModsService } from './mods/user-mods-service'
import { MockQualityService } from './quality/mock-quality-service'
import { ModrinthQualityService } from './quality/quality-service'
import { FileSettingsService } from './settings/file-settings-service'
import { SiteService } from './site/site-service'
import { MockServerStatusService } from './status/mock-server-status-service'
import { SlpServerStatusService } from './status/slp-server-status-service'
import type { Services } from './types'
import { MockVersionService } from './versions/mock-version-service'
import { RemoteVersionService } from './versions/remote-version-service'

export function createServices(events: EventSink): Services {
  const settings = new FileSettingsService()
  const config = new JsonRemoteConfigService(settings)
  // Giriş her iki modda da aynı: config'de authServerUrl varsa hesap sunucusu, yoksa yalnızca kullanıcı adı.
  const auth = new AccountAuthService(config)
  const performance = new ModrinthPerformancePackService()
  const userMods = new ModrinthUserModsService(performance)
  // Web sitesi (LeaderOS): Mağaza, Profil ve Haberler.
  const site = new SiteService(auth, config)

  if (process.env.CEVHER_MOCK_SERVICES === '1') {
    const modpack = new MockModpackService()
    const status = new MockServerStatusService()
    const versions = new MockVersionService()
    const install = new MockInstallService(events)
    const java = new MockJavaService(settings)
    const launch = new MockLaunchService({ auth, config, status, versions, install, modpack, java, settings }, events)
    const quality = new MockQualityService(settings)
    return { auth, site, config, status, versions, install, modpack, java, settings, launch, performance, userMods, quality }
  }

  const status = new SlpServerStatusService()
  const modpack = new MrpackModpackService()
  const versions = new RemoteVersionService()
  const java = new MinecraftJavaService(settings)
  const install = new MinecraftInstallService(events, versions, java)
  const launch = new MinecraftLaunchService({ auth, config, status, versions, install, modpack, java, settings, performance }, events)
  const quality = new ModrinthQualityService(performance, settings)
  return { auth, site, config, status, versions, install, modpack, java, settings, launch, performance, userMods, quality }
}
