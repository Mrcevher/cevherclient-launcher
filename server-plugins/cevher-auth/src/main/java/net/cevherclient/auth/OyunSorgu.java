package net.cevherclient.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * Oyun içi istemci sorgusu (oturum modu 1.2+).
 *
 * Giriş kanıtı yalnızca bağlanırken bir kez istenir; oyuna sonradan hile enjekte eden ya da oturum modunu
 * taklit eden istemci orada yakalanmaz. Bu sınıf oyuncu oyundayken rastgele aralıklarla
 * {@code cevherauth:sorgu/<nonce>} cookie'si ister. Oturum modu bilet sırrıyla imzalı bir durum satırı döner:
 * {@code [0x01][HMAC-SHA256(sır, "cevherauth-sorgu-v1|nonce|ad|durum")][durum]}. Durum; mod listesinin özeti
 * ({@code m=<sha1>}), agent engeli ({@code +noattach}) ve çalışma anında görülen tehlikeler
 * ({@code !attach}, {@code !agent}, {@code !sinif:<ad>}) gibi virgülle ayrılmış girdilerdir.
 *
 * Bilet sırrı yalnızca lobide durur (giriş kanıtı orada doğrulanır). Kapı rolündeki sunucu yanıtı
 * {@code /api/internal/sorgu} ile lobiye doğrulatır. Sınır: sırrı bellekten okuyup modu baştan yazan biri
 * doğru yanıt üretebilir; sorgu bunu yalnızca zorlaştırır, davranış kontrolleri (CevherKoruma) yine şarttır.
 */
final class OyunSorgu implements Listener {
	static final String ONEK = "sorgu/";
	private static final String BAGLAM = "cevherauth-sorgu-v1|";
	private static final long BILET_OMRU_MS = 24L * 60 * 60_000L;

	/** Lobide: son launcher girişinin sırrı ve giriş anındaki mod listesinin özeti. */
	private record Bilet(String sir, boolean destek, String modOzet, long zaman) {
	}

	enum Sonuc {
		/** İmza doğru. */
		GECERLI,
		/** Yanıt geldi ama imza yanlış ya da biçim bozuk: sırrı bilmeyen / değiştirilmiş istemci. */
		GECERSIZ,
		/** İstemci boş yanıt verdi (oturum modu yok ya da eski). */
		YOK,
		/** Lobide bu oyuncunun launcher bileti yok (oyunda şifreyle girdi); sorgulanamaz. */
		BILINMIYOR
	}

	record Yanit(Sonuc sonuc, String durum, boolean bekleniyor) {
	}

	private record Ayar(boolean acik, long ilkTik, long minTik, long maxTik, boolean at, boolean yanitsizAt, int yanitsizSinir) {
	}

	private final CevherAuthPlugin plugin;
	private final Map<String, Bilet> biletler = new ConcurrentHashMap<>();
	private final Map<UUID, BukkitTask> gorevler = new ConcurrentHashMap<>();
	private final Map<UUID, Integer> yanitsiz = new ConcurrentHashMap<>();
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
	private volatile Ayar ayar;

	OyunSorgu(CevherAuthPlugin plugin) {
		this.plugin = plugin;
		yenile();
	}

	void yenile() {
		ConfigurationSection c = plugin.getConfig().getConfigurationSection("oyun-sorgu");
		if (c == null) {
			c = plugin.getConfig().createSection("oyun-sorgu");
		}
		long min = Math.max(30, c.getLong("aralik-min-saniye", 180));
		long max = Math.max(min, c.getLong("aralik-max-saniye", 480));
		ayar = new Ayar(c.getBoolean("acik", true), Math.max(5, c.getLong("ilk-saniye", 25)) * 20L, min * 20L, max * 20L,
			c.getBoolean("hatada-at", true), c.getBoolean("yanitsiz-at", false), Math.max(1, c.getInt("yanitsiz-sinir", 3)));
	}

	private static String anahtar(String ad) {
		return ad.toLowerCase(Locale.ROOT);
	}

	// ── lobi: bilet kaydı ve doğrulama ──────────────────────────────────────────

	/** Lobi: launcher kanıtı ve mod denetimi geçti; sorgu için sır saklanır. */
	void biletKaydet(String ad, String sir, String modlar) {
		if (ad == null || sir == null) {
			return;
		}
		boolean destek = modlar != null && Arrays.asList(modlar.split(",")).contains("+sorgu");
		biletler.put(anahtar(ad), new Bilet(sir, destek, modlar == null ? "" : sha1(modlar), System.currentTimeMillis()));
		long sinir = System.currentTimeMillis() - BILET_OMRU_MS;
		biletler.values().removeIf(b -> b.zaman() < sinir);
	}

