package net.cevherclient.auth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Launcher'ın konuştuğu küçük JSON API (yalnızca POST, gövde en çok 8 KB):
 *
 *   POST /api/register  { username, password }  → { ok, username, token }
 *   POST /api/login     { username, password }  → { ok, username, token }
 *   POST /api/join      { token }               → { ok, username, expiresIn }   (giriş bileti)
 *   POST /api/logout    { token }               → { ok }
 *   POST /api/password  { token, currentPassword, newPassword } → { ok, username, token }
 *   POST /api/site/profile, /api/store/listing, /api/store/buy  → bkz. {@link SiteBridge}
 *   GET  /api/health                            → { ok, service }
 *
 * Hatalarda { ok: false, error: "<KOD>" } döner; kodlar launcher'da Türkçe/İngilizce
 * mesaja çevrilir.
 *
 * Tarayıcı kaynaklı istekler (Origin / Sec-Fetch-Site başlığı olanlar) ve
 * {@code application/json} olmayan gövdeler reddedilir; böylece bir web sayfası yerel API'ye
 * form gönderip ayrıcalıklı "yerel" yolu kullanamaz.
 */
final class AuthHttpServer {
	private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
	private static final Pattern IP_LITERAL = Pattern.compile("^[0-9a-fA-F:.]{2,45}$");
	private static final int MAX_BODY = 8 * 1024;
	private static final long WINDOW_MS = 10 * 60_000L;
	/** Başarılı (IP, hesap) çiftinin hesap kilidinden muaf kaldığı süre. */
	private static final long KNOWN_PAIR_MS = 30L * 24 * 60 * 60_000L;
	private static final String DUMMY_HASH = Passwords.hash("cevher-dummy");

	/** İstemci IP anahtarı ve isteğin güvenilir biçimde sunucu makinesinden (aracısız) gelip gelmediği. */
	private record Client(String ip, boolean local) {
	}

	private final CevherAuthPlugin plugin;
	/** (IP, kullanıcı) çifti başına hatalı giriş; yalnızca o çiftin başarısında sıfırlanır. */
	private final Throttle loginPair = new Throttle(WINDOW_MS);
	/** IP başına toplam hatalı giriş; başarıyla sıfırlanmaz (kullanıcı adı püskürtmeye karşı). */
	private final Throttle loginIp = new Throttle(WINDOW_MS);
	/** Hesap başına toplam hatalı giriş (dağıtık tahmine karşı). */
	private final Throttle loginAccount = new Throttle(WINDOW_MS);
	/**
	 * Daha önce başarıyla kimlik doğrulamış (IP, hesap) çiftleri. Yalnızca hesap çapındaki kilitten
	 * muaftır: başkalarının hatalı denemeleri hesabın sahibini bilinen cihazından kilitleyemez.
	 */
	private final Throttle loginKnown = new Throttle(KNOWN_PAIR_MS);
	private final Throttle registerIp = new Throttle(WINDOW_MS);
	private final Map<String, AtomicInteger> connections = new ConcurrentHashMap<>();
	private HttpServer server;
	private ExecutorService executor;

	private final SiteBridge bridge;

	AuthHttpServer(CevherAuthPlugin plugin) {
		this.plugin = plugin;
		this.bridge = new SiteBridge(plugin);
	}

	void start(String bind, int port) throws IOException {
		// JDK sunucusunun istek/yanıt süre sınırları: yavaş gönderilen gövde işçi iş parçacığını
		// sonsuza dek tutamaz. Değerler HttpServer ilk kullanıldığında okunur; başka bir eklenti
		// önce başlattıysa onun değerleri geçerli kalır.
		setDefaultProperty("sun.net.httpserver.maxReqTime", "10");
		setDefaultProperty("sun.net.httpserver.maxRspTime", "30");
		setDefaultProperty("sun.net.httpserver.clockTick", "1000");
		setDefaultProperty("sun.net.httpserver.maxConnections", "128");
		server = HttpServer.create(new InetSocketAddress(bind, port), 32);
		executor = Executors.newFixedThreadPool(8, r -> {
			Thread t = new Thread(r, "CevherAuth-HTTP");
			t.setDaemon(true);
			return t;
		});
		server.setExecutor(executor);
		server.createContext("/api/", this::handle);
		server.start();
	}

