package net.cevherclient.auth;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
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
 * girer.
 *
 * Roller (config: role):
 *   standalone — bileti olmayan bağlantılar reddedilir (eski davranış).
 *   lobby      — Velocity'nin ilk sunucusu: launcher'la gelen şifresiz girer, gelmeyen oyunda
 *                /login veya /register ile şifre girer. Launcher API burada çalışır.
 *   gate       — arka sunucu (survival): yalnızca lobide giriş yapmış oyuncuları alır.
 */
public final class CevherAuthPlugin extends JavaPlugin {
	private AccountStore accounts;
	private SessionStore sessions;
	private AuthHttpServer http;
	private LeaderOsSync site;
	private LobbyLogin lobbyLogin;
	private Guvenlik guvenlik;
	private ModDogrulama modDogrulama;
	private RiskTakip riskTakip;
	/** Op adları (küçük harf); HTTP iş parçacığından okunduğu için ana iş parçacığında yenilenir. */
	private volatile Set<String> operatorNames = Set.of();
	/** Sunucuda daha önce oynamış oyuncu adları (küçük harf); op adları gibi korunur. */
	private volatile Set<String> playedNames = Set.of();
	private volatile Set<String> trustedProxies = Set.of("loopback");
	private String role = "standalone";

	@Override
	public void onEnable() {
		saveDefaultConfig();
		role = getConfig().getString("role", "standalone").trim().toLowerCase(Locale.ROOT);
		if (!role.equals("lobby") && !role.equals("gate")) {
			role = "standalone";
		}

		// Güvenlik katmanı her rolde ilk kaydedilir: aynı öncelikte (LOWEST) önce çalışır.
		guvenlik = new Guvenlik(this);
		getServer().getPluginManager().registerEvents(guvenlik, this);
		getServer().getScheduler().runTaskTimerAsynchronously(this, guvenlik::cleanup, 20L * 60, 20L * 60);
		riskTakip = new RiskTakip(this);
		getServer().getPluginManager().registerEvents(riskTakip, this);
		riskTakip.baslat();
		modDogrulama = new ModDogrulama(this);

		if (role.equals("gate")) {
			enableGate();
			return;
		}

		accounts = new AccountStore(getDataFolder().toPath().resolve("accounts.json"));
		sessions = new SessionStore(getDataFolder().toPath().resolve("sessions.json"), getDataFolder().toPath().resolve("reconnect.json"), accounts);
		try {
			accounts.load();
			sessions.load();
		} catch (IOException | RuntimeException e) {
			// Eklentiyi kapatmak giriş kapısını da kaldırırdı: bunun yerine tüm girişler reddedilir.
			getLogger().log(Level.SEVERE, "Hesap dosyaları okunamadı; giriş kapısı kapalı kalacak (tüm girişler reddedilir). accounts.json / sessions.json dosyalarını onarıp sunucuyu yeniden başlatın", e);
			if (role.equals("lobby") || getConfig().getBoolean("require-launcher", true)) {
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

		if (role.equals("lobby")) {
			ensureNetworkSecret();
			PasswordLogFilter.install();
			lobbyLogin = new LobbyLogin(this);
			getServer().getPluginManager().registerEvents(lobbyLogin, this);
			getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
			lobbyLogin.start();
			getLogger().info("Lobi rolü: Cevherclient ile gelen şifresiz girer, gelmeyen oyunda /login veya /register yapar");
		}

		JoinListener joinListener = new JoinListener(this);
		LobbyLogin lobby = lobbyLogin;
		getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
			try {
				sessions.cleanup();
				if (http != null) {
					http.cleanup();
				}
				joinListener.cleanup();
				if (lobby != null) {
					lobby.cleanup();
				}
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

	/** Kapı rolü: hesap dosyası ve launcher API'si yok; her giriş lobiye sorulur. */
	private void enableGate() {
		String secret = getConfig().getString("network.secret", "");
		if (secret == null || secret.length() < 16) {
			getLogger().severe("Kapı rolü: network.secret boş veya kısa — lobi sunucusundaki network.secret değerini buraya kopyalayın. O zamana dek kimse giremez.");
		}
		getServer().getPluginManager().registerEvents(new GateListener(this), this);
		PluginCommand command = getCommand("cevherauth");
		if (command != null) {
			command.setExecutor((sender, cmd, label, args) -> {
				if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
					reloadConfig();
					guvenlik.yenile();
					modDogrulama.yenile();
					riskTakip.yenile();
					sender.sendMessage("§aCevherAuth (kapı) ayarları yeniden yüklendi.");
				} else if (AdminCommand.engelKomutu(this, sender, args)) {
					return true;
				} else if (AdminCommand.riskKomutu(this, sender, args)) {
					return true;
				} else {
					sender.sendMessage("§eBu sunucu kapı rolünde; hesap komutlarını lobi sunucusunda kullanın.");
				}
				return true;
			});
		}
		getLogger().info("Kapı rolü: yalnızca lobide giriş yapmış oyuncular alınır (" + getConfig().getString("network.lobby-api", "http://127.0.0.1:25580") + ")");
	}

	/** Lobi ilk açılışta ortak sırrı üretir; kapı sunucusunun config'ine kopyalanır. */
	private void ensureNetworkSecret() {
		String secret = getConfig().getString("network.secret", "");
		if (secret != null && secret.length() >= 16) {
			return;
		}
		getConfig().set("network.secret", Passwords.randomToken());
		saveConfig();
		getLogger().warning("network.secret üretildi (plugins/CevherAuth/config.yml). Aynı değeri survival'daki CevherAuth config'ine yazın.");
	}

	@Override
	public void onDisable() {
		if (riskTakip != null) {
			riskTakip.durdur();
		}
		if (lobbyLogin != null) {
			lobbyLogin.stop();
		}
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

	AuthHttpServer http() {
		return http;
	}

	Guvenlik guvenlik() {
		return guvenlik;
	}

	RiskTakip riskTakip() {
		return riskTakip;
	}

	/** Diğer eklentiler (CevherKoruma) için: risk bulgusu ekler. seviye = TEMIZ/DUSUK/ORTA/YUKSEK/KRITIK. */
	public void disRiskEkle(String ad, String ip, String seviye, String kaynak, String detay) {
		if (riskTakip != null) {
			riskTakip.ekle(ad, ip, RiskTakip.Seviye.bul(seviye), kaynak, detay);
		}
	}

	/** Diğer eklentiler için: oyuncunun risk özeti (renk kodsuz satırlar). */
	public java.util.List<String> disRiskRaporu(String ad) {
		java.util.List<String> out = new java.util.ArrayList<>();
		if (riskTakip != null) {
			for (String s : riskTakip.rapor(ad)) {
				out.add(s.replaceAll("&[0-9a-fk-or]", ""));
			}
		}
		return out;
	}

	ModDogrulama modDogrulama() {
		return modDogrulama;
	}

	boolean isGate() {
		return role.equals("gate");
	}

	/** Lobi rolünde oyun içi giriş yöneticisi; diğer rollerde null. */
	LobbyLogin lobbyLogin() {
		return lobbyLogin;
	}

	long sessionLifetimeMs() {
		return Math.max(1, getConfig().getLong("session-days", 30)) * 24L * 60 * 60 * 1000;
	}

	long ticketLifetimeMs() {
		return Math.max(30, getConfig().getLong("ticket-seconds", 300)) * 1000L;
	}

	/** Yeniden bağlanma payı; negatif ayar (-1) süresiz demektir: oyuncu Oyna'ya yeniden basana kadar. */
	long reconnectGraceMs() {
		long seconds = getConfig().getLong("reconnect-grace-seconds", -1);
		return seconds < 0 ? Long.MAX_VALUE : seconds * 1000L;
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

	/** Op adı mı (bu sunucunun ya da op-name-sources dosyalarındaki op'lar)? */
	boolean isOperatorName(String username) {
		return protectOpNames() && operatorNames.contains(username.toLowerCase(Locale.ROOT));
	}

	/**
	 * Oyun içi /register için korunan ad. Op adları her zaman korunur. "Oynamış" adlar yalnızca
	 * in-game-login.protect-played-names açıksa korunur: lisanssız sunucuda eski oyuncuların
	 * çoğunun hesabı yoktur ve kendi adlarıyla kayıt olabilmeleri gerekir.
	 */
	boolean isReservedForInGame(String username) {
		if (isOperatorName(username)) {
			return true;
		}
		return getConfig().getBoolean("in-game-login.protect-played-names", false) && isReservedName(username);
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
		guvenlik.yenile();
		modDogrulama.yenile();
		riskTakip.yenile();
		refreshTrustedProxies();
		refreshPlayedNames();
		refreshOperators();
	}

	/**
	 * Korunan "oynamış" adlar. Lobide bu sunucunun kendi oyuncuları sayılmaz (lobiye herkes uğrar,
	 * kayıt olamadan korunmaya başlarlardı); yalnızca played-name-sources dosyaları (ör. survival'ın
	 * usercache.json'u) okunur. Diğer rollerde sunucunun kendi oyuncuları da eklenir.
	 */
	private void refreshPlayedNames() {
		Set<String> names = new HashSet<>();
		if (!role.equals("lobby")) {
			for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
				if (p.getName() != null) {
					names.add(p.getName().toLowerCase(Locale.ROOT));
				}
			}
		}
		names.addAll(readNames("played-name-sources"));
		playedNames = Set.copyOf(names);
	}

	/** Bu sunucunun op'ları + op-name-sources dosyaları (ör. ana sunucunun ops.json'u). */
	private void refreshOperators() {
		Set<String> names = new HashSet<>();
		for (OfflinePlayer p : Bukkit.getOperators()) {
			if (p.getName() != null) {
				names.add(p.getName().toLowerCase(Locale.ROOT));
			}
		}
		names.addAll(readNames("op-name-sources"));
		operatorNames = Set.copyOf(names);
	}

	/** usercache.json / ops.json biçimindeki ([{ "name": ... }]) dosyalardan adları okur. */
	private Set<String> readNames(String configKey) {
		Set<String> names = new HashSet<>();
		Path serverRoot = getDataFolder().toPath().toAbsolutePath().getParent().getParent();
		for (String source : getConfig().getStringList(configKey)) {
			Path path = serverRoot.resolve(source).normalize();
			try {
				JsonElement root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
				if (root.isJsonArray()) {
					for (JsonElement el : root.getAsJsonArray()) {
						if (el.isJsonObject() && el.getAsJsonObject().has("name")) {
							names.add(el.getAsJsonObject().get("name").getAsString().toLowerCase(Locale.ROOT));
						}
					}
				}
			} catch (IOException | RuntimeException e) {
				getLogger().warning(configKey + " okunamadı: " + path + " (" + e.getMessage() + ")");
			}
		}
		return names;
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
