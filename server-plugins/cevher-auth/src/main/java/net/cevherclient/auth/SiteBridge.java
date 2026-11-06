package net.cevherclient.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Launcher'daki Mağaza ve Profil ekranları için LeaderOS köprüsü.
 *
 *   POST /api/site/profile  { token }             → { ok, linked, credits, rawCredits, currency, registeredAt, orders[] }
 *   POST /api/store/listing { token }             → { ok, site, categories[] }
 *   POST /api/store/buy     { token, productId }  → { ok, orderId, total, credits, rawCredits, currency }
 *
 * Site API anahtarı yalnızca bu eklentide durur; launcher yalnızca kendi oturum belirtecini
 * gönderir. Satın alma, launcher oturumunun sahibi olan oyuncunun ({@code siteSynced} ile siteye
 * bağlanmış) aynı adlı site hesabından, o hesabın kredisiyle yapılır.
 */
final class SiteBridge {
	private static final long WINDOW_MS = 10 * 60_000L;
	private static final Pattern ID = Pattern.compile("^[0-9]{1,12}$");

	private final CevherAuthPlugin plugin;
	/** Hesap başına 10 dakikadaki satın alma denemesi. */
	private final Throttle purchases = new Throttle(WINDOW_MS);
	/** Aynı hesap için aynı anda yalnızca bir satın alma (çift tıklamaya karşı). */
	private final Set<String> buying = ConcurrentHashMap.newKeySet();

	SiteBridge(CevherAuthPlugin plugin) {
		this.plugin = plugin;
	}

	/** Hata kodu + HTTP durumu; başarıda {@code body} dolu. */
	record Result(int status, JsonObject body) {
		static Result error(int status, String code) {
			JsonObject o = new JsonObject();
			o.addProperty("ok", false);
			o.addProperty("error", code);
			return new Result(status, o);
		}

		static Result ok(JsonObject o) {
			o.addProperty("ok", true);
			return new Result(200, o);
		}
	}

	void cleanup() {
		purchases.cleanup();
	}

	// ── uç noktalar ─────────────────────────────────────────────────────────────

	Result profile(String token) {
		AccountStore.Account account = account(token);
		if (account == null) {
			return Result.error(401, "SESSION_EXPIRED");
		}
		LeaderOsSync site = site();
		if (site == null) {
			return Result.error(503, "STORE_DISABLED");
		}
		JsonObject o = new JsonObject();
		o.addProperty("linked", account.siteSynced);
		if (!account.siteSynced) {
			return Result.ok(o);
		}
		LeaderOsSync.Response user = site.user(account.username);
		if (user.status() == 404) {
			o.addProperty("linked", false);
			return Result.ok(o);
		}
		JsonObject u = firstObject(user);
		if (!user.ok() || u == null) {
			return Result.error(502, "STORE_UNAVAILABLE");
		}
		String created = str(u, "creationDate");
		if (created != null) {
			o.addProperty("registeredAt", created);
		}
		addCredits(o, site.credits(account.username), str(u, "credit"));

		JsonArray orders = new JsonArray();
		String userId = str(u, "id");
		if (userId != null && ID.matcher(userId).matches()) {
			LeaderOsSync.Response res = site.orders(userId);
			if (res.ok() && res.json().isJsonArray()) {
				for (JsonElement el : res.json().getAsJsonArray()) {
					if (!el.isJsonObject() || orders.size() >= 10) {
						continue;
					}
					JsonObject src = el.getAsJsonObject();
					JsonObject dst = new JsonObject();
					copy(src, dst, "id", "id");
					copy(src, dst, "total", "total");
					copy(src, dst, "status", "status");
					copy(src, dst, "creationDate", "date");
					orders.add(dst);
				}
			}
		}
		o.add("orders", orders);
		return Result.ok(o);
	}

	Result listing(String token) {
		AccountStore.Account account = account(token);
		if (account == null) {
			return Result.error(401, "SESSION_EXPIRED");
		}
		LeaderOsSync site = site();
		if (site == null) {
			return Result.error(503, "STORE_DISABLED");
		}
		LeaderOsSync.Response res = site.storeListing(account.username);
		if (!res.ok() || !res.json().isJsonObject()) {
			return Result.error(502, "STORE_UNAVAILABLE");
		}
		JsonElement cats = res.json().getAsJsonObject().get("categories");
		JsonArray out = new JsonArray();
		if (cats != null && cats.isJsonArray()) {
			flatten(cats.getAsJsonArray(), null, null, site.siteUrl(), out);
		}
		JsonObject o = new JsonObject();
		o.addProperty("site", site.siteUrl());
		o.add("categories", out);
		return Result.ok(o);
	}