	private static void setDefaultProperty(String key, String value) {
		if (System.getProperty(key) == null) {
			System.setProperty(key, value);
		}
	}

	void stop() {
		if (server != null) {
			server.stop(0);
		}
		if (executor != null) {
			executor.shutdownNow();
		}
	}

	/** Süresi dolmuş sayaçları temizler (dakikada bir). */
	void cleanup() {
		loginPair.cleanup();
		loginIp.cleanup();
		loginAccount.cleanup();
		loginKnown.cleanup();
		registerIp.cleanup();
		bridge.cleanup();
	}

	private static void sendResult(HttpExchange ex, SiteBridge.Result result) throws IOException {
		send(ex, result.status(), result.body());
	}

	private void handle(HttpExchange ex) throws IOException {
		AtomicInteger counter = null;
		try (ex) {
			Client client = client(ex);
			counter = connections.computeIfAbsent(client.ip(), k -> new AtomicInteger());
			if (counter.incrementAndGet() > plugin.getConfig().getInt("http.max-connections-per-ip", 6)) {
				send(ex, 429, error("TOO_MANY_ATTEMPTS"));
				return;
			}
			String path = ex.getRequestURI().getPath();
			if (path.equals("/api/health") && ex.getRequestMethod().equals("GET")) {
				JsonObject o = ok();
				o.addProperty("service", "cevher-auth");
				send(ex, 200, o);
				return;
			}
			if (!ex.getRequestMethod().equals("POST")) {
				send(ex, 405, error("METHOD_NOT_ALLOWED"));
				return;
			}
			if (ex.getRequestHeaders().getFirst("Origin") != null || ex.getRequestHeaders().getFirst("Sec-Fetch-Site") != null) {
				send(ex, 403, error("FORBIDDEN_ORIGIN"));
				return;
			}
			String contentType = ex.getRequestHeaders().getFirst("Content-Type");
			if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("application/json")) {
				send(ex, 415, error("BAD_REQUEST"));
				return;
			}
			JsonObject body = readBody(ex);
			if (body == null) {
				send(ex, 400, error("BAD_REQUEST"));
				return;
			}
			switch (path) {
				case "/api/register" -> register(ex, body, client);
				case "/api/login" -> login(ex, body, client);
				case "/api/join" -> join(ex, body, client);
				case "/api/logout" -> logout(ex, body);
				case "/api/password" -> changePassword(ex, body, client);
				case "/api/site/profile" -> sendResult(ex, bridge.profile(str(body, "token")));
				case "/api/store/listing" -> sendResult(ex, bridge.listing(str(body, "token")));
				case "/api/store/buy" -> sendResult(ex, bridge.buy(str(body, "token"), str(body, "productId")));
				default -> send(ex, 404, error("NOT_FOUND"));
			}
		} catch (Exception e) {
			plugin.getLogger().log(Level.WARNING, "API isteği işlenemedi", e);
			try {
				send(ex, 500, error("INTERNAL"));
			} catch (IOException ignored) {
				// Bağlantı zaten kapanmış olabilir.
			}
		} finally {
			if (counter != null) {
				counter.decrementAndGet();
			}
		}
	}

	// ── uç noktalar ─────────────────────────────────────────────────────────────

	private void register(HttpExchange ex, JsonObject body, Client client) throws IOException {
		String username = str(body, "username");
		String password = str(body, "password");
		if (username == null || !USERNAME.matcher(username).matches()) {
			send(ex, 400, error("INVALID_USERNAME"));
			return;
		}
		if (!passwordOk(password)) {
			send(ex, 400, error("WEAK_PASSWORD"));
			return;
		}
		// Her deneme (başarısız olsa da) IP başına sayılır; PBKDF2 maliyeti kontrollerden sonra ödenir.
		if (!client.local()) {
			if (registerIp.count(client.ip()) >= plugin.getConfig().getInt("max-registrations-per-10min", 5)) {
				send(ex, 429, error("TOO_MANY_ATTEMPTS"));
				return;
			}
			registerIp.record(client.ip());
		}
		if (plugin.accounts().get(username) != null) {
			send(ex, 409, error("USERNAME_TAKEN"));
			return;
		}
		if (plugin.isReservedName(username) && !client.local()) {
			send(ex, 403, error("PROTECTED_NAME"));
			return;
		}
		int maxPerIp = plugin.getConfig().getInt("max-accounts-per-ip", 3);
		if (maxPerIp > 0 && !client.local() && plugin.accounts().countByIp(client.ip()) >= maxPerIp) {
			send(ex, 429, error("TOO_MANY_ACCOUNTS"));
			return;
		}
		// Sitede de aynı ad ve şifreyle hesap açılır. Site kapalıysa kayıt yine yapılır; site hesabı
		// oyuncunun bir sonraki girişinde açılır.
		boolean siteSynced = false;
		LeaderOsSync site = plugin.site();
		if (site != null && site.enabled()) {
			LeaderOsSync.Result r = site.register(username, password, client.ip());
			switch (r) {
				case OK -> siteSynced = true;
				case USERNAME_TAKEN -> {
					// Ad sitede zaten kayıtlı: oyuncu site şifresiyle launcher'dan giriş yapabilir.
					send(ex, 409, error("USERNAME_TAKEN"));
					return;
				}
				case INVALID_USERNAME -> {
					send(ex, 400, error("INVALID_USERNAME"));
					return;
				}
				case INVALID_PASSWORD -> {
					send(ex, 400, error("WEAK_PASSWORD"));
					return;
				}
				case LIMIT -> {
					send(ex, 429, error("TOO_MANY_ACCOUNTS"));
					return;
				}
				default -> plugin.getLogger().warning(username + " sitede açılamadı (" + r + "); yalnızca launcher hesabı açılıyor");
			}
		}
		AccountStore.Account account = plugin.accounts().create(username, Passwords.hash(password), client.ip(), siteSynced);
		if (account == null) {
			send(ex, 409, error("USERNAME_TAKEN"));
			return;
		}
		plugin.getLogger().info(username + " launcher'dan kayıt oldu (" + client.ip() + ")" + (siteSynced ? " — sitede de hesap açıldı" : ""));
		markKnown(client.ip(), AccountStore.key(account.username));
		sendSession(ex, account, account.credVersion);
	}

	private void login(HttpExchange ex, JsonObject body, Client client) throws IOException {
		String username = str(body, "username");
		String password = str(body, "password");
		String ukey = username != null && USERNAME.matcher(username).matches() ? AccountStore.key(username) : null;
		if (throttled(client.ip(), ukey)) {
			send(ex, 429, error("TOO_MANY_ATTEMPTS"));
			return;
		}
		AccountStore.Account account = ukey == null ? null : plugin.accounts().get(username);
		// Sürüm, şifre doğrulanmadan önce okunur: doğrulama sırasında şifre değişirse oturum verilmez.
		long version = account != null ? account.credVersion : 0;
		boolean valid;
		if (account != null && password != null) {
			valid = Passwords.verify(password, account.passwordHash);
		} else {
			// Hesap yoksa da aynı süre harcanır; yanıt süresinden hangi adların kayıtlı olduğu anlaşılmaz.
			Passwords.verify(password == null ? "" : password, DUMMY_HASH);
			valid = false;
		}
		if (!valid && ukey != null && password != null) {
			// Sitede açılmış (veya şifresi sitede değişmiş) hesap: site şifresiyle giriş denenir.
			AccountStore.Account fromSite = loginViaSite(username, password, account, client);
			if (fromSite != null) {
				account = fromSite;
				version = fromSite.credVersion;
				valid = true;
			}
		}
		if (!valid) {
			recordFailure(client.ip(), ukey);
			send(ex, 401, error("WRONG_CREDENTIALS"));
			return;
		}
		// Yalnızca bu (IP, hesap) çiftinin sayacı sıfırlanır; başkalarının bütçesine dokunulmaz.
		loginPair.clear(client.ip() + "|" + ukey);
		markKnown(client.ip(), ukey);
		if (!account.siteSynced && plugin.site() != null) {
			// Senkrondan önce açılmış hesap: site hesabı arka planda aynı şifreyle açılır.
			plugin.site().backfillAsync(account.username, password, client.ip());
		}
		sendSession(ex, account, version);
	}

	/**
	 * Launcher şifresi tutmadığında siteyi dener. Başarılıysa launcher hesabını oluşturur ya da
	 * şifresini site şifresine günceller ve hesabı döner; aksi halde null.
	 *
	 * Güvenlik: Mevcut bir launcher hesabının şifresi yalnızca o hesap daha önce siteyle
	 * eşleştirildiyse ({@code siteSynced}) siteden devralınır. Aksi halde biri sitede aynı adla
	 * hesap açıp launcher hesabını ele geçirebilirdi. Korunan adlar (op / daha önce oynamış)
	 * için siteden yeni launcher hesabı açılmaz.
	 */
	private AccountStore.Account loginViaSite(String username, String password, AccountStore.Account account, Client client) throws IOException {
		LeaderOsSync site = plugin.site();
		if (site == null || !site.enabled()) {
			return null;
		}
		if (account != null && !account.siteSynced) {
			return null;
		}
		if (account == null && plugin.isReservedName(username) && !client.local()) {
			return null;
		}
		if (!passwordOk(password)) {
			return null;
		}
		if (site.login(username, password, client.ip()) != LeaderOsSync.Result.OK) {
			return null;
		}
		if (account == null) {
			AccountStore.Account created = plugin.accounts().create(username, Passwords.hash(password), client.ip(), true);
			if (created == null) {
				return null; // Aynı anda başka bir istek oluşturdu; oyuncu tekrar denesin.
			}
			plugin.getLogger().info(username + " site hesabıyla launcher'a ilk kez giriş yaptı — launcher hesabı açıldı (" + client.ip() + ")");
			return created;
		}
		plugin.accounts().setPassword(account.username, Passwords.hash(password), true);
		plugin.sessions().revokeAll(account.username);
		plugin.getLogger().info(account.username + " launcher şifresi site şifresine güncellendi (" + client.ip() + ")");
		return plugin.accounts().get(account.username);
	}

	private void join(HttpExchange ex, JsonObject body, Client client) throws IOException {
		String token = str(body, "token");
		SessionStore.IssuedTicket ticket = token == null ? null
			: plugin.sessions().issueTicket(token, plugin.sessionLifetimeMs(), client.ip(), plugin.ticketLifetimeMs());
		if (ticket == null) {
			send(ex, 401, error("SESSION_EXPIRED"));
			return;
		}
		JsonObject o = ok();
		o.addProperty("username", ticket.username());
		o.addProperty("expiresIn", plugin.ticketLifetimeMs() / 1000);
		// Bilete özel sır: launcher oyuna verir, oyun bağlanırken bununla kanıt üretir (TicketProof).
		o.addProperty("secret", ticket.secret());
		send(ex, 200, o);
	}

	/** Şifre değiştirme: mevcut şifre doğrulanır, tüm oturumlar kapanır, bu cihaza yeni oturum verilir. */
	private void changePassword(HttpExchange ex, JsonObject body, Client client) throws IOException {
		String token = str(body, "token");
		SessionStore.Session session = token == null ? null : plugin.sessions().touch(token, plugin.sessionLifetimeMs());
		AccountStore.Account account = session == null ? null : plugin.accounts().get(session.username);
		if (account == null) {
			send(ex, 401, error("SESSION_EXPIRED"));
			return;
		}
		String ukey = AccountStore.key(account.username);
		if (throttled(client.ip(), ukey)) {
			send(ex, 429, error("TOO_MANY_ATTEMPTS"));
			return;
		}
		long version = account.credVersion;
		String current = str(body, "currentPassword");
		if (current == null || !Passwords.verify(current, account.passwordHash)) {
			recordFailure(client.ip(), ukey);
			send(ex, 401, error("WRONG_CREDENTIALS"));
			return;
		}
		String next = str(body, "newPassword");
		if (!passwordOk(next)) {
			send(ex, 400, error("WEAK_PASSWORD"));
			return;
		}
		if (account.credVersion != version) {
			send(ex, 401, error("SESSION_EXPIRED"));
			return;
		}
		loginPair.clear(client.ip() + "|" + ukey);
		markKnown(client.ip(), ukey);
		try {
			plugin.accounts().setPassword(account.username, Passwords.hash(next));
		} finally {
			// Yazım başarısız olsa bile eski kimlik bilgisiyle alınmış oturum ve biletler kapanır.
			plugin.sessions().revokeAll(account.username);
		}
		plugin.getLogger().info(account.username + " launcher'dan şifresini değiştirdi (" + client.ip() + ")");
		sendSession(ex, account, account.credVersion);
	}

	private void logout(HttpExchange ex, JsonObject body) throws IOException {
		String token = str(body, "token");
		if (token != null) {
			plugin.sessions().revoke(token);
		}
		send(ex, 200, ok());
	}

	// ── yardımcılar ─────────────────────────────────────────────────────────────

	private void sendSession(HttpExchange ex, AccountStore.Account account, long version) throws IOException {
		String token = plugin.sessions().create(account, version, plugin.sessionLifetimeMs(), plugin.maxSessionsPerAccount());
		if (token == null) {
			// Kimlik bilgisi doğrulandıktan sonra hesap silindi veya şifresi değişti.
			send(ex, 401, error("SESSION_EXPIRED"));
			return;
		}
		JsonObject o = ok();
		o.addProperty("username", account.username);
		o.addProperty("token", token);
		send(ex, 200, o);
	}

	private boolean passwordOk(String password) {
		int min = plugin.getConfig().getInt("password.min-length", 6);
		int max = plugin.getConfig().getInt("password.max-length", 64);
		return password != null && password.length() >= min && password.length() <= max && !password.isBlank();
	}

	private boolean throttled(String ip, String ukey) {
		int max = Math.max(1, plugin.getConfig().getInt("max-failed-logins", 10));
		if (loginIp.count(ip) >= max * 3) {
			return true;
		}
		if (ukey == null) {
			return false;
		}
		String pairKey = ip + "|" + ukey;
		if (loginPair.count(pairKey) >= max) {
			return true;
		}
		// Hesap çapındaki tavan, daha önce kimlik doğrulamamış her çifte uygulanır (dağıtık tahmin ve
		// çalınmış oturumla currentPassword tahmini dahil). Bilinen çift, başkalarının hatalarıyla kilitlenmez.
		return loginAccount.count(ukey) >= max * 3 && loginKnown.count(pairKey) == 0;
	}

	/** (IP, hesap) çiftini başarıyla kimlik doğrulamış olarak işaretler; pencere her başarıda yenilenir. */
	private void markKnown(String ip, String ukey) {
		String pairKey = ip + "|" + ukey;
		loginKnown.clear(pairKey);
		loginKnown.record(pairKey);
	}

	private void recordFailure(String ip, String ukey) {
		loginIp.record(ip);
		if (ukey != null) {
			loginPair.record(ip + "|" + ukey);
			loginAccount.record(ukey);
		}
	}

	/**
	 * İstemciyi belirler. Proxy başlıkları yalnızca bağlantıyı kuran adres
	 * {@code http.trusted-proxies} içindeyse dikkate alınır; zincir sağdan sola yürünür ve
	 * güvenilir proxy olmayan ilk adres istemci sayılır (soldaki girdiler sahte olabilir).
	 * "Yerel" ayrıcalığı yalnızca doğrudan loopback bağlantısına ve hiçbir proxy/tarayıcı
	 * başlığı taşımayan isteğe verilir.
	 */
	private Client client(HttpExchange ex) {
		String raw = CevherAuthPlugin.ipKey(ex.getRemoteAddress().getAddress());
		var headers = ex.getRequestHeaders();
		boolean proxied = headers.getFirst("X-Forwarded-For") != null
			|| headers.getFirst("Forwarded") != null
			|| headers.getFirst("X-Real-IP") != null;
		boolean local = "loopback".equals(raw) && !proxied && headers.getFirst("Origin") == null;
		String ip = raw;
		if (proxied && plugin.getConfig().getBoolean("http.trust-proxy-headers", false) && plugin.isTrustedProxy(raw)) {
			String forwarded = headers.getFirst("X-Forwarded-For");
			if (forwarded != null) {
				String[] parts = forwarded.split(",");
				for (int i = parts.length - 1; i >= 0; i--) {
					String candidate = parts[i].trim();
					if (!IP_LITERAL.matcher(candidate).matches()) {
						break; // Geçersiz girdi: doğrudan bağlantı adresine düş.
					}
					String key;
					try {
						key = CevherAuthPlugin.ipKey(InetAddress.getByName(candidate));
					} catch (IOException e) {
						break;
					}
					if (!plugin.isTrustedProxy(key)) {
						ip = key;
						break;
					}
				}
			}
		}
		return new Client(ip, local);
	}

	/** {@code http.trusted-proxies} listesini IP anahtarlarına çevirir (yalnızca IP değişmezleri). */
	static Set<String> parseProxies(List<String> entries) {
		Set<String> keys = new HashSet<>();
		for (String entry : entries) {
			String e = entry == null ? "" : entry.trim();
			if (!IP_LITERAL.matcher(e).matches()) {
				continue;
			}
			try {
				keys.add(CevherAuthPlugin.ipKey(InetAddress.getByName(e)));
			} catch (IOException ignored) {
				// Geçersiz girdi yok sayılır.
			}
		}
		return keys;
	}

	private static JsonObject readBody(HttpExchange ex) {
		try (InputStream in = ex.getRequestBody()) {
			byte[] data = in.readNBytes(MAX_BODY + 1);
			if (data.length > MAX_BODY) {
				return null;
			}
			JsonElement el = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
			return el.isJsonObject() ? el.getAsJsonObject() : null;
		} catch (Exception e) {
			return null;
		}
	}

	private static String str(JsonObject o, String key) {
		JsonElement el = o.get(key);
		return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString() ? el.getAsString() : null;
	}

	private static JsonObject ok() {
		JsonObject o = new JsonObject();
		o.addProperty("ok", true);
		return o;
	}

	private static JsonObject error(String code) {
		JsonObject o = new JsonObject();
		o.addProperty("ok", false);
		o.addProperty("error", code);
		return o;
	}

	private static void send(HttpExchange ex, int status, JsonObject body) throws IOException {
		byte[] bytes = JsonFile.GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
		ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		ex.getResponseHeaders().set("Cache-Control", "no-store");
		ex.sendResponseHeaders(status, bytes.length);
		try (OutputStream out = ex.getResponseBody()) {
			out.write(bytes);
		}
	}
}
