package net.cevherclient.auth;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

/**
 * Hesap dosyaları okunamadığında kaydedilir: giriş kapısı açık kalmasın diye tüm girişleri
 * reddeder (kapalı başarısızlık). Dosyalar onarılıp sunucu yeniden başlatılınca kalkar.
 */
final class LockdownListener implements Listener {
	private final CevherAuthPlugin plugin;

	LockdownListener(CevherAuthPlugin plugin) {
		this.plugin = plugin;
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPreLogin(AsyncPlayerPreLoginEvent event) {
		event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, plugin.message("lockdown"));
	}
}