	Result buy(String token, String productId) {
		AccountStore.Account account = account(token);
		if (account == null) {
			return Result.error(401, "SESSION_EXPIRED");
		}
		if (productId == null || !ID.matcher(productId).matches()) {
			return Result.error(400, "PRODUCT_NOT_FOUND");
		}
		LeaderOsSync site = site();
		if (site == null) {
			return Result.error(503, "STORE_DISABLED");
		}
		if (!account.siteSynced) {
			return Result.error(403, "SITE_NOT_LINKED");
		}
		String key = AccountStore.key(account.username);
		if (purchases.count(key) >= Math.max(1, plugin.getConfig().getInt("store.max-purchases-per-10min", 15))) {
			return Result.error(429, "TOO_MANY_ATTEMPTS");
		}
		if (!buying.add(key)) {
			return Result.error(409, "PURCHASE_IN_PROGRESS");
		}
		try {
			purchases.record(key);
			LeaderOsSync.Response user = site.user(account.username);
			JsonObject u = firstObject(user);
			String userId = u == null ? null : str(u, "id");
			if (user.status() == 404) {
				return Result.error(403, "SITE_NOT_LINKED");
			}
			if (userId == null || !ID.matcher(userId).matches()) {
				return Result.error(502, "STORE_UNAVAILABLE");
			}
			LeaderOsSync.Response res = site.buy(userId, productId);
			JsonObject body = res.json() != null && res.json().isJsonObject() ? res.json().getAsJsonObject() : null;
			boolean ok = res.ok() && body != null && (!body.has("status") || truthy(body.get("status"))) && !body.has("error");
			if (!ok) {
				String code = res.error();
				plugin.getLogger().warning(account.username + " satın alma başarısız (ürün " + productId + "): " + code);
				return switch (code) {
					case "INSUFFICIENT_BALANCE" -> Result.error(400, "INSUFFICIENT_BALANCE");
					case "OUT_OF_STOCK" -> Result.error(400, "OUT_OF_STOCK");
					case "PRODUCT_NOT_FOUND" -> Result.error(404, "PRODUCT_NOT_FOUND");
					case "USER_NOT_FOUND" -> Result.error(403, "SITE_NOT_LINKED");
					default -> Result.error(502, "STORE_UNAVAILABLE");
				};
			}
			JsonObject o = new JsonObject();
			copy(body, o, "orderID", "orderId");
			copy(body, o, "total", "total");
			addCredits(o, site.credits(account.username), null);
			plugin.getLogger().info(account.username + " launcher'dan ürün satın aldı (ürün " + productId
				+ ", sipariş " + str(body, "orderID") + ", tutar " + str(body, "total") + ")");
			return Result.ok(o);
		} finally {
			buying.remove(key);
		}
	}

	// ── yardımcılar ─────────────────────────────────────────────────────────────

	private LeaderOsSync site() {
		LeaderOsSync site = plugin.site();
		return site != null && site.enabled() && plugin.getConfig().getBoolean("store.enabled", true) ? site : null;
	}

	private AccountStore.Account account(String token) {
		if (token == null || token.isEmpty() || token.length() > 256) {
			return null;
		}
		SessionStore.Session session = plugin.sessions().touch(token, plugin.sessionLifetimeMs());
		return session == null ? null : plugin.accounts().get(session.username);
	}

	/** Kredi bilgisini ekler; credits uç noktası yoksa kullanıcı kaydındaki "credit" kullanılır. */
	private static void addCredits(JsonObject o, LeaderOsSync.Response res, String fallback) {
		if (res.ok() && res.json().isJsonObject()) {
			JsonObject c = res.json().getAsJsonObject();
			copy(c, o, "credits", "credits");
			copy(c, o, "raw_credits", "rawCredits");
			copy(c, o, "currency", "currency");
		} else if (fallback != null) {
			o.addProperty("credits", fallback);
			try {
				o.addProperty("rawCredits", Double.parseDouble(fallback));
			} catch (NumberFormatException ignored) {
				// Ham değer yok.
			}
		}
	}

