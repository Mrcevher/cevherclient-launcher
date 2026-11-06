package net.cevherclient.auth;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * CevherAuth: Cevherclient launcher hesapları.
 *
 * Oyuncu hesabını launcher'da açar veya launcher'da giriş yapar; Oyna'ya bastığında launcher
 * bu eklentiden tek kullanımlık bir giriş bileti alır ve oyuncu sunucuya hiçbir şey yazmadan
 * girer. Bileti olmayan bağlantılar reddedilir (lisanssız sunucuda kimlik sahteciliğine karşı).
 */
public final class CevherAuthPlugin extends JavaPlugin {
	private AccountStore accounts;
	private SessionStore sessions;
	private AuthHttpServer http;
	private LeaderOsSync site;
	/** Op adları (küçük harf); HTTP iş parçacığından okunduğu için ana iş parçacığında yenilenir. */
	private volatile Set<String> operatorNames = Set.of();
	/** Sunucuda daha önce oynamış oyuncu adları (küçük harf); op adları gibi korunur. */
	private volatile Set<String> playedNames = Set.of();
	private volatile Set<String> trustedProxies = Set.of("loopback");

	@Override
	public void onEnable() {
		saveDefaultConfig();
		accounts = new AccountStore(getDataFolder().toPath().resolve("accounts.json"));
		sessions = new SessionStore(getDataFolder().toPath().resolve("sessions.json"), accounts);
		try {
			accounts.load();
			sessions.load();
		} catch (IOException | RuntimeException e) {
			// Eklentiyi kapatmak giriş kapısını da kaldırırdı: bunun yerine tüm girişler reddedilir.
			getLogger().log(Level.SEVERE, "Hesap dosyaları okunamadı; giriş kapısı kapalı kalacak (tüm girişler reddedilir). accounts.json / sessions.json dosyalarını onarıp sunucuyu yeniden başlatın", e);
			if (getConfig().getBoolean("require-launcher", true)) {
				getServer().getPluginManager().registerEvents(new LockdownListener(this), this);
			}
			return;
		}

		refreshOperators();
		refreshPlayedNames();
		refreshTrustedProxies();
		// Op listesini ve oynamış oyuncu adlarını periyodik güncelle; süresi dolmuş oturumları temizle.
		getServer().getScheduler().runTaskTimer(this, this::refreshOperators, 20L * 60, 20L * 60);
		getServer().getScheduler().runTaskTimer(this, this::refreshPlayedNames, 20L * 60 * 5, 20L * 60 * 5);
		JoinListener joinListener = new JoinListener(this);
		getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
			try {
				sessions.cleanup();
				if (http != null) {
					http.cleanup();
				}
				joinListener.cleanup();
			} catch (IOException e) {
				getLogger().log(Level.WARNING, "Oturum temizliği yazılamadı", e);
			}
		}, 20L * 60, 20L * 60 * 5);

		getServer().getPluginManager().registerEvents(joinListener, this);
		PluginCommand command = getCommand("cevherauth");
		if (command != null) {
			AdminCommand admin = new AdminCommand(this);
			command.setExecutor(admin);
			command.setTabCompleter(admin);
		}

		site = new LeaderOsSync(this);
		getLogger().info(site.enabled()
			? "LeaderOS site senkronu açık (" + getConfig().getString("leaderos.url") + ")"
			: "LeaderOS site senkronu kapalı (config: leaderos.enabled / url / api-key)");

		String bind = getConfig().getString("http.bind", "0.0.0.0");
		int port = getConfig().getInt("http.port", 25580);
		http = new AuthHttpServer(this);
		try {
			http.start(bind, port);
			getLogger().info("Launcher API " + bind + ":" + port + " adresinde açık — " + accounts.size() + " hesap");
		} catch (IOException e) {
			// API olmadan kimse bilet alamaz; require-launcher açıksa sunucuya kimse giremez.
			getLogger().log(Level.SEVERE, "Launcher API " + port + " portunda açılamadı (port kullanımda olabilir)", e);
		}
	}

	@Override
	public void onDisable() {
		if (http != null) {
			http.stop();
		}
		if (site != null) {
			site.shutdown();
		}
		if (sessions != null) {
			try {
				sessions.flush();
			} catch (IOException e) {
				getLogger().log(Level.WARNING, "Oturum dosyası yazılamadı", e);
			}
		}
	}

	AccountStore accounts() {
		return accounts;
	}

	SessionStore sessions() {
		return sessions;
	}

	LeaderOsSync site() {
		return site;
	}

	long sessionLifetimeMs() {
		return Math.max(1, getConfig().getLong("session-days", 30)) * 24L * 60 * 60 * 1000;
	}

	long ticketLifetimeMs() {
		return Math.max(30, getConfig().getLong("ticket-seconds", 300)) * 1000L;
	}

	long reconnectGraceMs() {
		return Math.max(0, getConfig().getLong("reconnect-grace-seconds", 120)) * 1000L;
	}

	boolean protectOpNames() {
		return getConfig().getBoolean("protect-op-names", true);
	}

	int maxSessionsPerAccount() {
		return getConfig().getInt("max-sessions-per-account", 10);
	}

	/** Op adı veya (ayar açıksa) sunucuda daha önce oynamış bir oyuncunun adı: yalnızca yerel kayıt/yönetici alabilir. */
	boolean isReservedName(String username) {
		String key = username.toLowerCase(Locale.ROOT);
		if (protectOpNames() && operatorNames.contains(key)) {
			return true;
		}
		return getConfig().getBoolean("protect-played-names", true) && playedNames.contains(key);
	}

	boolean isTrustedProxy(String ipKey) {
		return trustedProxies.contains(ipKey);
	}

	Component message(String key) {
		String raw = getConfig().getString("messages." + key, key);
		return LegacyComponentSerializer.legacyAmpersand().deserialize(raw);
	}

	private void refreshTrustedProxies() {
		List<String> entries = getConfig().getStringList("http.trusted-proxies");
		trustedProxies = AuthHttpServer.parseProxies(entries.isEmpty() ? List.of("127.0.0.1", "::1") : entries);
	}

	void reloadSettings() {
		reloadConfig();
		refreshTrustedProxies();
	}

	private void refreshPlayedNames() {
		playedNames = java.util.Arrays.stream(Bukkit.getOfflinePlayers())
			.map(OfflinePlayer::getName)
			.filter(n -> n != null)
			.map(n -> n.toLowerCase(Locale.ROOT))
			.collect(Collectors.toUnmodifiableSet());
	}

	private void refreshOperators() {
		operatorNames = Bukkit.getOperators().stream()
			.map(OfflinePlayer::getName)
			.filter(n -> n != null)
			.map(n -> n.toLowerCase(Locale.ROOT))
			.collect(Collectors.toUnmodifiableSet());
	}

	/**
	 * IP karşılaştırma anahtarı. Aynı bilgisayardan gelen bağlantılar 127.0.0.1 veya ::1 olarak
	 * görünebilir; ikisi de "loopback" sayılır. IPv4 eşlemeli IPv6 adresleri IPv4'e indirgenir.
	 */
	static String ipKey(InetAddress address) {
		if (address == null) {
			return "unknown";
		}
		if (address.isLoopbackAddress()) {
			return "loopback";
		}
		if (address instanceof Inet6Address v6) {
			byte[] b = v6.getAddress();
			boolean mapped = true;
			for (int i = 0; i < 10; i++) {
				mapped &= b[i] == 0;
			}
			if (mapped && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) {
				return (b[12] & 0xff) + "." + (b[13] & 0xff) + "." + (b[14] & 0xff) + "." + (b[15] & 0xff);
			}
		}
		return address.getHostAddress();
	}
}
