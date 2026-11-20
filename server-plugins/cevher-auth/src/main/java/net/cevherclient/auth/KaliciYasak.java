package net.cevherclient.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * Kalıcı yasak (ör. CevherKoruma'da yetkilinin "Hileydi → Onayla" kararı). Bir yasak dört şeyle eşleşir:
 * <ul>
 *   <li><b>hesap adı</b>,</li>
 *   <li><b>IP</b> (IP değişirse yeni IP de yasağa eklenir),</li>
 *   <li><b>cihaz izi</b>: Cevherclient modu her girişte bilgisayarın kimliğinden (Windows MachineGuid, fiziksel ağ
 *       kartları) üretilmiş tek yönlü özetleri {@code cevherclient:cihaz} kanalından gönderir; IP veya hesap
 *       değişse de aynı kalır,</li>
 *   <li><b>damga</b>: yasak anında bilgisayara (kayıt defteri, kullanıcı klasörü, oyun klasörü) yazılan rastgele
 *       belirteç; mod her girişte bunu da gönderir.</li>
 * </ul>
 * Eşleşen her yeni hesap adı / IP / cihaz izi yasağa eklenir: aynı bilgisayardan açılan yan hesaplar da girer girmez
 * yasaklanır. Kayıtlar {@code yasaklar.json}'da; kapı sunucusundaki yasak lobiye de bildirilir (giriş kapısı orası).
 */
final class KaliciYasak implements Listener, PluginMessageListener {
	static final String KANAL = "cevherclient:cihaz";
	private static final Pattern IZ = Pattern.compile("[a-z]:[0-9a-f]{16,64}");
	private static final Pattern DAMGA = Pattern.compile("[A-Za-z0-9_-]{16,64}");
	private static final DateTimeFormatter TARIH = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

	static final class Kayit {
		String id;
		String ana;
		Set<String> adlar = new LinkedHashSet<>();
		Set<String> ipler = new LinkedHashSet<>();
		Set<String> cihazlar = new LinkedHashSet<>();
		Set<String> damgalar = new LinkedHashSet<>();
		String sebep;
		String yetkili;
		String vaka;
		long zaman;
		List<String> gecmis = new ArrayList<>();
	}

	private final CevherAuthPlugin plugin;
	private final Path dosya;
	private final Path cihazDosya;
	private final List<Kayit> kayitlar = new CopyOnWriteArrayList<>();
	/** küçük harf ad → son görülen cihaz izleri. */
	private final Map<String, Set<String>> cihazlar = new ConcurrentHashMap<>();
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
	private volatile boolean cihazKirli;

	KaliciYasak(CevherAuthPlugin plugin) {
		this.plugin = plugin;
		this.dosya = plugin.getDataFolder().toPath().resolve("yasaklar.json");
		this.cihazDosya = plugin.getDataFolder().toPath().resolve("cihazlar.json");
		try {
			List<Kayit> l = JsonFile.read(dosya, new TypeToken<List<Kayit>>() { }.getType(), new ArrayList<Kayit>());
			for (Kayit k : l) {
				if (k != null && k.id != null) {
					duzelt(k);
					kayitlar.add(k);
				}
			}
			Map<String, Set<String>> c = JsonFile.read(cihazDosya, new TypeToken<Map<String, Set<String>>>() { }.getType(), Map.<String, Set<String>>of());
			c.forEach((ad, iz) -> cihazlar.put(ad, ConcurrentHashMap.newKeySet()));
			c.forEach((ad, iz) -> cihazlar.get(ad).addAll(iz));
		} catch (IOException e) {
			plugin.getLogger().severe("yasaklar.json okunamadı: " + e.getMessage());
		}
		plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, KANAL, this);
		plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, KANAL);
		plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::cihazKaydet, 20L * 60, 20L * 60);
		plugin.getLogger().info("Kalıcı yasak: " + kayitlar.size() + " kayıt");
	}

	private static void duzelt(Kayit k) {
		if (k.adlar == null) k.adlar = new LinkedHashSet<>();
		if (k.ipler == null) k.ipler = new LinkedHashSet<>();
		if (k.cihazlar == null) k.cihazlar = new LinkedHashSet<>();
		if (k.damgalar == null) k.damgalar = new LinkedHashSet<>();
		if (k.gecmis == null) k.gecmis = new ArrayList<>();
	}

	List<Kayit> kayitlar() {
		return kayitlar;
	}

	private boolean ipYasagi() {
		return plugin.getConfig().getBoolean("kalici-yasak.ip", true);
	}

	private boolean bagliHesap() {
		return plugin.getConfig().getBoolean("kalici-yasak.bagli-hesaplar", true);
	}

	private static boolean gecerliIp(String ip) {
		return ip != null && !ip.isEmpty() && !ip.equals("loopback") && !ip.equals("unknown") && ip.length() <= 64;
	}

	// ── sorgu ──────────────────────────────────────────────────────────────────

	/** Ad, IP, cihaz izleri veya damgalardan biri eşleşen yasak; yoksa null. */
	Kayit bul(String ad, String ip, Set<String> izler, Set<String> damgalar) {
		String a = ad == null ? null : ad.toLowerCase(Locale.ROOT);
		boolean ipAcik = ipYasagi();
		for (Kayit k : kayitlar) {
			if (a != null && k.adlar.contains(a)) return k;
			if (ipAcik && gecerliIp(ip) && k.ipler.contains(ip)) return k;
			if (izler != null) for (String z : izler) if (k.cihazlar.contains(z)) return k;
			if (damgalar != null) for (String d : damgalar) if (k.damgalar.contains(d)) return k;
		}
		return null;
	}

	Kayit bulAdVeyaIp(String s) {
		String a = s.toLowerCase(Locale.ROOT);
		for (Kayit k : kayitlar) {
			if (k.id.equalsIgnoreCase(s) || k.adlar.contains(a) || k.ipler.contains(s)) return k;
		}
		return null;
	}

	// ── yasaklama ──────────────────────────────────────────────────────────────

	/**
	 * Oyuncuyu kalıcı yasaklar (ya da var olan yasağını genişletir). Çevrimiçiyse bilgisayarına damga yazdırılır ve
	 * atılır. {@code yay}: kapı sunucusundaysa yasağı lobiye de bildir.
	 */
	synchronized Kayit yasakla(String ad, String ip, Set<String> ekIz, String damga, String sebep, String yetkili, String vaka, boolean yay) {
		String a = ad.toLowerCase(Locale.ROOT);
		Kayit k = bulAdVeyaIp(ad);
		boolean yeni = k == null;
		if (yeni) {
			k = new Kayit();
			k.id = Passwords.randomToken().substring(0, 8);
			k.ana = ad;
			k.sebep = sebep == null || sebep.isBlank() ? "Hile" : sebep;
			k.yetkili = yetkili == null ? "?" : yetkili;
			k.vaka = vaka == null ? "" : vaka;
			k.zaman = System.currentTimeMillis();
			k.damgalar.add(damga != null && DAMGA.matcher(damga).matches() ? damga : Passwords.randomToken().substring(0, 32));
			k.gecmis.add(TARIH.format(Instant.now()) + " yasaklandı: " + ad + " — " + k.sebep + " (" + k.yetkili + (k.vaka.isEmpty() ? "" : ", vaka " + k.vaka) + ")");
			kayitlar.add(k);
		} else if (damga != null && DAMGA.matcher(damga).matches()) {
			k.damgalar.add(damga);
		}
		k.adlar.add(a);
		if (gecerliIp(ip)) k.ipler.add(ip);
		Set<String> izler = cihazlar.get(a);
		if (izler != null) k.cihazlar.addAll(izler);
		if (ekIz != null) for (String z : ekIz) if (IZ.matcher(z).matches()) k.cihazlar.add(z);
		// Aynı bilgisayarda görülmüş diğer hesaplar (yan hesaplar)
		if (bagliHesap() && !k.cihazlar.isEmpty()) {
			for (Map.Entry<String, Set<String>> e : cihazlar.entrySet()) {
				for (String z : e.getValue()) {
					if (k.cihazlar.contains(z) && k.adlar.add(e.getKey())) {
						k.gecmis.add(TARIH.format(Instant.now()) + " aynı bilgisayarda görülen hesap eklendi: " + e.getKey());
					}
				}
			}
		}
		kaydet();
		plugin.getLogger().warning("[YASAK] " + ad + " kalıcı yasaklandı (" + k.id + "): " + k.adlar.size() + " hesap, " + k.ipler.size() + " IP, "
			+ k.cihazlar.size() + " cihaz izi — " + k.sebep);
		final Kayit son = k;
		Runnable at = () -> {
			for (Player p : Bukkit.getOnlinePlayers()) {
				if (son.adlar.contains(p.getName().toLowerCase(Locale.ROOT)) || (ipYasagi() && son.ipler.contains(ipOf(p)))) {
					damgala(p, son);
					Bukkit.getScheduler().runTaskLater(plugin, () -> {
						if (p.isOnline()) p.kick(mesaj(son));
					}, 10L);
				}
			}
		};
		if (Bukkit.isPrimaryThread()) at.run(); else Bukkit.getScheduler().runTask(plugin, at);
		if (yay && plugin.isGate()) {
			lobiyeGonder("/api/internal/ban", lobiGovdesi(ad, ip, k, sebep, yetkili, vaka));
		}
		return k;
	}

	private JsonObject lobiGovdesi(String ad, String ip, Kayit k, String sebep, String yetkili, String vaka) {
		JsonObject o = new JsonObject();
		o.addProperty("ad", ad);
		o.addProperty("ip", ip == null ? "" : ip);
		o.addProperty("damga", k.damgalar.iterator().next());
		JsonArray iz = new JsonArray();
		k.cihazlar.forEach(iz::add);
		o.add("izler", iz);
		o.addProperty("sebep", sebep == null ? "" : sebep);
		o.addProperty("yetkili", yetkili == null ? "" : yetkili);
		o.addProperty("vaka", vaka == null ? "" : vaka);
		return o;
	}

	synchronized Kayit kaldir(String adVeyaIp, boolean yay) {
		Kayit k = bulAdVeyaIp(adVeyaIp);
		if (k == null) return null;
		kayitlar.remove(k);
		kaydet();
		plugin.getLogger().warning("[YASAK] kaldırıldı: " + k.ana + " (" + k.id + ")");
		if (yay && plugin.isGate()) {
			JsonObject o = new JsonObject();
			o.addProperty("ad", k.ana);
			lobiyeGonder("/api/internal/unban", o);
		}
		return k;
	}

	/** Eşleşen yasağa yeni ad / IP / cihaz izi / damga ekler; değişiklik olduysa kaydeder. */
	private void genislet(Kayit k, String ad, String ip, Set<String> izler, Set<String> damgalar, String nasil) {
		boolean degisti = false;
		String a = ad == null ? null : ad.toLowerCase(Locale.ROOT);
		synchronized (this) {
			if (a != null && bagliHesap() && k.adlar.add(a)) {
				k.gecmis.add(TARIH.format(Instant.now()) + " yan hesap yakalandı: " + ad + " (" + nasil + ")");
				degisti = true;
			}
			if (ipYasagi() && gecerliIp(ip) && k.ipler.add(ip)) {
				k.gecmis.add(TARIH.format(Instant.now()) + " yeni IP eklendi: " + ip + " (" + nasil + ")");
				degisti = true;
			}
			if (izler != null) for (String z : izler) degisti |= k.cihazlar.add(z);
			if (damgalar != null) for (String d : damgalar) degisti |= k.damgalar.add(d);
			while (k.gecmis.size() > 100) k.gecmis.remove(0);
			if (degisti) kaydet();
		}
		if (degisti) {
			plugin.getLogger().warning("[YASAK] " + ad + " (" + ip + ") yasaklı " + k.ana + " ile eşleşti (" + nasil + "): yasak genişletildi");
		}
	}

	Component mesaj(Kayit k) {
		String sablon = plugin.getConfig().getString("messages.kalici-yasak",
			"&c&lSunucudan kalıcı olarak yasaklandın.\n&7Sebep: &f%sebep%\n&7Tarih: &f%tarih%\n&8Kayıt: %id%");
		String s = sablon.replace("%sebep%", k.sebep == null ? "Hile" : k.sebep).replace("%tarih%", TARIH.format(Instant.ofEpochMilli(k.zaman)))
			.replace("%id%", k.id);
		return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
	}

	// ── giriş ──────────────────────────────────────────────────────────────────

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPreLogin(AsyncPlayerPreLoginEvent event) {
		if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
		String ad = event.getName();
		String ip = CevherAuthPlugin.ipKey(event.getAddress());
		Kayit k = bul(ad, ip, null, null);
		if (k == null) return;
		boolean adla = k.adlar.contains(ad.toLowerCase(Locale.ROOT));
		genislet(k, ad, ip, null, null, adla ? "hesap adı" : "IP");
		event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, mesaj(k));
	}

	/** Mod: {"t":"iz","izler":["m:..","n:.."],"damgalar":[".."]} */
	@Override
	public void onPluginMessageReceived(String channel, Player player, byte[] message) {
		if (!KANAL.equals(channel) || message.length > 4096) return;
		Set<String> izler = new LinkedHashSet<>();
		Set<String> damgalar = new LinkedHashSet<>();
		try {
			JsonElement e = JsonParser.parseString(new String(message, StandardCharsets.UTF_8));
			if (!e.isJsonObject()) return;
			JsonObject o = e.getAsJsonObject();
			if (o.has("izler") && o.get("izler").isJsonArray()) {
				for (JsonElement x : o.getAsJsonArray("izler")) {
					String s = x.isJsonPrimitive() ? x.getAsString() : "";
					if (IZ.matcher(s).matches() && izler.size() < 12) izler.add(s);
				}
			}
			if (o.has("damgalar") && o.get("damgalar").isJsonArray()) {
				for (JsonElement x : o.getAsJsonArray("damgalar")) {
					String s = x.isJsonPrimitive() ? x.getAsString() : "";
					if (DAMGA.matcher(s).matches() && damgalar.size() < 8) damgalar.add(s);
				}
			}
		} catch (RuntimeException ex) {
			return;
		}
		String a = player.getName().toLowerCase(Locale.ROOT);
		if (!izler.isEmpty()) {
			Set<String> s = cihazlar.computeIfAbsent(a, x -> ConcurrentHashMap.newKeySet());
			if (s.addAll(izler)) cihazKirli = true;
			while (s.size() > 12) s.remove(s.iterator().next());
		}
		String ip = ipOf(player);
		Kayit k = bul(null, null, izler, damgalar);
		if (k == null) return;
		boolean damgali = false;
		for (String d : damgalar) damgali |= k.damgalar.contains(d);
		genislet(k, player.getName(), ip, izler, damgalar, damgali ? "bilgisayardaki yasak damgası" : "cihaz izi");
		plugin.riskTakip().ekle(player.getName(), ip, RiskTakip.Seviye.KRITIK, "kalici-yasak", "yasaklı " + k.ana + " ile aynı bilgisayar");
		Bukkit.getScheduler().runTask(plugin, () -> {
			if (player.isOnline()) {
				damgala(player, k);
				Bukkit.getScheduler().runTaskLater(plugin, () -> {
					if (player.isOnline()) player.kick(mesaj(k));
				}, 10L);
			}
		});
	}

	/** Bilgisayara yasak damgasını yazdırır (mod yüklüyse). */
	private void damgala(Player p, Kayit k) {
		if (!p.getListeningPluginChannels().contains(KANAL) || k.damgalar.isEmpty()) return;
		JsonObject o = new JsonObject();
		o.addProperty("t", "damga");
		o.addProperty("k", k.damgalar.iterator().next());
		p.sendPluginMessage(plugin, KANAL, o.toString().getBytes(StandardCharsets.UTF_8));
	}

	static String ipOf(Player p) {
		InetSocketAddress a = p.getAddress();
		return CevherAuthPlugin.ipKey(a == null ? null : a.getAddress());
	}

	// ── kalıcılık ──────────────────────────────────────────────────────────────

	private void kaydet() {
		try {
			JsonFile.write(dosya, new ArrayList<>(kayitlar));
		} catch (IOException e) {
			plugin.getLogger().severe("yasaklar.json yazılamadı: " + e.getMessage());
		}
	}

	void cihazKaydet() {
		if (!cihazKirli) return;
		cihazKirli = false;
		try {
			JsonFile.write(cihazDosya, new java.util.HashMap<>(cihazlar));
		} catch (IOException e) {
			plugin.getLogger().warning("cihazlar.json yazılamadı: " + e.getMessage());
		}
	}

	private void lobiyeGonder(String yol, JsonObject body) {
		String url = plugin.getConfig().getString("network.lobby-api", "http://127.0.0.1:25580").replaceAll("/+$", "") + yol;
		body.addProperty("secret", plugin.getConfig().getString("network.secret", ""));
		HttpRequest req = HttpRequest.newBuilder(URI.create(url))
			.timeout(Duration.ofSeconds(5))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(JsonFile.GSON.toJson(body), StandardCharsets.UTF_8))
			.build();
		http.sendAsync(req, HttpResponse.BodyHandlers.discarding()).whenComplete((r, t) -> {
			if (t != null) {
				plugin.getLogger().severe("Yasak lobiye bildirilemedi (" + yol + "): " + t.getMessage() + " — lobide /cevherauth yasakla ile elle ekleyin");
			} else if (r.statusCode() != 200) {
				plugin.getLogger().severe("Yasak lobiye bildirilemedi (" + yol + "): HTTP " + r.statusCode());
			}
		});
	}

	// ── lobi uç noktaları (AuthHttpServer çağırır) ─────────────────────────────

	void lobidenYasak(JsonObject b) {
		String ad = str(b, "ad");
		if (ad == null || ad.isEmpty() || ad.length() > 32) return;
		Set<String> izler = new LinkedHashSet<>();
		if (b.has("izler") && b.get("izler").isJsonArray()) {
			for (JsonElement x : b.getAsJsonArray("izler")) {
				if (x.isJsonPrimitive() && IZ.matcher(x.getAsString()).matches()) izler.add(x.getAsString());
			}
		}
		yasakla(ad, str(b, "ip"), izler, str(b, "damga"), str(b, "sebep"), str(b, "yetkili"), str(b, "vaka"), false);
	}

	void lobidenKaldir(JsonObject b) {
		String ad = str(b, "ad");
		if (ad != null && !ad.isEmpty()) kaldir(ad, false);
	}

	private static String str(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
	}
}