	/** Kategori ağacını düz listeye çevirir; yalnızca aktif kategori ve ürünler, görsel adresleri tam URL. */
	private static void flatten(JsonArray cats, String parentId, String parentName, String siteUrl, JsonArray out) {
		for (JsonElement el : cats) {
			if (!el.isJsonObject()) {
				continue;
			}
			JsonObject c = el.getAsJsonObject();
			if (c.has("isActive") && !truthy(c.get("isActive"))) {
				continue;
			}
			String id = str(c, "id");
			String name = str(c, "name");
			if (id == null || name == null) {
				continue;
			}
			String fullName = parentName == null ? name : parentName + " › " + name;
			JsonObject dst = new JsonObject();
			dst.addProperty("id", id);
			dst.addProperty("name", fullName);
			if (parentId != null) {
				dst.addProperty("parentId", parentId);
			}
			String image = image(siteUrl, "categories", c);
			if (image != null) {
				dst.addProperty("image", image);
			}
			JsonArray products = new JsonArray();
			JsonElement ps = c.get("products");
			if (ps != null && ps.isJsonArray()) {
				for (JsonElement pe : ps.getAsJsonArray()) {
					if (!pe.isJsonObject()) {
						continue;
					}
					JsonObject p = pe.getAsJsonObject();
					if (p.has("isActive") && !truthy(p.get("isActive"))) {
						continue;
					}
					if (str(p, "id") == null || str(p, "name") == null) {
						continue;
					}
					JsonObject q = new JsonObject();
					copy(p, q, "id", "id");
					copy(p, q, "name", "name");
					copy(p, q, "details", "details");
					copy(p, q, "price", "price");
					copy(p, q, "discountedPrice", "discountedPrice");
					copy(p, q, "discountExpiryDate", "discountExpiry");
					copy(p, q, "stock", "stock");
					copy(p, q, "duration", "duration");
					copy(p, q, "priority", "priority");
					q.addProperty("featured", p.has("isFeaturedProduct") && truthy(p.get("isFeaturedProduct")));
					String pimg = image(siteUrl, "products", p);
					if (pimg != null) {
						q.addProperty("image", pimg);
					}
					products.add(q);
				}
			}
			dst.add("products", products);
			out.add(dst);
			JsonElement subs = c.get("subcategories");
			if (subs != null && subs.isJsonArray()) {
				flatten(subs.getAsJsonArray(), id, fullName, siteUrl, out);
			}
		}
	}

	/** LeaderOS görsel yolu: /assets/core/images/store/{products|categories}/{imageID}.{imageType} */
	private static String image(String siteUrl, String kind, JsonObject o) {
		String id = str(o, "imageID");
		String type = str(o, "imageType");
		if (id == null || type == null || !id.matches("^[A-Za-z0-9_-]{1,64}$") || !type.matches("^[A-Za-z0-9]{1,8}$")) {
			return null;
		}
		return siteUrl + "/assets/core/images/store/" + kind + "/" + id + "." + type;
	}

	/** /api/users yanıtı nesne ya da tek elemanlı dizi olabilir. */
	private static JsonObject firstObject(LeaderOsSync.Response res) {
		if (!res.ok()) {
			return null;
		}
		JsonElement el = res.json();
		if (el.isJsonArray() && !el.getAsJsonArray().isEmpty()) {
			el = el.getAsJsonArray().get(0);
		}
		if (el.isJsonObject() && el.getAsJsonObject().has("data") && el.getAsJsonObject().get("data").isJsonObject()) {
			el = el.getAsJsonObject().get("data");
		}
		return el.isJsonObject() ? el.getAsJsonObject() : null;
	}

	private static boolean truthy(JsonElement el) {
		if (el == null || !el.isJsonPrimitive()) {
			return false;
		}
		var p = el.getAsJsonPrimitive();
		if (p.isBoolean()) {
			return p.getAsBoolean();
		}
		String s = p.getAsString().trim();
		return s.equals("1") || s.equalsIgnoreCase("true");
	}

	private static String str(JsonObject o, String key) {
		JsonElement el = o.get(key);
		return el != null && el.isJsonPrimitive() ? el.getAsString() : null;
	}

	private static void copy(JsonObject from, JsonObject to, String key, String as) {
		JsonElement el = from.get(key);
		if (el != null && el.isJsonPrimitive()) {
			to.add(as, el);
		}
	}
}
