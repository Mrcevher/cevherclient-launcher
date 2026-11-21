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
		// Lobi rolünde launcher'sız oyuncu reddedilmez, oyunda şifre girer (bkz. LobbyLogin).
		LobbyLogin lobby = plugin.lobbyLogin();
		if (lobby == null && !plugin.getConfig().getBoolean("require-launcher", true)) {
			return;
		}
		if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
			return; // Güvenlik katmanı (IP engeli / online sınırı) zaten reddetti.
		}
		String name = event.getName();
		String ip = CevherAuthPlugin.ipKey(event.getAddress());
		if (misses.count(ip) >= plugin.getConfig().getInt("max-ticket-misses", 20)) {
			event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, plugin.message("no-ticket"));
			return;
		}
		if (lobby != null) {
			// Offline modda "Arda" ile "arda" farklı oyunculardır: hesap kayıtlı yazımla kullanılmalı.
			AccountStore.Account account = plugin.accounts().get(name);
			if (account != null && !account.username.equals(name)) {
				event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, plugin.message("name-case")
					.replaceText(b -> b.matchLiteral("{ad}").replacement(account.username)));
				return;
			}
		}
		boolean checkIp = plugin.getConfig().getBoolean("check-ip", true);

		SessionStore.TicketLookup found = plugin.sessions().findTicket(name, ip, checkIp);
		String sorguModlar = null;
		switch (found.check()) {
			case OK -> {
				if (plugin.getConfig().getBoolean("require-proof", true)) {
					ProofResult pr = requestProof(event, found.ticket(), name);
					Proof proof = pr.proof();
					if (proof != Proof.VALID) {
						if (lobby != null) {
							// Lobide reddedilmez: kanıtı olmayan/yanlış olan oyuncuya oyunda şifre sorulur.
							// Yanlış kanıt yine sayılır (tekrarlayan denemeler max-ticket-misses ile durur).
							if (proof == Proof.INVALID) {
								misses.record(ip);
								plugin.getLogger().warning(name + " biletini yanlış kanıtla kullanmaya çalıştı (" + ip + "); oyunda şifre sorulacak");
							} else {
								plugin.getLogger().info(name + " biletli ama kanıtsız bağlandı (" + ip + "); oyunda şifre sorulacak");
							}
							return;
						}
						misses.record(ip);
						plugin.getLogger().warning(name + " bileti kanıtsız kullanılmaya çalışıldı (" + ip + ", " + proof + "), reddedildi");
						event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
							plugin.message(proof == Proof.NONE ? "outdated-launcher" : "no-ticket"));
						return;
					}
					sorguModlar = pr.mods();
					// Bilet sahibinin oyunu: yüklü mod listesi izinli mi? (Lobide de reddedilir;
					// aksi hâlde yasaklı modla gelen oyuncuya şifre sorulup içeri alınırdı.)
					String red = plugin.modDogrulama().kontrol(name, ip, pr.mods());
					if (red != null) {
						event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, plugin.message("mod-denied")
							.replaceText(b -> b.matchLiteral("{mod}").replacement(red)));
						return;
					}
				}
				if (!plugin.sessions().confirm(found, ip, plugin.reconnectGraceMs())) {
					// Aynı bilet başka bağlantıyla kullanıldı veya bu arada iptal edildi.
					// Lobide oyuncu reddedilmez; oyunda şifre sorulur.
					if (lobby == null) {
						event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, plugin.message("no-ticket"));
					}
					return;
				}
				// Launcher'da doğrulanmış oyuncu: oyunda şifre sorulmaz.
				if (lobby != null) {
					lobby.markLauncherVerified(name, ip);
				}
				// Oyun içi sorgu (OyunSorgu) aynı bilet sırrıyla imzalanır; sır lobide saklanır.
				if (sorguModlar != null || plugin.getConfig().getBoolean("require-proof", true)) {
					plugin.oyunSorgu().biletKaydet(name, found.ticket().secret(), sorguModlar);
				}
			}
			case WRONG_NAME -> {
				if (lobby != null) {
					return; // Oyunda şifre sorulur.
				}
				misses.record(ip);
				event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, plugin.message("wrong-name"));
			}
			case MISSING, WRONG_IP -> {
				if (lobby != null) {
					return; // Launcher'sız oyuncu: oyunda /login veya /register.
				}
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
	private record ProofResult(Proof proof, String mods) {
		static final ProofResult NONE = new ProofResult(Proof.NONE, null);
	}

	private ProofResult requestProof(AsyncPlayerPreLoginEvent event, SessionStore.Ticket ticket, String name) {
		PlayerLoginConnection connection = event.getConnection();
		if (connection == null || ticket == null) {
			return ProofResult.NONE;
		}
		String nonce = TicketProof.newNonce();
		byte[] response;
		try {
			response = connection.retrieveCookie(new NamespacedKey(plugin, TicketProof.KEY_PREFIX + nonce))
				.get(PROOF_TIMEOUT_MS, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return ProofResult.NONE;
		} catch (Exception e) {
			return ProofResult.NONE;
		}
		if (response == null || response.length == 0) {
			return ProofResult.NONE;
		}
		TicketProof.Result r = TicketProof.check(ticket.secret(), nonce, name, response);
		return new ProofResult(r.valid() ? Proof.VALID : Proof.INVALID, r.mods());
	}
}
