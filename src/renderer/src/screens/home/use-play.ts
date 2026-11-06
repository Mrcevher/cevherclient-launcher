import { useTranslation } from 'react-i18next'
import { AppError } from '@shared/errors'
import type { Language, ServerProfile } from '@shared/types'
import { localize } from '@shared/utils'
import { reportError } from '../../lib/errors'
import { useConfigStore } from '../../store/config-store'
import { useLaunchStore } from '../../store/launch-store'

/**
 * Oyna butonunun davranışı: seçili sunucu profilini hazırla, oyunu başlat ve
 * doğrudan sunucuya bağlan. Sunucunun çevrimdışı olduğu zaten biliniyorsa
 * LINK START animasyonu hiç oynatılmadan kullanıcı bilgilendirilir.
 */
export function usePlay(server: ServerProfile | null): () => void {
  const { i18n } = useTranslation()
  const status = useConfigStore((s) => (server ? s.serverStatus[server.id] : undefined))
  const start = useLaunchStore((s) => s.start)

  return () => {
    if (!server) return
    const name = localize(server.name, i18n.language as Language)
    if (status && !status.online) {
      reportError(new AppError('SERVER_OFFLINE', server.address, { server: name }))
      return
    }
    void start({ kind: 'server', serverId: server.id }, name)
  }
}
