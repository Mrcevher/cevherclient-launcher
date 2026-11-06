package net.cevherclient.auth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * LeaderOS sitesiyle hesap senkronu (config: {@code leaderos.*}).
 *
 * Sitenin oyun API'si kullanılır:
 *   POST /api/auth/register  username, password, ip, useragent [, email]
 *   POST /api/auth/login     username, password, ip, useragent
 * İstekler {@code X-Api-Key} başlığı ve form gövdesiyle gönderilir.
 *
 * Site erişilemezse launcher çalışmaya devam eder; eksik site hesabı oyuncunun bir sonraki
 * girişinde açılır.
 */
final class LeaderOsSync {
	enum Result {
		OK,
		USERNAME_TAKEN,
		USER_NOT_FOUND,
		WRONG_PASSWORD,
		INVALID_USERNAME,
		INVALID_PASSWORD,
		INVALID_EMAIL,
		EMAIL_TAKEN,
		LIMIT,
		/** Site kapalı, zaman aşımı, yanlış API anahtarı veya tanınmayan yanıt. */
		UNAVAILABLE
	}

	private static final String USER_AGENT = "CevherClient";

	private final CevherAuthPlugin plugin;
	private final HttpClient http;
	private final ExecutorService background = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "CevherAuth-LeaderOS");
		t.setDaemon(true);
		return t;
	});

	LeaderOsSync(CevherAuthPlugin plugin) {
		this.plugin = plugin;
		this.http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(timeoutSeconds()))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
	}

	boolean enabled() {
		return plugin.getConfig().getBoolean("leaderos.enabled", false)
			&& !url().isEmpty()
			&& !apiKey().isEmpty();
	}

	Result register(String username, String password, String ip) {
		Map<String, String> form = new LinkedHashMap<>();
		form.put("username", username);
		form.put("password", password);
		form.put("ip", ipForSite(ip));
		form.put("useragent", USER_AGENT);
		return post("auth/register", form);
	}

	Result login(String username, String password, String ip) {
		Map<String, String> form = new LinkedHashMap<>();
		form.put("username", username);
		form.put("password", password);
		form.put("ip", ipForSite(ip));
		form.put("useragent", USER_AGENT);
		return post("auth/login", form);
	}

	/**
	 * Launcher'da girişi başarılı olan ama sitede hesabı henüz açılmamış oyuncu için site hesabını
	 * arka planda açar. Ad sitede başkasına aitse hesap işaretlenmez (siteden şifre devralınamaz).
	 */
	void backfillAsync(String username, String password, String ip) {
		if (!enabled()) {
			return;
		}
		background.execute(() -> {
			Result r = register(username, password, ip);
			try {
				if (r == Result.OK) {
					plugin.accounts().setSiteSynced(username, true);
					plugin.getLogger().info(username + " için sitede hesap açıldı (eski hesap aktarıldı)");
				} else if (r == Result.USERNAME_TAKEN) {
					plugin.getLogger().warning(username + " adı sitede başka bir hesaba ait; launcher hesabı siteye bağlanmadı");
				} else if (r != Result.UNAVAILABLE) {
					plugin.getLogger().warning(username + " siteye aktarılamadı: " + r);
				}
			} catch (Exception e) {
				plugin.getLogger().log(Level.WARNING, username + " site senkron durumu kaydedilemedi", e);
			}
		});
	}

	void shutdown() {
		background.shutdownNow();
	}

	// ── Mağaza / profil köprüsü (launcher API anahtarını hiç görmez) ───────────────

	/** Ham LeaderOS yanıtı; site erişilemezse {@code status = 0, json = null}. */
	record Response(int status, JsonElement json) {
		boolean ok() {
			return status >= 200 && status < 300 && json != null;
		}

		String error() {
			if (json != null && json.isJsonObject()) {
				JsonElement e = json.getAsJsonObject().get("error");
				if (e != null && e.isJsonPrimitive()) {
					return e.getAsString();
				}
			}
			return status == 0 ? "UNAVAILABLE" : "HTTP_" + status;
		}
	}

	String siteUrl() {
		return url();
	}

	/** GET /api/store/listing?username= — kategoriler, alt kategoriler ve ürünler. */
	Response storeListing(String username) {
		return get("store/listing?username=" + URLEncoder.encode(username, StandardCharsets.UTF_8));
	}

	/** GET /api/credits?username= — { credits, raw_credits, currency }. */
	Response credits(String username) {
		return get("credits?username=" + URLEncoder.encode(username, StandardCharsets.UTF_8));
	}

	/** GET /api/users/?username= — { id, username, credit, creationDate … }. */
	Response user(String username) {
		return get("users/?username=" + URLEncoder.encode(username, StandardCharsets.UTF_8));
	}

	/** GET /api/users/{id}/orders — sipariş listesi. */
	Response orders(String userId) {
		return get("users/" + URLEncoder.encode(userId, StandardCharsets.UTF_8) + "/orders");
	}

	/** POST /api/store/buy — ürünü oyuncunun site kredisiyle satın alır. */
	Response buy(String userId, String productId) {
		String body = "products%5B%5D=" + URLEncoder.encode(productId, StandardCharsets.UTF_8)
			+ "&userID=" + URLEncoder.encode(userId, StandardCharsets.UTF_8);
		return send("store/buy", HttpRequest.newBuilder(URI.create(url() + "/api/store/buy"))
			.header("Content-Type", "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers.ofString(body)));
	}

	private Response get(String api) {
		return send(api, HttpRequest.newBuilder(URI.create(url() + "/api/" + api)).GET());
	}

	private Response send(String api, HttpRequest.Builder builder) {
		try {
			HttpRequest request = builder
				.timeout(Duration.ofSeconds(timeoutSeconds()))
				.header("X-Api-Key", apiKey())
				.header("Accept", "application/json")
				.header("User-Agent", USER_AGENT)
				.build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			JsonElement json = null;
			try {
				json = JsonParser.parseString(response.body() == null ? "" : response.body());
			} catch (Exception ignored) {
				// Aşağıda json = null döner.
			}
			if (json == null || json.isJsonNull()) {
				plugin.getLogger().warning("LeaderOS beklenmeyen yanıt verdi (" + api + ", HTTP " + response.statusCode() + ")");
			}
			return new Response(response.statusCode(), json == null || json.isJsonNull() ? null : json);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return new Response(0, null);
		} catch (Exception e) {
			plugin.getLogger().log(Level.WARNING, "LeaderOS'a ulaşılamadı (" + api + "): " + e.getMessage());
			return new Response(0, null);
		}
	}

	// ── HTTP ────────────────────────────────────────────────────────────────────

	private Result post(String api, Map<String, String> form) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url() + "/api/" + api))
				.timeout(Duration.ofSeconds(timeoutSeconds()))
				.header("X-Api-Key", apiKey())
				.header("Content-Type", "application/x-www-form-urlencoded")
				.header("Accept", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(encode(form)))
				.build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			return parse(api, response.statusCode(), response.body());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return Result.UNAVAILABLE;
		} catch (Exception e) {
			plugin.getLogger().log(Level.WARNING, "LeaderOS'a ulaşılamadı (" + api + "): " + e.getMessage());
			return Result.UNAVAILABLE;
		}
	}

	private Result parse(String api, int status, String body) {
		JsonObject json = null;
		try {
			JsonElement el = JsonParser.parseString(body == null ? "" : body);
			if (el.isJsonObject()) {
				json = el.getAsJsonObject();
			}
		} catch (Exception ignored) {
			// Aşağıda UNAVAILABLE döner.
		}
		if (json == null) {
			plugin.getLogger().warning("LeaderOS beklenmeyen yanıt verdi (" + api + ", HTTP " + status + ")");
			return Result.UNAVAILABLE;
		}
		boolean ok = status >= 200 && status < 300
			&& json.has("status") && json.get("status").isJsonPrimitive() && json.get("status").getAsBoolean()
			&& !json.has("error");
		if (ok) {
			return Result.OK;
		}
		String error = json.has("error") && json.get("error").isJsonPrimitive() ? json.get("error").getAsString() : "";
		return switch (error) {
			case "USERNAME_ALREADY_EXIST" -> Result.USERNAME_TAKEN;
			case "USER_NOT_FOUND" -> Result.USER_NOT_FOUND;
			case "WRONG_PASSWORD" -> Result.WRONG_PASSWORD;
			case "INVALID_USERNAME" -> Result.INVALID_USERNAME;
			case "INVALID_PASSWORD" -> Result.INVALID_PASSWORD;
			case "INVALID_EMAIL" -> Result.INVALID_EMAIL;
			case "EMAIL_ALREADY_EXIST" -> Result.EMAIL_TAKEN;
			case "REGISTER_LIMIT" -> Result.LIMIT;
			default -> {
				plugin.getLogger().warning("LeaderOS isteği başarısız (" + api + ", HTTP " + status + "): " + error);
				yield Result.UNAVAILABLE;
			}
		};
	}

	private static String encode(Map<String, String> form) {
		StringJoiner joiner = new StringJoiner("&");
		for (Map.Entry<String, String> e : form.entrySet()) {
			joiner.add(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
		}
		return joiner.toString();
	}

	/** CevherAuth yerel bağlantıyı "loopback" olarak tutar; siteye geçerli bir IP gönderilir. */
	private static String ipForSite(String ip) {
		return ip == null || ip.equals("loopback") || ip.equals("unknown") ? "127.0.0.1" : ip;
	}

	private String url() {
		String url = plugin.getConfig().getString("leaderos.url", "").trim();
		while (url.endsWith("/")) {
			url = url.substring(0, url.length() - 1);
		}
		return url;
	}

	private String apiKey() {
		return plugin.getConfig().getString("leaderos.api-key", "").trim();
	}

	private int timeoutSeconds() {
		return Math.max(2, plugin.getConfig().getInt("leaderos.timeout-seconds", 8));
	}
}