	void biletSil(String ad) {
		biletler.remove(anahtar(ad));
	}

	/** Lobi: yanıtı bilet sırrıyla doğrular. */
	Yanit dogrula(String ad, String nonce, byte[] yanit) {
		Bilet b = biletler.get(anahtar(ad));
		if (b == null || System.currentTimeMillis() - b.zaman() > BILET_OMRU_MS) {
			return new Yanit(Sonuc.BILINMIYOR, null, false);
		}
		if (yanit == null || yanit.length == 0) {
			return new Yanit(Sonuc.YOK, null, b.destek());
		}
		if (yanit[0] != 1 || yanit.length < 33 || yanit.length > 4096) {
			return new Yanit(Sonuc.GECERSIZ, null, b.destek());
		}
		String durum = new String(yanit, 33, yanit.length - 33, StandardCharsets.UTF_8);
		byte[] beklenen = hmac(b.sir(), BAGLAM + nonce + "|" + anahtar(ad) + "|" + durum);
		if (beklenen == null || !MessageDigest.isEqual(beklenen, Arrays.copyOfRange(yanit, 1, 33))) {
			return new Yanit(Sonuc.GECERSIZ, null, b.destek());
		}
		for (String g : durum.split(",")) {
			if (g.startsWith("m=") && !b.modOzet().isEmpty() && !g.substring(2).equals(b.modOzet())) {
				durum = durum + ",!mod-degisti";
				break;
			}
		}
		return new Yanit(Sonuc.GECERLI, durum, b.destek());
	}

	/** Lobi iç API: { secret, ad, nonce, yanit(base64) } → { sonuc, durum, bekleniyor }. */
	JsonObject httpDogrula(JsonObject body) {
		JsonObject o = new JsonObject();
		String ad = str(body, "ad");
		String nonce = str(body, "nonce");
		String y = str(body, "yanit");
		if (ad == null || nonce == null || !nonce.matches("[0-9a-f]{32}")) {
			o.addProperty("sonuc", Sonuc.BILINMIYOR.name());
			return o;
		}
		byte[] yanit;
		try {
			yanit = y == null || y.isEmpty() ? new byte[0] : Base64.getDecoder().decode(y);
		} catch (IllegalArgumentException e) {
			yanit = new byte[] {0};
		}
		Yanit r = dogrula(ad, nonce, yanit);
		o.addProperty("sonuc", r.sonuc().name());
		if (r.durum() != null) {
			o.addProperty("durum", r.durum());
		}
		o.addProperty("bekleniyor", r.bekleniyor());
		return o;
	}

