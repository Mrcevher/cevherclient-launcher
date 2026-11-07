package net.cevherclient.auth;

import io.papermc.paper.connection.PlayerLoginConnection;
import java.util.concurrent.TimeUnit;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

/**
 * Sunucuya bağlanan her oyuncunun launcher biletini kontrol eder. Bilet geçerliyse oyuncu
 * hiçbir şey yazmadan içeri girer; değilse "Cevherclient ile gir" mesajıyla geri çevrilir.
 *
 * Offline modda Minecraft girişi yalnızca bir ad taşır. Bu yüzden bilet, onu alan oyun
 * istemcisine bağlanır: login aşamasında istemciden bilet sırrıyla üretilmiş bir kanıt
 * istenir ({@link TicketProof}); kanıt doğrulanmadan bilet tüketilmez.
 */
final class JoinListener implements Listener {
	/** İstemcinin kanıt cookie'sine yanıt vermesi için beklenen en uzun süre. */
	private static final long PROOF_TIMEOUT_MS = 5_000;

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

		SessionStore.TicketLookup found = plugin.sessions().findTicket(name, ip, checkIp);
		switch (found.check()) {
			case OK -> {
				if (plugin.getConfig().getBoolean("require-proof", true)) {
					Proof proof = requestProof(event, found.ticket(), name);
					if (proof != Proof.VALID) {
						misses.record(ip);
						plugin.getLogger().warning(name + " bileti kanıtsız kullanılmaya çalışıldı (" + ip + ", " + proof + "), reddedildi");
						event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
							plugin.message(proof == Proof.NONE ? "outdated-launcher" : "no-ticket"));
						return;
					}
				}
				if (!plugin.sessions().confirm(found, ip, plugin.reconnectGraceMs())) {
					// Aynı bilet başka bağlantıyla kullanıldı veya bu arada iptal edildi.
					event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, plugin.message("no-ticket"));
				}
				// Launcher'da doğrulanmış oyuncu: oyunda şifre sorulmaz.
			}
			case WRONG_NAME -> {
				misses.record(ip);
				event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, plugin.message("wrong-name"));
			}
			case MISSING, WRONG_IP -> {
				misses.record(ip);
				if (found.check() == SessionStore.TicketCheck.WRONG_IP) {
					// Sık neden: launcher bileti Cloudflare/proxy üzerinden IPv6 ile, oyun IPv4 ile bağlanır.
					plugin.getLogger().info(name + " biletinin IP'si uyuşmuyor (bilet: " + plugin.sessions().pendingTicketIp(name)
						+ ", bağlantı: " + ip + "), reddedildi. Tünel/IPv6 kullanılıyorsa check-ip: false yapın; bağı require-proof sağlar.");
				} else {
					plugin.getLogger().info(name + " biletsiz bağlanmaya çalıştı (" + ip + "), reddedildi");
				}
				event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, plugin.message("no-ticket"));
			}
		}
	}

	private enum Proof {
		/** Kanıt doğru. */
		VALID,
		/** İstemci kanıt vermedi (mod yok / eski launcher / yanıt gelmedi). */
		NONE,
		/** Kanıt verildi ama yanlış: sırrı bilmeyen biri. */
		INVALID
	}

	/**
	 * Login aşamasında istemciden {@code cevherauth:proof/<nonce>} cookie'sini ister. Olay
	 * zaten asenkron iş parçacığında çalışır; yanıt en fazla {@link #PROOF_TIMEOUT_MS} beklenir.
	 */
	private Proof requestProof(AsyncPlayerPreLoginEvent event, SessionStore.Ticket ticket, String name) {
		PlayerLoginConnection connection = event.getConnection();
		if (connection == null || ticket == null) {
			return Proof.NONE;
		}
		String nonce = TicketProof.newNonce();
		byte[] response;
		try {
			response = connection.retrieveCookie(new NamespacedKey(plugin, TicketProof.KEY_PREFIX + nonce))
				.get(PROOF_TIMEOUT_MS, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return Proof.NONE;
		} catch (Exception e) {
			return Proof.NONE;
		}
		if (response == null || response.length == 0) {
			return Proof.NONE;
		}
		return TicketProof.verify(ticket.secret(), nonce, name, response) ? Proof.VALID : Proof.INVALID;
	}
}
