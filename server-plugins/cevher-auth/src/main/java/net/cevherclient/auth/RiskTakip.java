package net.cevherclient.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * Oyuncu risk takibi: hileyle gelen oyuncuları tek bir defterde toplar, risk sınıfına ayırır,
 * sınıf yükseldiğinde konsola uyarı yazar ve OP'lara (cevher_client modu varsa ekranın sağ üstünde)
 * canlı liste gönderir.
 *
 * Kaynaklar:
 * <ul>
 *   <li>launcher: oyun açılmadan karantinaya alınan jar'lar (/api/join raporu; lobide),</li>
 *   <li>mod-dogrulama: sunucunun reddettiği mod listesi,</li>
 *   <li>hile-istemcisi: client brand / kanal / custom click tespiti,</li>
 *   <li>guvenlik: paket kalkanı ve Paper'ın güvenlik nedenli atmaları, IP engeli,</li>
 *   <li>vulcan: anticheat işaretleri (10 dk'lık pencerede puan) ve cezaları,</li>
 *   <li>ayni-ip: riskli bir hesapla aynı IP'den gelen diğer hesaplar.</li>
 * </ul>
 * Kapı sunucusu, lobideki bulguları giriş sorgusunun yanıtıyla alır; defter iki sunucuda da tutulur.
 */
final class RiskTakip implements Listener {
	static final String KANAL = "cevherclient:risk";
	private static final int MAX_BULGU = 40;
	private static final long CEVRIMDISI_GOSTER_MS = 10 * 60_000L;
	private static final long SAKLA_MS = 7L * 24 * 60 * 60_000L;

	enum Seviye {
		TEMIZ("TEMİZ", "&a"), DUSUK("DÜŞÜK", "&7"), ORTA("ORTA", "&e"), YUKSEK("YÜKSEK", "&6"), KRITIK("KRİTİK", "&c");

		final String etiket;
		final String renk;

		Seviye(String etiket, String renk) {
			this.etiket = etiket;
			this.renk = renk;
		}

		static Seviye bul(String s) {
			try {
				return s == null ? DUSUK : valueOf(s.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				return DUSUK;
			}
		}
	}

	record Bulgu(long zaman, Seviye seviye, String kaynak, String detay) {
	}

	static final class Kayit {
		final String ad;
		volatile String ip;
		volatile long sonGorulme;
		final Deque<Bulgu> bulgular = new ArrayDeque<>();
		/** Vulcan: [zaman, ağırlık] ve kontrol adı sayaçları (10 dk pencere). */
		final Deque<long[]> vulcan = new ArrayDeque<>();
		final Map<String, Integer> vulcanKontrol = new HashMap<>();
		Seviye vulcanSeviye = Seviye.TEMIZ;

		Kayit(String ad) {
			this.ad = ad;
		}
	}

	private record Ayar(boolean acik, long sureMs, boolean opSohbet, boolean vulcan, int vDusuk, int vOrta, int vYuksek,
		long vPencereMs, int savasCarpani) {
	}

	private final CevherAuthPlugin plugin;
	private final Map<String, Kayit> kayitlar = new ConcurrentHashMap<>();
	private final Path dosya;
	private final AtomicBoolean degisti = new AtomicBoolean();
	private volatile Ayar ayar;
	private String sonGonderilen = "";
	private long sonGonderim;

	RiskTakip(CevherAuthPlugin plugin) {
		this.plugin = plugin;
		this.dosya = plugin.getDataFolder().toPath().resolve("risk.json");
		yenile();
		yukle();
	}

	void yenile() {
		ConfigurationSection c = plugin.getConfig().getConfigurationSection("risk-takibi");
		if (c == null) {
			c = plugin.getConfig().createSection("risk-takibi");
		}
		ayar = new Ayar(
			c.getBoolean("enabled", true),
			Math.max(1, c.getLong("sure-saat", 24)) * 60 * 60_000L,
			c.getBoolean("op-sohbet-uyarisi", true),
			c.getBoolean("vulcan.enabled", true),
			Math.max(1, c.getInt("vulcan.dusuk", 3)),
			Math.max(1, c.getInt("vulcan.orta", 15)),
			Math.max(1, c.getInt("vulcan.yuksek", 40)),
			Math.max(1, c.getLong("vulcan.pencere-dakika", 10)) * 60_000L,
			Math.max(1, c.getInt("vulcan.savas-carpani", 2)));
	}

	void baslat() {
		plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, KANAL);
		plugin.getServer().getScheduler().runTaskTimer(plugin, this::hudGonder, 40L, 40L);
		plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::kaydetGerekirse, 20L * 30, 20L * 30);
		// Vulcan diğer eklentilerden sonra hazır olsun diye bir tick sonra bağlanılır.
		plugin.getServer().getScheduler().runTask(plugin, this::vulcanBagla);
	}

	void durdur() {
		degisti.set(true);
		kaydetGerekirse();
	}

	// ── defter ─────────────────────────────────────────────────────────────────

	private static String anahtar(String ad) {
		return ad.toLowerCase(Locale.ROOT);
	}

	private Kayit kayit(String ad) {
		return kayitlar.computeIfAbsent(anahtar(ad), k -> new Kayit(ad));
	}

	/** Son {@code sure-saat} içindeki en yüksek bulgu. */
	Seviye seviye(Kayit k) {
		long sinir = System.currentTimeMillis() - ayar.sureMs();
		Seviye s = Seviye.TEMIZ;
		synchronized (k) {
			for (Bulgu b : k.bulgular) {
				if (b.zaman() >= sinir && b.seviye().ordinal() > s.ordinal()) {
					s = b.seviye();
				}
			}
		}
		return s;
	}

	private static Bulgu enOnemli(Kayit k, long sinir) {
		Bulgu en = null;
		synchronized (k) {
			for (Bulgu b : k.bulgular) {
				if (b.zaman() >= sinir && (en == null || b.seviye().ordinal() >= en.seviye().ordinal())) {
					en = b;
				}
			}
		}
		return en;
	}

	/** Bulgu ekler; aynı kaynak+detay 60 sn içinde tekrar gelirse yok sayılır. */
	void ekle(String ad, String ip, Seviye seviye, String kaynak, String detay) {
		ekle(ad, ip, new Bulgu(System.currentTimeMillis(), seviye, kaynak, kisalt(detay, 200)), true);
	}

	private boolean ekle(String ad, String ip, Bulgu b, boolean tekrarKontrol) {
		if (!ayar.acik() || ad == null || ad.isEmpty()) {
			return false;
		}
		Kayit k = kayit(ad);
		if (ip != null) {
			k.ip = ip;
		}
		Seviye once = seviye(k);
		synchronized (k) {
			for (Bulgu x : k.bulgular) {
				boolean ayni = x.kaynak().equals(b.kaynak()) && x.detay().equals(b.detay());
				if (ayni && (x.zaman() == b.zaman() || (tekrarKontrol && Math.abs(b.zaman() - x.zaman()) < 60_000))) {
					return false;
				}
			}
			k.bulgular.addLast(b);
			while (k.bulgular.size() > MAX_BULGU) {
				k.bulgular.pollFirst();
			}
		}
		Seviye sonra = seviye(k);
		degisti.set(true);
		String satir = "[RİSK] " + k.ad + " (" + (k.ip == null ? "?" : k.ip) + ") "
			+ (sonra.ordinal() > once.ordinal() ? once.etiket + " → " + sonra.etiket : sonra.etiket)
			+ " | " + b.seviye().etiket + " " + b.kaynak() + ": " + b.detay();
		if (sonra.ordinal() > once.ordinal() && sonra.ordinal() >= Seviye.ORTA.ordinal()) {
			plugin.getLogger().warning(satir);
			if (ayar.opSohbet() && sonra.ordinal() >= Seviye.YUKSEK.ordinal()) {
				Component c = LegacyComponentSerializer.legacyAmpersand().deserialize("&8[&cRisk&8] &f" + k.ad + " "
					+ sonra.renk + sonra.etiket + " &7— " + b.kaynak() + ": " + kisalt(b.detay(), 90));
				plugin.getServer().getScheduler().runTask(plugin, () -> {
					for (Player p : plugin.getServer().getOnlinePlayers()) {
						if (yetkili(p)) {
							p.sendMessage(c);
						}
					}
				});
			}
		} else {
			plugin.getLogger().info(satir);
		}
		return true;
	}

	private static String kisalt(String s, int n) {
		if (s == null) {
			return "";
		}
		return s.length() <= n ? s : s.substring(0, n - 1) + "…";
	}

	static boolean yetkili(Player p) {
		return p.isOp() || p.hasPermission("cevherauth.risk");
	}

	boolean temizle(String ad) {
		degisti.set(true);
		return kayitlar.remove(anahtar(ad)) != null;
	}

	// ── launcher raporu / lobi ↔ kapı ──────────────────────────────────────────

	/** Launcher'ın /api/join ile gönderdiği karantina raporu (lobi). */
	void launcherRaporu(String ad, String ip, JsonArray dosyalar) {
		int n = 0;
		for (JsonElement e : dosyalar) {
			if (++n > 30 || !e.isJsonObject()) {
				break;
			}
			JsonObject o = e.getAsJsonObject();
			String risk = str(o, "risk");
			String reason = str(o, "reason");
			String modId = str(o, "modId");
			String name = str(o, "name");
			Seviye s = switch (risk == null ? "" : risk) {
				case "CRITICAL" -> Seviye.KRITIK;
				case "HIGH" -> Seviye.YUKSEK;
				default -> reason != null && reason.startsWith("Doğrulanamadı") ? Seviye.DUSUK : Seviye.ORTA;
			};
			String jar = (modId != null && !modId.isEmpty() ? modId : "?") + " [" + kisalt(name, 60) + "]";
			ekle(ad, ip, s, "launcher-karantina", jar + " — " + kisalt(reason, 140));
		}
	}

	private static String str(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
	}

	/** Lobi → kapı: bu oyuncunun son bulguları (giriş sorgusu yanıtına eklenir). */
	JsonArray disaAktar(String ad) {
		JsonArray arr = new JsonArray();
		Kayit k = kayitlar.get(anahtar(ad));
		if (k == null) {
			return arr;
		}
		long sinir = System.currentTimeMillis() - ayar.sureMs();
		synchronized (k) {
			for (Bulgu b : k.bulgular) {
				if (b.zaman() >= sinir) {
					arr.add(bulguJson(b));
				}
			}
		}
		return arr;
	}

	/** Kapı: lobiden gelen bulguları deftere alır (aynısı varsa atlanır). */
	void iceAktar(String ad, String ip, JsonArray arr) {
		for (JsonElement e : arr) {
			Bulgu b = bulgu(e);
			if (b != null) {
				ekle(ad, ip, new Bulgu(b.zaman(), b.seviye(), b.kaynak().startsWith("lobi:") ? b.kaynak() : "lobi:" + b.kaynak(), b.detay()), false);
			}
		}
	}

	private static JsonObject bulguJson(Bulgu b) {
		JsonObject o = new JsonObject();
		o.addProperty("t", b.zaman());
		o.addProperty("s", b.seviye().name());
		o.addProperty("k", b.kaynak());
		o.addProperty("d", b.detay());
		return o;
	}

	private static Bulgu bulgu(JsonElement e) {
		if (!e.isJsonObject()) {
			return null;
		}
		JsonObject o = e.getAsJsonObject();
		try {
			return new Bulgu(o.get("t").getAsLong(), Seviye.bul(str(o, "s")), kisalt(str(o, "k"), 40), kisalt(str(o, "d"), 200));
		} catch (RuntimeException ex) {
			return null;
		}
	}

	// ── oyuncu olayları ────────────────────────────────────────────────────────

	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(PlayerJoinEvent event) {
		Player p = event.getPlayer();
		InetSocketAddress a = p.getAddress();
		String ip = CevherAuthPlugin.ipKey(a == null ? null : a.getAddress());
		Kayit k = kayitlar.get(anahtar(p.getName()));
		if (k != null) {
			k.ip = ip;
			k.sonGorulme = System.currentTimeMillis();
		}
		// Riskli bir hesapla aynı IP'den gelen diğer hesaplar.
		if (!ip.equals("loopback") && !ip.equals("unknown")) {
			for (Kayit diger : kayitlar.values()) {
				if (diger != k && ip.equals(diger.ip) && !diger.ad.equalsIgnoreCase(p.getName())
					&& seviye(diger).ordinal() >= Seviye.YUKSEK.ordinal()) {
					ekle(p.getName(), ip, Seviye.DUSUK, "ayni-ip", diger.ad + " (" + seviye(diger).etiket + ") ile aynı IP");
					break;
				}
			}
		}
		if (yetkili(p)) {
			sonGonderilen = ""; // yeni OP'a liste hemen gitsin
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		Kayit k = kayitlar.get(anahtar(event.getPlayer().getName()));
		if (k != null) {
			k.sonGorulme = System.currentTimeMillis();
		}
	}

	// ── Vulcan ─────────────────────────────────────────────────────────────────

	@SuppressWarnings("unchecked")
	private void vulcanBagla() {
		Plugin vulcan = plugin.getServer().getPluginManager().getPlugin("Vulcan");
		if (vulcan == null || !vulcan.isEnabled() || !ayar.vulcan()) {
			return;
		}
		try {
			ClassLoader cl = vulcan.getClass().getClassLoader();
			Class<?> check = Class.forName("me.frep.vulcan.api.check.Check", true, cl);
			Method getCheck = Class.forName("me.frep.vulcan.api.event.VulcanPostFlagEvent", true, cl).getMethod("getCheck");
			Method getPlayer = Class.forName("me.frep.vulcan.api.event.VulcanPostFlagEvent", true, cl).getMethod("getPlayer");
			Method ad = check.getMethod("getDisplayName");
			Method kategori = check.getMethod("getCategory");
			Method tur = check.getMethod("getType");
			Class<? extends Event> flag = (Class<? extends Event>) Class.forName("me.frep.vulcan.api.event.VulcanPostFlagEvent", true, cl);
			Class<? extends Event> ceza = (Class<? extends Event>) Class.forName("me.frep.vulcan.api.event.VulcanPunishEvent", true, cl);
			Method cezaCheck = ceza.getMethod("getCheck");
			Method cezaPlayer = ceza.getMethod("getPlayer");
			plugin.getServer().getPluginManager().registerEvent(flag, this, EventPriority.MONITOR, (l, e) -> {
				try {
					Object c = getCheck.invoke(e);
					vulcanIsaret((Player) getPlayer.invoke(e), ad.invoke(c) + " " + tur.invoke(c), String.valueOf(kategori.invoke(c)));
				} catch (ReflectiveOperationException | RuntimeException ex) {
					// Vulcan sürümü farklı: işaret sayılmaz.
				}
			}, plugin, true);
			plugin.getServer().getPluginManager().registerEvent(ceza, this, EventPriority.MONITOR, (l, e) -> {
				try {
					Object c = cezaCheck.invoke(e);
					Player p = (Player) cezaPlayer.invoke(e);
					ekle(p.getName(), ip(p), Seviye.YUKSEK, "vulcan-ceza", ad.invoke(c) + " " + tur.invoke(c) + " (" + kategori.invoke(c) + ")");
				} catch (ReflectiveOperationException | RuntimeException ex) {
					// yok say
				}
			}, plugin, true);
			plugin.getLogger().info("Risk takibi: Vulcan işaretleri dinleniyor");
		} catch (ReflectiveOperationException | RuntimeException e) {
			plugin.getLogger().log(Level.WARNING, "Vulcan API'sine bağlanılamadı; anticheat işaretleri risk takibine katılmayacak", e);
		}
	}

	private static String ip(Player p) {
		InetSocketAddress a = p.getAddress();
		return CevherAuthPlugin.ipKey(a == null ? null : a.getAddress());
	}

	/** Bir Vulcan işareti: 10 dk penceredeki puan eşik geçince bulgu eklenir. Savaş kontrolleri ağır sayılır. */
	private void vulcanIsaret(Player p, String kontrol, String kategori) {
		Ayar a = ayar;
		if (p == null) {
			return;
		}
		Kayit k = kayit(p.getName());
		long simdi = System.currentTimeMillis();
		int agirlik = "combat".equalsIgnoreCase(kategori) ? a.savasCarpani() : 1;
		Seviye yeni;
		String ozet;
		synchronized (k) {
			k.vulcan.addLast(new long[] {simdi, agirlik});
			k.vulcanKontrol.merge(kontrol, 1, Integer::sum);
			while (!k.vulcan.isEmpty() && k.vulcan.peekFirst()[0] < simdi - a.vPencereMs()) {
				k.vulcan.pollFirst();
			}
			if (k.vulcan.isEmpty()) {
				k.vulcanKontrol.clear();
			}
			int puan = 0;
			for (long[] x : k.vulcan) {
				puan += (int) x[1];
			}
			yeni = puan >= a.vYuksek() ? Seviye.YUKSEK : puan >= a.vOrta() ? Seviye.ORTA : puan >= a.vDusuk() ? Seviye.DUSUK : Seviye.TEMIZ;
			if (yeni.ordinal() <= k.vulcanSeviye.ordinal()) {
				return;
			}
			k.vulcanSeviye = yeni;
			List<Map.Entry<String, Integer>> en = new ArrayList<>(k.vulcanKontrol.entrySet());
			en.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
			StringBuilder sb = new StringBuilder("puan " + puan + "/" + (a.vPencereMs() / 60_000) + "dk: ");
			for (int i = 0; i < Math.min(4, en.size()); i++) {
				sb.append(i > 0 ? ", " : "").append(en.get(i).getKey()).append(" x").append(en.get(i).getValue());
			}
			ozet = sb.toString();
		}
		ekle(p.getName(), ip(p), yeni, "vulcan", ozet);
	}

	// ── OP ekranı ──────────────────────────────────────────────────────────────

	/** OP'lara (cevher_client modu kanalı dinliyorsa) güncel listeyi gönderir. Ana iş parçacığı. */
	private void hudGonder() {
		long simdi = System.currentTimeMillis();
		long sinir = simdi - ayar.sureMs();
		// Vulcan seviyesi pencere boşalınca sıfırlanır (yeni işaretler yeniden eşik geçebilsin).
		for (Kayit k : kayitlar.values()) {
			synchronized (k) {
				if (!k.vulcan.isEmpty() && k.vulcan.peekLast()[0] < simdi - ayar.vPencereMs()) {
					k.vulcan.clear();
					k.vulcanKontrol.clear();
					k.vulcanSeviye = Seviye.TEMIZ;
				}
			}
		}
		List<Player> oplar = new ArrayList<>();
		for (Player p : plugin.getServer().getOnlinePlayers()) {
			if (yetkili(p) && p.getListeningPluginChannels().contains(KANAL)) {
				oplar.add(p);
			}
		}
		if (oplar.isEmpty()) {
			return;
		}
		List<JsonObject> satirlar = new ArrayList<>();
		for (Kayit k : kayitlar.values()) {
			Seviye s = seviye(k);
			if (s == Seviye.TEMIZ) {
				continue;
			}
			Player online = plugin.getServer().getPlayerExact(k.ad);
			if (online == null && simdi - k.sonGorulme > CEVRIMDISI_GOSTER_MS) {
				continue;
			}
			Bulgu en = enOnemli(k, sinir);
			JsonObject o = new JsonObject();
			o.addProperty("ad", k.ad);
			o.addProperty("seviye", s.name());
			o.addProperty("neden", en == null ? "" : kisalt(en.kaynak() + ": " + en.detay(), 70));
			o.addProperty("online", online != null);
			satirlar.add(o);
		}
		satirlar.sort(Comparator.<JsonObject>comparingInt(o -> -Seviye.bul(o.get("seviye").getAsString()).ordinal())
			.thenComparing(o -> o.get("ad").getAsString()));
		JsonArray arr = new JsonArray();
		satirlar.stream().limit(15).forEach(arr::add);
		JsonObject msg = new JsonObject();
		msg.addProperty("t", "liste");
		msg.add("oyuncular", arr);
		String json = msg.toString();
		if (json.equals(sonGonderilen) && simdi - sonGonderim < 10_000) {
			return;
		}
		sonGonderilen = json;
		sonGonderim = simdi;
		byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
		for (Player p : oplar) {
			p.sendPluginMessage(plugin, KANAL, bytes);
		}
	}

	// ── komut ──────────────────────────────────────────────────────────────────

	List<String> rapor(String ad) {
		List<String> out = new ArrayList<>();
		long sinir = System.currentTimeMillis() - ayar.sureMs();
		if (ad != null) {
			Kayit k = kayitlar.get(anahtar(ad));
			if (k == null) {
				out.add("&7" + ad + " için kayıt yok.");
				return out;
			}
			Seviye s = seviye(k);
			out.add("&b" + k.ad + " &8| " + s.renk + s.etiket + " &8| &7IP " + k.ip);
			synchronized (k) {
				List<Bulgu> l = new ArrayList<>(k.bulgular);
				for (int i = l.size() - 1; i >= 0 && out.size() < 16; i--) {
					Bulgu b = l.get(i);
					out.add(" " + b.seviye().renk + b.seviye().etiket + (b.zaman() < sinir ? " &8(eski)" : "") + " &7" + b.kaynak() + ": &f" + b.detay());
				}
			}
			return out;
		}
		List<Kayit> l = new ArrayList<>(kayitlar.values());
		l.removeIf(k -> seviye(k) == Seviye.TEMIZ);
		l.sort(Comparator.comparingInt((Kayit k) -> -seviye(k).ordinal()).thenComparing(k -> k.ad));
		out.add("&bRisk listesi &7(son " + (ayar.sureMs() / 3_600_000) + " saat) — " + l.size() + " oyuncu");
		for (Kayit k : l) {
			Seviye s = seviye(k);
			Bulgu en = enOnemli(k, sinir);
			boolean online = plugin.getServer().getPlayerExact(k.ad) != null;
			out.add(" " + s.renk + s.etiket + " &f" + k.ad + (online ? " &a●" : " &8●") + " &7" + (en == null ? "" : kisalt(en.kaynak() + ": " + en.detay(), 80)));
			if (out.size() > 25) {
				break;
			}
		}
		return out;
	}

	// ── kalıcılık ──────────────────────────────────────────────────────────────

	private void yukle() {
		if (!Files.isRegularFile(dosya)) {
			return;
		}
		try {
			JsonObject root = JsonParser.parseString(Files.readString(dosya, StandardCharsets.UTF_8)).getAsJsonObject();
			long sinir = System.currentTimeMillis() - SAKLA_MS;
			for (Map.Entry<String, JsonElement> e : root.entrySet()) {
				JsonObject o = e.getValue().getAsJsonObject();
				Kayit k = new Kayit(str(o, "ad") == null ? e.getKey() : str(o, "ad"));
				k.ip = str(o, "ip");
				k.sonGorulme = o.has("son") ? o.get("son").getAsLong() : 0;
				for (JsonElement b : o.getAsJsonArray("bulgular")) {
					Bulgu x = bulgu(b);
					if (x != null && x.zaman() >= sinir) {
						k.bulgular.addLast(x);
					}
				}
				if (!k.bulgular.isEmpty()) {
					kayitlar.put(anahtar(k.ad), k);
				}
			}
		} catch (IOException | RuntimeException e) {
			plugin.getLogger().warning("risk.json okunamadı: " + e.getMessage());
		}
	}

	private void kaydetGerekirse() {
		if (!degisti.getAndSet(false)) {
			return;
		}
		JsonObject root = new JsonObject();
		long sinir = System.currentTimeMillis() - SAKLA_MS;
		Map<String, Kayit> sirali = new LinkedHashMap<>(kayitlar);
		for (Map.Entry<String, Kayit> e : sirali.entrySet()) {
			Kayit k = e.getValue();
			JsonArray arr = new JsonArray();
			synchronized (k) {
				for (Bulgu b : k.bulgular) {
					if (b.zaman() >= sinir) {
						arr.add(bulguJson(b));
					}
				}
			}
			if (arr.isEmpty()) {
				continue;
			}
			JsonObject o = new JsonObject();
			o.addProperty("ad", k.ad);
			o.addProperty("ip", k.ip);
			o.addProperty("son", k.sonGorulme);
			o.add("bulgular", arr);
			root.add(e.getKey(), o);
		}
		try {
			Files.createDirectories(dosya.getParent());
			Path tmp = dosya.resolveSibling("risk.json.tmp");
			Files.writeString(tmp, JsonFile.GSON.toJson(root), StandardCharsets.UTF_8);
			Files.move(tmp, dosya, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			degisti.set(true);
			plugin.getLogger().warning("risk.json yazılamadı: " + e.getMessage());
		}
	}
}