	private Yanit uzaktan(String ad, String nonce, byte[] yanit) {
		String url = plugin.getConfig().getString("network.lobby-api", "http://127.0.0.1:25580").replaceAll("/+$", "")
			+ "/api/internal/sorgu";
		JsonObject body = new JsonObject();
		body.addProperty("secret", plugin.getConfig().getString("network.secret", ""));
		body.addProperty("ad", ad);
		body.addProperty("nonce", nonce);
		body.addProperty("yanit", yanit == null ? "" : Base64.getEncoder().encodeToString(yanit));
		try {
			HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url))
					.timeout(Duration.ofSeconds(4))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(JsonFile.GSON.toJson(body), StandardCharsets.UTF_8))
					.build(),
				HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (r.statusCode() != 200) {
				return new Yanit(Sonuc.BILINMIYOR, null, false);
			}
			JsonObject j = JsonParser.parseString(r.body()).getAsJsonObject();
			Sonuc s;
			try {
				s = Sonuc.valueOf(str(j, "sonuc"));
			} catch (RuntimeException e) {
				s = Sonuc.BILINMIYOR;
			}
			return new Yanit(s, str(j, "durum"), j.has("bekleniyor") && j.get("bekleniyor").getAsBoolean());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return new Yanit(Sonuc.BILINMIYOR, null, false);
		} catch (Exception e) {
			return new Yanit(Sonuc.BILINMIYOR, null, false);
		}
	}

	// ── zamanlama ───────────────────────────────────────────────────────────────

	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(PlayerJoinEvent event) {
		Player p = event.getPlayer();
		LobbyLogin lobby = plugin.lobbyLogin();
		if (lobby != null && !lobby.viaLauncher(p.getName())) {
			// Lobiye launcher'sız geldi: eski bir launcher girişinden kalan sır kullanılmasın.
			biletSil(p.getName());
			return;
		}
		Ayar a = ayar;
		if (!a.acik()) {
			return;
		}
		yanitsiz.remove(p.getUniqueId());
		planla(p, a.ilkTik());
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		BukkitTask t = gorevler.remove(event.getPlayer().getUniqueId());
		if (t != null) {
			t.cancel();
		}
		yanitsiz.remove(event.getPlayer().getUniqueId());
	}

	private void planla(Player p, long gecikme) {
		BukkitTask eski = gorevler.put(p.getUniqueId(), plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
			if (p.isOnline()) {
				sor(p);
			}
		}, gecikme));
		if (eski != null) {
			eski.cancel();
		}
	}

	/** Oyuncuya sorguyu gönderir; yanıt (ya da 10 sn içinde yanıt yoksa boş) arka planda değerlendirilir. */
	void sor(Player p) {
		Ayar a = ayar;
		String ad = p.getName();
		String ip = p.getAddress() == null ? null : CevherAuthPlugin.ipKey(p.getAddress().getAddress());
		String nonce = TicketProof.newNonce();
		try {
			p.retrieveCookie(new NamespacedKey(plugin, ONEK + nonce))
				.orTimeout(10, TimeUnit.SECONDS)
				.whenComplete((bytes, err) -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
					byte[] yanit = err == null ? bytes : null;
					Yanit y = plugin.lobbyLogin() != null ? dogrula(ad, nonce, yanit) : uzaktan(ad, nonce, yanit);
					plugin.getServer().getScheduler().runTask(plugin, () -> sonuc(p, ad, ip, y));
				}));
		} catch (RuntimeException e) {
			plugin.getLogger().fine("Sorgu gönderilemedi (" + ad + "): " + e);
		}
		if (p.isOnline()) {
			planla(p, ThreadLocalRandom.current().nextLong(a.minTik(), a.maxTik() + 1));
		}
	}

	private void sonuc(Player p, String ad, String ip, Yanit y) {
		Ayar a = ayar;
		switch (y.sonuc()) {
			case GECERLI -> {
				yanitsiz.remove(p.getUniqueId());
				StringBuilder kotu = new StringBuilder();
				for (String g : y.durum().split(",")) {
					if (g.startsWith("!")) {
						kotu.append(kotu.isEmpty() ? "" : ", ").append(g.substring(1));
					}
				}
				if (!kotu.isEmpty()) {
					hata(p, ad, ip, "oyun sırasında: " + kotu, a.at());
				}
			}
			case GECERSIZ -> hata(p, ad, ip, "sorguya sahte/bozuk yanıt (oturum modu değiştirilmiş ya da taklit ediliyor)", a.at());
			case YOK -> {
				if (!y.bekleniyor()) {
					return; // Eski oturum modu: sorguyu tanımıyor.
				}
				int n = yanitsiz.merge(p.getUniqueId(), 1, Integer::sum);
				plugin.getLogger().info("[Sorgu] " + ad + " sorguya yanıt vermedi (" + n + "/" + a.yanitsizSinir() + ")");
				if (n >= a.yanitsizSinir()) {
					yanitsiz.remove(p.getUniqueId());
					hata(p, ad, ip, "oturum modu girişte sorgu desteği bildirdi ama " + n + " sorguya yanıt vermedi", a.yanitsizAt());
				}
			}
			case BILINMIYOR -> {
				// Launcher'sız oyuncu ya da lobiye ulaşılamadı: değerlendirilemez.
			}
		}
	}

	private void hata(Player p, String ad, String ip, String neden, boolean at) {
		plugin.getLogger().warning("[Sorgu] " + ad + " (" + ip + ") istemci doğrulaması başarısız: " + neden + (at ? " — atıldı" : ""));
		plugin.riskTakip().ekle(ad, ip, RiskTakip.Seviye.KRITIK, "istemci-sorgu", neden);
		if (at && p.isOnline()) {
			String raw = plugin.getConfig().getString("messages.sorgu-hata",
				"&cİstemci doğrulaması başarısız.\n&7Oyunu kapatıp &fCevherclient launcher &7ile yeniden gir.");
			p.kick(LegacyComponentSerializer.legacyAmpersand().deserialize(raw));
		}
	}

	// ── yardımcılar ─────────────────────────────────────────────────────────────

	private static byte[] hmac(String sir, String mesaj) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(sir.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return mac.doFinal(mesaj.getBytes(StandardCharsets.UTF_8));
		} catch (Exception e) {
			return null;
		}
	}

	static String sha1(String s) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			return "";
		}
	}

	private static String str(JsonObject o, String k) {
		return o != null && o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : null;
	}
}
