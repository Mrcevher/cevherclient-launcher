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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * Kalıcı yasak (ör. CevherKoruma'da yetkilinin "Hileydi → Onayla" kararı). Bir yasak dört şeyle eşleşir: hesap adı,
 * IP, Cevherclient'ın gönderdiği cihaz izi ve bilgisayara yazılan damga. Eşleşen her yeni ad / IP / iz yasağa eklenir.
 *
 * <p>Asıl kayıt lobidedir (giriş kapısı). Kapı sunucusu (skyblock) yasakları lobiye iletir ve lobideki listeyi düzenli
 * olarak çeker. Yasağın kaldırılması iki onay ister: biri kurucu ({@code cevherauth.yasak.kurucu}), diğeri üst yetkili
 * ({@code cevherauth.yasak.ustyetkili}) ya da başka bir kurucu. Yasaklı oyuncu siteden itiraz talebi gönderebilir;
 * talep yetkililerin vaka ekranında görünür ve reddedilebilir.
 */
final class KaliciYasak implements Listener, PluginMessageListener {
	static final String KANAL = "cevherclient:cihaz";
	static final String ROL_KURUCU = "KURUCU";
	static final String ROL_UST = "ÜST YETKİLİ";
	private static final Pattern IZ = Pattern.compile("[a-z]:[0-9a-f]{16,64}");
	private static final Pattern DAMGA = Pattern.compile("[A-Za-z0-9_-]{16,64}");
	private static final DateTimeFormatter TARIH = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());
	private static final int OP_MAX_BAYT = 58_000;

	/** Yasağın dayandığı vaka kanıtı (CevherKoruma'dan). */
	static final class Kanit {
		int guven = -1;
		String karar = "";
		String tur = "";
		String ozet = "";
		String gerekce = "";
		String not = "";
		String dunya = "";
		String konum = "";
		String istemci = "";
		int ping;
		double tps;
		double puan;
		long vakaBas;
		long vakaBit;
		List<String> kontroller = new ArrayList<>();
		List<String> satirlar = new ArrayList<>();
	}

	static final class Onay {
		String ad;
		String rol;
		long zaman;
	}

	static final class Talep {
		String id;
		long zaman;
		String hesap;
		String mesaj;
		String durum = "BEKLIYOR";
		String islemYapan = "";
		String islemNeden = "";
		long islemZaman;
	}

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
		Kanit kanit;
		List<Onay> onaylar = new ArrayList<>();
		List<Talep> talepler = new ArrayList<>();
		/**
		 * Yetkili bu yasak hakkında karar verdi (itiraz / kaldırma reddedildi): yasak sürer ama vaka ekranının "Yasaklar"
		 * listesinden düşer. Yeni itiraz ya da yeni kaldırma onayı gelince yeniden görünür.
		 */
		boolean islendi;
		/** Yalnızca kapıda: lobiye henüz ulaşmamış yerel kayıt (eşitlemede silinmez). */
		transient long yerelZaman;
	}

	private final CevherAuthPlugin plugin;
	private final Path dosya;
	private final Path cihazDosya;
	private final Path arsivDosya;
	private final List<Kayit> kayitlar = new CopyOnWriteArrayList<>();
	/** küçük harf ad → son görülen cihaz izleri. */
	private final Map<String, Set<String>> cihazlar = new ConcurrentHashMap<>();
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
	private volatile boolean cihazKirli;
	private volatile String sonOpJson = "";

	KaliciYasak(CevherAuthPlugin plugin) {
		this.plugin = plugin;
		this.dosya = plugin.getDataFolder().toPath().resolve("yasaklar.json");
		this.cihazDosya = plugin.getDataFolder().toPath().resolve("cihazlar.json");
		this.arsivDosya = plugin.getDataFolder().toPath().resolve("kaldirilan-yasaklar.txt");
		try {
			List<Kayit> l = JsonFile.read(dosya, new TypeToken<List<Kayit>>() { }.getType(), new ArrayList<Kayit>());
			for (Kayit k : l) {
				if (k != null && k.id != null) {
					duzelt(k);
					kayitlar.add(k);
				}
			}
			Map<String, Set<String>> c = JsonFile.read(cihazDosya, new TypeToken<Map<String, Set<String>>>() { }.getType(), Map.<String, Set<String>>of());
			c.forEach((ad, iz) -> {
				Set<String> s = ConcurrentHashMap.newKeySet();
				s.addAll(iz);
				cihazlar.put(ad, s);
			});
		} catch (IOException e) {
			plugin.getLogger().severe("yasaklar.json okunamadı: " + e.getMessage());
		}
		plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, KANAL, this);
		plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, KANAL);
		plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, RiskTakip.KANAL);
		plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::cihazKaydet, 20L * 60, 20L * 60);
		if (plugin.isGate()) {
			plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::lobidenCek, 20L * 5, 20L * 30);
		}
		plugin.getLogger().info("Kalıcı yasak: " + kayitlar.size() + " kayıt");
	}

	private static void duzelt(Kayit k) {
		if (k.adlar == null) k.adlar = new LinkedHashSet<>();
		if (k.ipler == null) k.ipler = new LinkedHashSet<>();
		if (k.cihazlar == null) k.cihazlar = new LinkedHashSet<>();
		if (k.damgalar == null) k.damgalar = new LinkedHashSet<>();
		if (k.gecmis == null) k.gecmis = new ArrayList<>();
		if (k.onaylar == null) k.onaylar = new ArrayList<>();
		if (k.talepler == null) k.talepler = new ArrayList<>();
		if (k.sebep == null) k.sebep = "Hile";
		if (k.yetkili == null) k.yetkili = "?";
		if (k.vaka == null) k.vaka = "";
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

	private static String simdi() {
		return TARIH.format(Instant.now());
	}

	/** Komutu verenin yasak kaldırma rolü; yetkisi yoksa null. Konsol kurucu sayılır. */
	static String rol(CommandSender s) {
		if (s instanceof ConsoleCommandSender || s.hasPermission("cevherauth.yasak.kurucu")) {
			return ROL_KURUCU;
		}
		if (s.hasPermission("cevherauth.yasak.ustyetkili")) {
			return ROL_UST;
		}
		return null;
	}

	// ── sorgu ──────────────────────────────────────────────────────────────────

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
	synchronized Kayit yasakla(String ad, String ip, Set<String> ekIz, String damga, String sebep, String yetkili, String vaka, Kanit kanit,
			String istenenId, boolean yay) {
		String a = ad.toLowerCase(Locale.ROOT);
		Kayit k = bulAdVeyaIp(ad);
		boolean yeni = k == null;
		if (yeni) {
			k = new Kayit();
			k.id = istenenId != null && istenenId.matches("[A-Za-z0-9_-]{4,16}") ? istenenId : Passwords.randomToken().substring(0, 8);
			k.ana = ad;
			k.sebep = sebep == null || sebep.isBlank() ? "Hile" : sebep;
			k.yetkili = yetkili == null || yetkili.isBlank() ? "?" : yetkili;
			k.vaka = vaka == null ? "" : vaka;
			k.zaman = System.currentTimeMillis();
			k.kanit = kanit;
			k.damgalar.add(damga != null && DAMGA.matcher(damga).matches() ? damga : Passwords.randomToken().substring(0, 32));
			k.gecmis.add(simdi() + " yasaklandı: " + ad + " — " + k.sebep + " (" + k.yetkili + (k.vaka.isEmpty() ? "" : ", vaka " + k.vaka) + ")");
			if (plugin.isGate()) k.yerelZaman = System.currentTimeMillis();
			kayitlar.add(k);
		} else {
			if (damga != null && DAMGA.matcher(damga).matches()) k.damgalar.add(damga);
			if (k.kanit == null && kanit != null) k.kanit = kanit;
		}
		k.adlar.add(a);
		if (gecerliIp(ip)) k.ipler.add(ip);
		Set<String> izler = cihazlar.get(a);
		if (izler != null) k.cihazlar.addAll(izler);
		if (ekIz != null) for (String z : ekIz) if (IZ.matcher(z).matches()) k.cihazlar.add(z);
		if (bagliHesap() && !k.cihazlar.isEmpty()) {
			for (Map.Entry<String, Set<String>> e : cihazlar.entrySet()) {
				for (String z : e.getValue()) {
					if (k.cihazlar.contains(z) && k.adlar.add(e.getKey())) {
						k.gecmis.add(simdi() + " aynı bilgisayarda görülen hesap eklendi: " + e.getKey());
					}
				}
			}
		}
		kaydet();
		plugin.getLogger().warning("[YASAK] " + ad + " kalıcı yasaklandı (" + k.id + "): " + k.adlar.size() + " hesap, " + k.ipler.size() + " IP, "
			+ k.cihazlar.size() + " cihaz izi — " + k.sebep);
		final Kayit son = k;
		anaIs(() -> {
			for (Player p : Bukkit.getOnlinePlayers()) {
				if (son.adlar.contains(p.getName().toLowerCase(Locale.ROOT)) || (ipYasagi() && son.ipler.contains(ipOf(p)))) {
					damgala(p, son);
					Bukkit.getScheduler().runTaskLater(plugin, () -> {
						if (p.isOnline()) p.kick(mesaj(son));
					}, 10L);
				}
			}
		});
		if (yay && plugin.isGate()) {
			lobiyeGonder("/api/internal/ban", lobiGovdesi(ad, ip, k, sebep, yetkili, vaka), null);
		}
		return k;
	}

	private JsonObject lobiGovdesi(String ad, String ip, Kayit k, String sebep, String yetkili, String vaka) {
		JsonObject o = new JsonObject();
		o.addProperty("id", k.id);
		o.addProperty("ad", ad);
		o.addProperty("ip", ip == null ? "" : ip);
		o.addProperty("damga", k.damgalar.iterator().next());
		JsonArray iz = new JsonArray();
		k.cihazlar.forEach(iz::add);
		o.add("izler", iz);
		o.addProperty("sebep", sebep == null ? "" : sebep);
		o.addProperty("yetkili", yetkili == null ? "" : yetkili);
		o.addProperty("vaka", vaka == null ? "" : vaka);
		if (k.kanit != null) o.add("kanit", JsonFile.GSON.toJsonTree(k.kanit));
		return o;
	}

	/** Yasağı doğrudan kaldırır (iki onay tamamlandığında veya konsoldan). */
	private synchronized Kayit kaldirDogrudan(Kayit k, String neden) {
		if (!kayitlar.remove(k)) return null;
		k.gecmis.add(simdi() + " yasak kaldırıldı: " + neden);
		kaydet();
		arsivle(k, neden);
		plugin.getLogger().warning("[YASAK] kaldırıldı: " + k.ana + " (" + k.id + ") — " + neden);
		return k;
	}

	/** Konsoldan doğrudan kaldırma (onay gerekmez). Kapıdaysa lobiye de iletilir. */
	void konsoldanKaldir(String hedef, Consumer<String> cevap) {
		if (plugin.isGate()) {
			JsonObject o = new JsonObject();
			o.addProperty("islem", "kaldir");
			o.addProperty("hedef", hedef);
			o.addProperty("ad", "CONSOLE");
			lobiyeGonder("/api/internal/yasak-islem", o, cevap);
			return;
		}
		Kayit k = bulAdVeyaIp(hedef);
		cevap.accept(k == null ? "Böyle bir yasak yok." : kaldirDogrudan(k, "konsoldan kaldırıldı") != null ? k.ana + " yasağı kaldırıldı." : "Kaldırılamadı.");
	}

	/**
	 * Yasak kaldırma onayı. İki farklı kişi onaylayınca (en az biri kurucu) yasak kalkar. Kapı sunucusundaysa işlem
	 * lobide yapılır; sonuç {@code cevap}'a gelir.
	 */
	void onayla(String hedef, String ad, String rol, Consumer<String> cevap) {
		if (rol == null) {
			cevap.accept("Yasak kaldırma onayı için yetkin yok (üst yetkili veya kurucu olmalısın).");
			return;
		}
		if (plugin.isGate()) {
			JsonObject o = new JsonObject();
			o.addProperty("islem", "onay");
			o.addProperty("hedef", hedef);
			o.addProperty("ad", ad);
			o.addProperty("rol", rol);
			lobiyeGonder("/api/internal/yasak-islem", o, cevap);
			return;
		}
		cevap.accept(onaylaYerel(hedef, ad, rol));
	}

	private synchronized String onaylaYerel(String hedef, String ad, String rol) {
		Kayit k = bulAdVeyaIp(hedef);
		if (k == null) return "Böyle bir yasak yok: " + hedef;
		for (Onay o : k.onaylar) {
			if (o.ad.equalsIgnoreCase(ad)) return "Bu yasağın kaldırılmasını zaten onayladın (" + k.onaylar.size() + "/2).";
		}
		Onay o = new Onay();
		o.ad = ad;
		o.rol = rol;
		o.zaman = System.currentTimeMillis();
		k.onaylar.add(o);
		k.islendi = false;
		k.gecmis.add(simdi() + " yasak kaldırma onayı: " + ad + " (" + rol + ")");
		boolean kurucuVar = k.onaylar.stream().anyMatch(x -> ROL_KURUCU.equals(x.rol));
		if (k.onaylar.size() >= 2 && kurucuVar) {
			StringBuilder kim = new StringBuilder();
			for (Onay x : k.onaylar) kim.append(kim.length() > 0 ? " + " : "").append(x.ad).append(" (").append(x.rol).append(")");
			for (Talep t : k.talepler) {
				if ("BEKLIYOR".equals(t.durum)) {
					t.durum = "KABUL";
					t.islemYapan = kim.toString();
					t.islemZaman = System.currentTimeMillis();
				}
			}
			kaldirDogrudan(k, "iki onayla kaldırıldı: " + kim);
			return k.ana + " yasağı kaldırıldı (2/2 onay: " + kim + ").";
		}
		kaydet();
		return "Onayın kaydedildi (" + k.onaylar.size() + "/2). " + (kurucuVar ? "İkinci bir üst yetkili ya da kurucu onayı bekleniyor." : "Bir kurucu onayı bekleniyor.");
	}

	/** Bekleyen itiraz talebini reddeder (talepId boşsa en yeni bekleyen). */
	void talepReddet(String hedef, String talepId, String ad, String rol, String neden, Consumer<String> cevap) {
		if (rol == null) {
			cevap.accept("Talep reddetmek için yetkin yok (üst yetkili veya kurucu olmalısın).");
			return;
		}
		if (plugin.isGate()) {
			JsonObject o = new JsonObject();
			o.addProperty("islem", "red");
			o.addProperty("hedef", hedef);
			o.addProperty("talep", talepId == null ? "" : talepId);
			o.addProperty("ad", ad);
			o.addProperty("rol", rol);
			o.addProperty("neden", neden == null ? "" : neden);
			lobiyeGonder("/api/internal/yasak-islem", o, cevap);
			return;
		}
		cevap.accept(talepReddetYerel(hedef, talepId, ad, neden));
	}

	/**
	 * Yetkilinin "Reddet" kararı: bekleyen itiraz varsa reddedilir (oyuncu 24 saat yeni itiraz açamaz), verilmiş kaldırma
	 * onayları sıfırlanır ve yasak vaka ekranındaki listeden düşer (yasak sürer).
	 */
	private synchronized String talepReddetYerel(String hedef, String talepId, String ad, String neden) {
		Kayit k = bulAdVeyaIp(hedef);
		if (k == null) return "Böyle bir yasak yok: " + hedef;
		String nd = neden == null ? "" : neden.length() > 200 ? neden.substring(0, 200) : neden;
		Talep t = null;
		for (int i = k.talepler.size() - 1; i >= 0; i--) {
			Talep x = k.talepler.get(i);
			if ("BEKLIYOR".equals(x.durum) && (talepId == null || talepId.isEmpty() || x.id.equalsIgnoreCase(talepId))) {
				t = x;
				break;
			}
		}
		if (t != null) {
			t.durum = "REDDEDILDI";
			t.islemYapan = ad;
			t.islemNeden = nd;
			t.islemZaman = System.currentTimeMillis();
			k.gecmis.add(simdi() + " itiraz talebi reddedildi: " + ad + (nd.isEmpty() ? "" : " — " + nd));
		} else {
			k.gecmis.add(simdi() + " yasak kaldırma reddedildi: " + ad + (nd.isEmpty() ? "" : " — " + nd));
		}
		int onaySayisi = k.onaylar.size();
		k.onaylar.clear();
		k.islendi = true;
		kaydet();
		return k.ana + (t != null ? " için itiraz reddedildi (24 saat yeni itiraz açamaz)" : " için kaldırma reddedildi, yasak sürüyor")
			+ (onaySayisi > 0 ? "; " + onaySayisi + " kaldırma onayı sıfırlandı." : ".");
	}

	/** Site → lobi: yasaklı hesabın itiraz talebi. Dönüş: durum kodu. */
	synchronized String talepEkle(String hesap, String mesaj) {
		Kayit k = bulAdVeyaIp(hesap);
		if (k == null) return "YASAK_YOK";
		for (Talep t : k.talepler) {
			if ("BEKLIYOR".equals(t.durum)) return "ZATEN_BEKLIYOR";
		}
		long simdi = System.currentTimeMillis();
		// Reddedilen itirazdan sonra 24 saat yeni itiraz açılamaz (site "COK_SIK" mesajını gösterir).
		for (Talep t : k.talepler) {
			if ("REDDEDILDI".equals(t.durum) && t.islemZaman > simdi - 86_400_000L) return "COK_SIK";
		}
		long son24 = k.talepler.stream().filter(t -> t.zaman > simdi - 86_400_000L).count();
		if (son24 >= 2) return "COK_SIK";
		Talep t = new Talep();
		t.id = Passwords.randomToken().substring(0, 6);
		t.zaman = System.currentTimeMillis();
		t.hesap = hesap;
		t.mesaj = mesaj.length() > 1000 ? mesaj.substring(0, 1000) : mesaj;
		k.talepler.add(t);
		k.islendi = false;
		while (k.talepler.size() > 20) k.talepler.remove(0);
		k.gecmis.add(simdi() + " siteden itiraz talebi geldi (" + hesap + ")");
		kaydet();
		plugin.getLogger().warning("[YASAK] " + hesap + " siteden itiraz talebi gönderdi (" + k.id + ")");
		anaIs(() -> {
			for (Player p : Bukkit.getOnlinePlayers()) {
				if (RiskTakip.yetkili(p)) {
					p.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(
						"&6[Yasak] &f" + hesap + " &7siteden yasak itirazı gönderdi. &eJ &7→ Yasaklar sekmesinden inceleyebilirsin."));
				}
			}
		});
		return "OK";
	}

	/** Site için: hesabın yasak ve talep durumu. */
	JsonObject durum(String hesap) {
		Kayit k = bulAdVeyaIp(hesap);
		JsonObject o = new JsonObject();
		o.addProperty("yasakli", k != null);
		if (k == null) return o;
		o.addProperty("sebep", k.sebep);
		o.addProperty("tarih", TARIH.format(Instant.ofEpochMilli(k.zaman)));
		o.addProperty("onay", k.onaylar.size());
		JsonArray a = new JsonArray();
		for (Talep t : k.talepler) {
			JsonObject j = new JsonObject();
			j.addProperty("tarih", TARIH.format(Instant.ofEpochMilli(t.zaman)));
			j.addProperty("durum", t.durum);
			j.addProperty("neden", t.islemNeden);
			a.add(j);
		}
		o.add("talepler", a);
		return o;
	}

	private void arsivle(Kayit k, String neden) {
		StringBuilder sb = new StringBuilder();
		sb.append("════════════════════════════════════════════════════════════\n");
		sb.append("YASAK KALDIRILDI — ").append(k.ana).append(" (kayıt ").append(k.id).append(")\n");
		sb.append("Kaldırılma: ").append(simdi()).append(" — ").append(neden).append('\n');
		sb.append("Yasaklanma: ").append(TARIH.format(Instant.ofEpochMilli(k.zaman))).append(" — ").append(k.sebep).append(" (").append(k.yetkili).append(")\n");
		sb.append("Hesaplar: ").append(String.join(", ", k.adlar)).append('\n');
		sb.append("IP'ler: ").append(String.join(", ", k.ipler)).append('\n');
		sb.append("Geçmiş:\n");
		for (String g : k.gecmis) sb.append("  • ").append(g).append('\n');
		sb.append('\n');
		try {
			Files.writeString(arsivDosya, sb.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			plugin.getLogger().warning("kaldirilan-yasaklar.txt yazılamadı: " + e.getMessage());
		}
	}

	/** Eşleşen yasağa yeni ad / IP / cihaz izi / damga ekler; değişiklik olduysa kaydeder. */
	private void genislet(Kayit k, String ad, String ip, Set<String> izler, Set<String> damgalar, String nasil) {
		boolean degisti = false;
		String a = ad == null ? null : ad.toLowerCase(Locale.ROOT);
		synchronized (this) {
			if (a != null && bagliHesap() && k.adlar.add(a)) {
				k.gecmis.add(simdi() + " yan hesap yakalandı: " + ad + " (" + nasil + ")");
				degisti = true;
			}
			if (ipYasagi() && gecerliIp(ip) && k.ipler.add(ip)) {
				k.gecmis.add(simdi() + " yeni IP eklendi: " + ip + " (" + nasil + ")");
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
		String s = sablon.replace("%sebep%", k.sebep).replace("%tarih%", TARIH.format(Instant.ofEpochMilli(k.zaman))).replace("%id%", k.id);
		String itiraz = plugin.getConfig().getString("kalici-yasak.itiraz-adresi", "");
		if (!itiraz.isBlank()) s += "\n&7İtiraz: &f" + itiraz;
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

	/** OP modu risk kanalını kaydedince yasak listesini gönder. */
	@EventHandler
	public void onKanal(PlayerRegisterChannelEvent e) {
		if (RiskTakip.KANAL.equals(e.getChannel()) && RiskTakip.yetkili(e.getPlayer())) {
			Player p = e.getPlayer();
			Bukkit.getScheduler().runTaskLater(plugin, () -> opGonder(p), 30L);
		}
	}

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

	private void anaIs(Runnable r) {
		if (Bukkit.isPrimaryThread()) r.run();
		else Bukkit.getScheduler().runTask(plugin, r);
	}

	// ── OP ekranı (Cevherclient vaka ekranının "Yasaklar" sekmesi) ─────────────

	private JsonObject opJson() {
		JsonArray arr = new JsonArray();
		List<Kayit> l = new ArrayList<>(kayitlar);
		l.sort((x, y) -> Long.compare(y.zaman, x.zaman));
		int boyut = 0;
		for (Kayit k : l) {
			if (k.islendi) continue; // karar verilmiş yasak ekranda gösterilmez (yasak sürer)
			JsonObject o = new JsonObject();
			o.addProperty("id", k.id);
			o.addProperty("ana", k.ana);
			JsonArray adlar = new JsonArray();
			k.adlar.forEach(adlar::add);
			o.add("adlar", adlar);
			JsonArray ipler = new JsonArray();
			k.ipler.forEach(ip -> ipler.add(ipGizle(ip)));
			o.add("ipler", ipler);
			o.addProperty("cihaz", k.cihazlar.size());
			o.addProperty("damga", k.damgalar.size());
			o.addProperty("sebep", k.sebep);
			o.addProperty("yetkili", k.yetkili);
			o.addProperty("vaka", k.vaka);
			o.addProperty("zaman", k.zaman);
			JsonArray g = new JsonArray();
			for (int i = Math.max(0, k.gecmis.size() - 15); i < k.gecmis.size(); i++) g.add(k.gecmis.get(i));
			o.add("gecmis", g);
			if (k.kanit != null) {
				JsonObject kn = JsonFile.GSON.toJsonTree(k.kanit).getAsJsonObject();
				if (kn.has("satirlar") && kn.getAsJsonArray("satirlar").size() > 25) {
					JsonArray s = new JsonArray();
					for (int i = 0; i < 25; i++) s.add(kn.getAsJsonArray("satirlar").get(i));
					kn.add("satirlar", s);
				}
				o.add("kanit", kn);
			}
			o.add("onaylar", JsonFile.GSON.toJsonTree(k.onaylar));
			o.add("talepler", JsonFile.GSON.toJsonTree(k.talepler));
			int b = o.toString().getBytes(StandardCharsets.UTF_8).length;
			if (boyut + b > OP_MAX_BAYT) break;
			boyut += b;
			arr.add(o);
		}
		JsonObject m = new JsonObject();
		m.addProperty("t", "yasaklar");
		m.add("yasaklar", arr);
		return m;
	}

	/** Ekranda IP'nin yalnızca ilk bölümü görünür: 176.42.242.198 → 176.xxx.xxx.xxx */
	static String ipGizle(String ip) {
		if (ip == null || ip.isEmpty()) return "";
		char ayrac = ip.indexOf('.') >= 0 ? '.' : ':';
		int i = ip.indexOf(ayrac);
		if (i <= 0) return ip.length() <= 3 ? ip : ip.substring(0, 3) + "xxxxx";
		StringBuilder sb = new StringBuilder(ip.substring(0, i));
		int parca = ayrac == '.' ? 3 : 2;
		for (int n = 0; n < parca; n++) sb.append(ayrac).append("xxx");
		return sb.toString();
	}

	private void opGonder(Player p) {
		if (!p.isOnline() || !RiskTakip.yetkili(p) || !p.getListeningPluginChannels().contains(RiskTakip.KANAL)) return;
		p.sendPluginMessage(plugin, RiskTakip.KANAL, opJson().toString().getBytes(StandardCharsets.UTF_8));
	}

	/** Liste değiştiyse tüm çevrimiçi OP'lere gönder (ana iş parçacığı). */
	private void oplaraGonder() {
		anaIs(() -> {
			String json = opJson().toString();
			if (json.equals(sonOpJson)) return;
			sonOpJson = json;
			byte[] b = json.getBytes(StandardCharsets.UTF_8);
			for (Player p : Bukkit.getOnlinePlayers()) {
				if (RiskTakip.yetkili(p) && p.getListeningPluginChannels().contains(RiskTakip.KANAL)) {
					p.sendPluginMessage(plugin, RiskTakip.KANAL, b);
				}
			}
		});
	}

	// ── kalıcılık ──────────────────────────────────────────────────────────────

	private void kaydet() {
		try {
			JsonFile.write(dosya, new ArrayList<>(kayitlar));
		} catch (IOException e) {
			plugin.getLogger().severe("yasaklar.json yazılamadı: " + e.getMessage());
		}
		oplaraGonder();
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

	// ── kapı ↔ lobi ────────────────────────────────────────────────────────────

	private String lobiAdresi(String yol) {
		return plugin.getConfig().getString("network.lobby-api", "http://127.0.0.1:25580").replaceAll("/+$", "") + yol;
	}

	/** Lobiye iç istek; {@code cevap} null değilse lobinin "mesaj" alanı (ya da hata) ana iş parçacığında iletilir. */
	private void lobiyeGonder(String yol, JsonObject body, Consumer<String> cevap) {
		body.addProperty("secret", plugin.getConfig().getString("network.secret", ""));
		HttpRequest req = HttpRequest.newBuilder(URI.create(lobiAdresi(yol)))
			.timeout(Duration.ofSeconds(6))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(JsonFile.GSON.toJson(body), StandardCharsets.UTF_8))
			.build();
		CompletableFuture<HttpResponse<String>> f = http.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		f.whenComplete((r, t) -> {
			String sonuc;
			if (t != null) {
				sonuc = "Lobiye ulaşılamadı: " + t.getMessage();
				plugin.getLogger().severe("Yasak işlemi lobiye iletilemedi (" + yol + "): " + t.getMessage());
			} else if (r.statusCode() != 200) {
				sonuc = "Lobi isteği reddetti (HTTP " + r.statusCode() + ")";
				plugin.getLogger().severe("Yasak işlemi lobiye iletilemedi (" + yol + "): HTTP " + r.statusCode());
			} else {
				String m = "Tamam.";
				try {
					JsonObject j = JsonParser.parseString(r.body()).getAsJsonObject();
					if (j.has("mesaj")) m = j.get("mesaj").getAsString();
				} catch (RuntimeException ignored) {
					// gövde yok
				}
				sonuc = m;
				lobidenCek();
			}
			if (cevap != null) anaIs(() -> cevap.accept(sonuc));
		});
	}

	/** Kapı: lobideki asıl listeyi çeker, yereli onunla değiştirir (lobiye henüz ulaşmamış yeni kayıtlar korunur). */
	private void lobidenCek() {
		JsonObject body = new JsonObject();
		body.addProperty("secret", plugin.getConfig().getString("network.secret", ""));
		HttpRequest req = HttpRequest.newBuilder(URI.create(lobiAdresi("/api/internal/yasaklar")))
			.timeout(Duration.ofSeconds(6))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(JsonFile.GSON.toJson(body), StandardCharsets.UTF_8))
			.build();
		http.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).whenComplete((r, t) -> {
			if (t != null || r.statusCode() != 200) return;
			try {
				JsonObject j = JsonParser.parseString(r.body()).getAsJsonObject();
				List<Kayit> lobi = JsonFile.GSON.fromJson(j.get("yasaklar"), new TypeToken<List<Kayit>>() { }.getType());
				if (lobi == null) return;
				synchronized (this) {
					Set<String> lobiIdler = new HashSet<>();
					for (Kayit k : lobi) {
						duzelt(k);
						lobiIdler.add(k.id);
					}
					List<Kayit> yeni = new ArrayList<>(lobi);
					long sinir = System.currentTimeMillis() - 120_000L;
					for (Kayit k : kayitlar) {
						if (!lobiIdler.contains(k.id) && k.yerelZaman > sinir) yeni.add(k);
					}
					String eski = JsonFile.GSON.toJson(kayitlar);
					String simdiki = JsonFile.GSON.toJson(yeni);
					if (eski.equals(simdiki)) return;
					kayitlar.clear();
					kayitlar.addAll(yeni);
					kaydet();
				}
			} catch (RuntimeException e) {
				plugin.getLogger().warning("Lobideki yasak listesi okunamadı: " + e.getMessage());
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
		Kanit kanit = null;
		if (b.has("kanit") && b.get("kanit").isJsonObject()) {
			try {
				kanit = JsonFile.GSON.fromJson(b.get("kanit"), Kanit.class);
			} catch (RuntimeException ignored) {
				// geçersiz kanıt
			}
		}
		yasakla(ad, str(b, "ip"), izler, str(b, "damga"), str(b, "sebep"), str(b, "yetkili"), str(b, "vaka"), kanit, str(b, "id"), false);
	}

	JsonObject lobiListesi() {
		JsonObject o = new JsonObject();
		o.addProperty("ok", true);
		o.add("yasaklar", JsonFile.GSON.toJsonTree(new ArrayList<>(kayitlar)));
		return o;
	}

	/** Kapıdan gelen onay / red / kaldır işlemi; dönüş kullanıcıya gösterilecek mesaj. */
	String lobiIslem(JsonObject b) {
		String islem = str(b, "islem");
		String hedef = str(b, "hedef");
		String ad = str(b, "ad");
		if (hedef == null || ad == null) return "Eksik bilgi.";
		String rol = str(b, "rol");
		if (rol != null && !ROL_KURUCU.equals(rol) && !ROL_UST.equals(rol)) rol = null;
		return switch (islem == null ? "" : islem) {
			case "onay" -> rol == null ? "Yetki yok." : onaylaYerel(hedef, ad, rol);
			case "red" -> rol == null ? "Yetki yok." : talepReddetYerel(hedef, str(b, "talep"), ad, str(b, "neden"));
			case "kaldir" -> {
				if (!"CONSOLE".equals(ad)) yield "Yalnızca konsol doğrudan kaldırabilir.";
				Kayit k = bulAdVeyaIp(hedef);
				yield k == null ? "Böyle bir yasak yok." : kaldirDogrudan(k, "konsoldan kaldırıldı (kapı)") != null ? k.ana + " yasağı kaldırıldı." : "Kaldırılamadı.";
			}
			default -> "Bilinmeyen işlem.";
		};
	}

	private static String str(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
	}
}
