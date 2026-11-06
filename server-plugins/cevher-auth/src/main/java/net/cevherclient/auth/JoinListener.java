package net.cevherclient.auth;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

/**
 * Sunucuya bağlanan her oyuncunun launcher biletini kontrol eder. Bilet geçerliyse oyuncu
 * hiçbir şey yazmadan içeri girer; değilse "Cevherclient ile gir" mesajıyla geri çevrilir.
 */
final class JoinListener implements Listener {
	/** IP başına biletsiz/eşleşmeyen bağlantı denemeleri; tahmin amaçlı taramayı yavaşlatır. */
	private final Throttle misses = new Throttle(10 * 60_000L);
	private final CevherAuthPlugin plugin;

	JoinListener(CevherAuthPlugin plugin) {
		this.plugin = plugin;
	}

	void cleanup() {
		misses.cleanup();
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPreLogin(AsyncPlayerPreLoginEvent event) {
		if (!plugin.getConfig().getBoolean("require-launcher", true)) {
			return;
		}
		String name = event.getName();
		String ip = CevherAuthPlugin.ipKey(event.getAddress());
		if (misses.count(ip) >= plugin.getConfig().getInt("max-ticket-misses", 20)) {
			event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, plugin.message("no-ticket"));
			return;
		}
		boolean checkIp = plugin.getConfig().getBoolean("check-ip", true);

		switch (plugin.sessions().consumeTicket(name, ip, checkIp, plugin.reconnectGraceMs())) {
			case OK -> {
				// Launcher'da doğrulanmış oyuncu: oyunda şifre sorulmaz.
			}
			case WRONG_NAME -> {
				misses.record(ip);
				event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, plugin.message("wrong-name"));
			}
			case MISSING, WRONG_IP -> {
				misses.record(ip);
				plugin.getLogger().info(name + " biletsiz bağlanmaya çalıştı (" + ip + "), reddedildi");
				event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, plugin.message("no-ticket"));
			}
		}
	}
}
