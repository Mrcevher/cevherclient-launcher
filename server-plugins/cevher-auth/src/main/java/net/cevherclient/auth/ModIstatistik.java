package net.cevherclient.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Onaylı hilecilerden Modrinth proje yasağı önerisi (tasarım: docs/plans/2026-11-24-modrinth-proje-onerisi-design.md).
 *
 * <p>Lobi (ana kayıt): her oyuncunun kabul edilen girişteki Modrinth projelerini saklar (normal oyuncu taban çizgisi),
 * kalıcı yasakta yasaklananın projelerini kayda ekler, eşikleri geçen projeler için yetkiliye öneri üretir ve onaylanan
 * projeleri {@code yasakli-projeler.json}'da tutar. Launcher'a gömülü modlar (izinli hash'ler, jar-klasorleri,
 * modpack-dosyalari) hiç kaydedilmez ve aday olmaz.
 *
 * <p>Kapı: lobiden çekilen öneri / yasak listesinin aynasını tutar, yeni öneriyi yetkililere duyurur; kararlar lobiye
 * iletilir. Hiçbir proje yetkili onayı olmadan yasaklanmaz.
 */
final class ModIstatistik {
	static final Pattern PROJE = Pattern.compile("[A-Za-z0-9]{8}");
	private static final int MAX_PROJE = 200;
	private static final int MAX_OYUNCU = 20_000;
	private static final int MAX_ORNEK_KAYIT = 10;

	static final class Liste {
		long zaman;
		Map<String, String> projeler = new LinkedHashMap<>();
	}

	static final class Oneri {
		String proje;
		String modId = "";
		int hileci;
		int normal;
		int taban;
		List<String> kayitlar = new ArrayList<>();
		String durum = "BEKLIYOR";
		long zaman;
		int yoksayHileci;
		String yapan = "";
	}

	static final class Yasak {
		String proje;
		String modId = "";
		String yapan = "";
		long zaman;
		int hileci;
		int normal;
		int taban;
	}

	private final CevherAuthPlugin plugin;
	private final Path listeDosya;
	private final Path oneriDosya;
	private final Path yasakDosya;
	private final Map<String, Liste> listeler = new ConcurrentHashMap<>();
	private final Map<String, Oneri> oneriler = new ConcurrentHashMap<>();
	private final Map<String, Yasak> yasaklilar = new ConcurrentHashMap<>();
	/** Kapı: duyurulmuş öneri anahtarları (proje@hileci), aynı öneri her çekişte tekrar duyurulmasın. */
	private final Set<String> duyurulan = ConcurrentHashMap.newKeySet();
	private volatile boolean listeKirli;

	ModIstatistik(CevherAuthPlugin plugin) {
		this.plugin = plugin;
		Path d = plugin.getDataFolder().toPath();
		this.listeDosya = d.resolve("mod-listeleri.json");
		this.oneriDosya = d.resolve("proje-onerileri.json");
		this.yasakDosya = d.resolve("yasakli-projeler.json");
		if (!plugin.isGate()) {
			try {
				listeler.putAll(JsonFile.read(listeDosya, new TypeToken<Map<String, Liste>>() { }.getType(), Map.<String, Liste>of()));
				oneriler.putAll(JsonFile.read(oneriDosya, new TypeToken<Map<String, Oneri>>() { }.getType(), Map.<String, Oneri>of()));
				yasaklilar.putAll(JsonFile.read(yasakDosya, new TypeToken<Map<String, Yasak>>() { }.getType(), Map.<String, Yasak>of()));
			} catch (IOException | RuntimeException e) {
				plugin.getLogger().severe("Mod istatistik dosyaları okunamadı: " + e.getMessage());
			}
			listeler.values().removeIf(l -> l == null || l.projeler == null);
			plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::listeKaydet, 20L * 60, 20L * 60);
			plugin.getLogger().info("Proje önerisi: " + listeler.size() + " oyuncu mod listesi, " + yasaklilar.size() + " yasaklı proje, "
				+ oneriler.values().stream().filter(o -> "BEKLIYOR".equals(o.durum)).count() + " bekleyen öneri");
		}
	}

	// ── ayarlar ────────────────────────────────────────────────────────────────

	private ConfigurationSection ayar() {
		ConfigurationSection c = plugin.getConfig().getConfigurationSection("mod-dogrulama.proje-onerisi");
		return c != null ? c : plugin.getConfig().createSection("mod-dogrulama.proje-onerisi");
	}

	private boolean acik() {
		return ayar().getBoolean("acik", true);
	}

	/** Oyuncudan gelen mod kimliği: yalnızca [a-z0-9_.-], en fazla 64 karakter. */
	static String modTemiz(String s) {
		if (s == null) return "";
		StringBuilder sb = new StringBuilder();
		for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
			if (sb.length() >= 64) break;
			if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '-') sb.append(c);
		}
		return sb.toString();
	}

	// ── lobi: taban çizgisi ────────────────────────────────────────────────────

	/** Kabul edilen girişte çağrılır (asenkron login iş parçacığı). {@code projeler}: projeId → modId, gömülü modlar hariç. */
	void girisKaydet(String ad, Map<String, String> projeler) {
		if (plugin.isGate() || ad == null || ad.isEmpty()) return;
		Liste l = new Liste();
		l.zaman = System.currentTimeMillis();
		for (Map.Entry<String, String> e : projeler.entrySet()) {
			if (l.projeler.size() >= MAX_PROJE) break;
			if (PROJE.matcher(e.getKey()).matches()) l.projeler.put(e.getKey(), modTemiz(e.getValue()));
		}
		listeler.put(ad.toLowerCase(Locale.ROOT), l);
		if (listeler.size() > MAX_OYUNCU) {
			// En eski görülenleri düşür
			List<Map.Entry<String, Liste>> s = new ArrayList<>(listeler.entrySet());
			s.sort((a, b) -> Long.compare(a.getValue().zaman, b.getValue().zaman));
			for (int i = 0; i < s.size() - MAX_OYUNCU; i++) listeler.remove(s.get(i).getKey());
		}
		listeKirli = true;
	}

	private void listeKaydet() {
		if (!listeKirli) return;
		listeKirli = false;
		try {
			JsonFile.write(listeDosya, new LinkedHashMap<>(listeler));
		} catch (IOException e) {
			listeKirli = true;
			plugin.getLogger().warning("mod-listeleri.json yazılamadı: " + e.getMessage());
		}
	}

	void kapat() {
		if (!plugin.isGate()) {
			listeKirli = true;
			listeKaydet();
		}
	}

	/** Lobi uygulaması: bu proje yetkili onayıyla yasaklandı mı? */
	boolean yasakli(String proje) {
		return proje != null && yasaklilar.containsKey(proje.toLowerCase(Locale.ROOT));
	}

	// ── lobi: yasak kaydı ve öneri ─────────────────────────────────────────────

	/**
	 * Kalıcı yasakta (KaliciYasak.yasakla, lobide) çağrılır: kaydın tüm hesaplarının son projelerini kayda ekler, sonra
	 * önerileri yeniden hesaplar. Çağıran KaliciYasak kilidini tutar.
	 */
	void yasakEklendi(KaliciYasak.Kayit k, List<KaliciYasak.Kayit> tumu) {
		if (plugin.isGate() || !acik()) return;
		if (k.modProjeleri == null) k.modProjeleri = new LinkedHashMap<>();
		for (String ad : k.adlar) {
			Liste l = listeler.get(ad);
			if (l == null) continue;
			for (Map.Entry<String, String> e : l.projeler.entrySet()) {
				if (k.modProjeleri.size() >= MAX_PROJE) break;
				k.modProjeleri.putIfAbsent(e.getKey(), e.getValue());
			}
		}
		if (!k.modProjeleri.isEmpty()) oneriHesapla(k, tumu);
	}

	private synchronized void oneriHesapla(KaliciYasak.Kayit k, List<KaliciYasak.Kayit> tumu) {
		ConfigurationSection c = ayar();
		int minHileci = Math.max(1, c.getInt("min-hileci", 2));
		int minTaban = Math.max(1, c.getInt("min-taban", 20));
		double maxOran = c.getDouble("max-oran", 0.05);
		long sinir = System.currentTimeMillis() - Math.max(1, c.getLong("taban-gun", 90)) * 86_400_000L;

		Set<String> yasakliAdlar = new HashSet<>();
		for (KaliciYasak.Kayit x : tumu) yasakliAdlar.addAll(x.adlar);
		List<Liste> taban = new ArrayList<>();
		for (Map.Entry<String, Liste> e : listeler.entrySet()) {
			if (!yasakliAdlar.contains(e.getKey()) && e.getValue().zaman >= sinir) taban.add(e.getValue());
		}
		List<Oneri> yeni = new ArrayList<>();
		for (Map.Entry<String, String> pe : k.modProjeleri.entrySet()) {
			String proje = pe.getKey();
			if (yasaklilar.containsKey(proje.toLowerCase(Locale.ROOT))) continue;
			List<String> ornek = new ArrayList<>();
			int hileci = 0;
			for (KaliciYasak.Kayit x : tumu) {
				if (x.modProjeleri != null && x.modProjeleri.containsKey(proje)) {
					hileci++;
					if (ornek.size() < MAX_ORNEK_KAYIT) ornek.add(x.id);
				}
			}
			int normal = 0;
			for (Liste l : taban) if (l.projeler.containsKey(proje)) normal++;
			boolean aday = hileci >= minHileci && taban.size() >= minTaban && normal <= maxOran * taban.size();
			Oneri o = oneriler.get(proje);
			if (o == null && !aday) continue;
			if (o == null) {
				o = new Oneri();
				o.proje = proje;
				o.zaman = System.currentTimeMillis();
				oneriler.put(proje, o);
			}
			o.modId = modTemiz(pe.getValue());
			o.hileci = hileci;
			o.normal = normal;
			o.taban = taban.size();
			o.kayitlar = ornek;
			if ("YOKSAYILDI".equals(o.durum)) {
				// Yoksayılan proje, yasaklı hileci sayısı yoksayma anındakinin 2 katına çıkınca yeniden önerilir
				if (aday && hileci >= 2 * Math.max(1, o.yoksayHileci)) {
					o.durum = "BEKLIYOR";
					o.zaman = System.currentTimeMillis();
					yeni.add(o);
				}
			} else if ("BEKLIYOR".equals(o.durum)) {
				if (aday) yeni.add(o);
				else oneriler.remove(proje); // eşik artık tutmuyor (ör. taban büyüdü)
			}
		}
		oneriKaydet();
		if (!yeni.isEmpty()) {
			Bukkit.getScheduler().runTask(plugin, () -> {
				for (Oneri o : yeni) duyur(o);
			});
		}
	}

	private void oneriKaydet() {
		try {
			JsonFile.write(oneriDosya, new LinkedHashMap<>(oneriler));
		} catch (IOException e) {
			plugin.getLogger().warning("proje-onerileri.json yazılamadı: " + e.getMessage());
		}
	}

	private void yasakKaydet() {
		try {
			JsonFile.write(yasakDosya, new LinkedHashMap<>(yasaklilar));
		} catch (IOException e) {
			plugin.getLogger().warning("yasakli-projeler.json yazılamadı: " + e.getMessage());
		}
	}

	/** Kapıdan ya da yerel komuttan gelen karar. {@code islem}: yasakla | yoksay | izin. Dönüş: kullanıcıya mesaj. */
	synchronized String kararYerel(String islem, String proje, String yapan, String rol) {
		if (rol == null) return "Yetkin yok (üst yetkili veya kurucu olmalısın).";
		if (proje == null || !PROJE.matcher(proje).matches()) return "Geçersiz Modrinth proje kimliği (8 harf/rakam).";
		String anahtar = proje.toLowerCase(Locale.ROOT);
		Oneri o = oneriler.get(proje);
		switch (islem) {
			case "yasakla" -> {
				if (yasaklilar.containsKey(anahtar)) return proje + " zaten yasaklı.";
				Yasak y = new Yasak();
				y.proje = proje;
				y.yapan = yapan + " (" + rol + ")";
				y.zaman = System.currentTimeMillis();
				if (o != null) {
					y.modId = o.modId;
					y.hileci = o.hileci;
					y.normal = o.normal;
					y.taban = o.taban;
					o.durum = "YASAKLANDI";
					o.yapan = y.yapan;
				}
				yasaklilar.put(anahtar, y);
				yasakKaydet();
				oneriKaydet();
				plugin.getLogger().warning("[PROJE] " + proje + (y.modId.isEmpty() ? "" : " (" + y.modId + ")") + " yasaklandı — " + y.yapan);
				return proje + (y.modId.isEmpty() ? "" : " (" + y.modId + ")") + " yasaklandı; bu modla gelen oyuncu bir sonraki girişte reddedilir."
					+ (o != null && o.normal > 0 ? " Etkilenecek yasaksız oyuncu: " + o.normal + "." : "");
			}
			case "yoksay" -> {
				if (o == null || !"BEKLIYOR".equals(o.durum)) return "Bu proje için bekleyen öneri yok: " + proje;
				o.durum = "YOKSAYILDI";
				o.yoksayHileci = o.hileci;
				o.yapan = yapan + " (" + rol + ")";
				oneriKaydet();
				plugin.getLogger().info("[PROJE] " + proje + " önerisi yoksayıldı — " + o.yapan);
				return proje + " önerisi yoksayıldı (yasaklı hileci sayısı " + (2 * Math.max(1, o.hileci)) + "'e çıkarsa yeniden önerilir).";
			}
			case "izin" -> {
				Yasak y = yasaklilar.remove(anahtar);
				if (y == null) return proje + " yasaklı değil.";
				if (o != null && "YASAKLANDI".equals(o.durum)) {
					o.durum = "YOKSAYILDI";
					o.yoksayHileci = o.hileci;
				}
				yasakKaydet();
				oneriKaydet();
				plugin.getLogger().warning("[PROJE] " + proje + " yasağı kaldırıldı — " + yapan + " (" + rol + ")");
				return proje + " yasağı kaldırıldı.";
			}
			default -> {
				return "Bilinmeyen işlem.";
			}
		}
	}

	// ── lobi → kapı eşitlemesi ─────────────────────────────────────────────────

	/** Lobinin /api/internal/yasaklar yanıtına eklenir. */
	void lobiJson(JsonObject o) {
		JsonArray b = new JsonArray();
		for (Oneri x : oneriler.values()) if ("BEKLIYOR".equals(x.durum)) b.add(JsonFile.GSON.toJsonTree(x));
		o.add("projeOnerileri", b);
		o.add("yasakliProjeler", JsonFile.GSON.toJsonTree(new ArrayList<>(yasaklilar.values())));
	}

	/** Kapı: lobiden çekilen öneri / yasak aynası; yeni öneriler yetkililere duyurulur. */
	void kapiGuncelle(JsonObject j) {
		if (!plugin.isGate()) return;
		if (j.has("projeOnerileri") && j.get("projeOnerileri").isJsonArray()) {
			Map<String, Oneri> yeni = new LinkedHashMap<>();
			for (JsonElement e : j.getAsJsonArray("projeOnerileri")) {
				try {
					Oneri o = JsonFile.GSON.fromJson(e, Oneri.class);
					if (o == null || o.proje == null || !PROJE.matcher(o.proje).matches()) continue;
					o.modId = modTemiz(o.modId);
					yeni.put(o.proje, o);
				} catch (RuntimeException ignored) {
					// bozuk girdi
				}
			}
			oneriler.clear();
			oneriler.putAll(yeni);
			List<Oneri> duyur = new ArrayList<>();
			for (Oneri o : yeni.values()) if (duyurulan.add(o.proje + "@" + o.hileci)) duyur.add(o);
			if (!duyur.isEmpty()) Bukkit.getScheduler().runTask(plugin, () -> duyur.forEach(this::duyur));
		}
		if (j.has("yasakliProjeler") && j.get("yasakliProjeler").isJsonArray()) {
			Map<String, Yasak> yeni = new LinkedHashMap<>();
			for (JsonElement e : j.getAsJsonArray("yasakliProjeler")) {
				try {
					Yasak y = JsonFile.GSON.fromJson(e, Yasak.class);
					if (y != null && y.proje != null && PROJE.matcher(y.proje).matches()) {
						y.modId = modTemiz(y.modId);
						yeni.put(y.proje.toLowerCase(Locale.ROOT), y);
					}
				} catch (RuntimeException ignored) {
					// bozuk girdi
				}
			}
			yasaklilar.clear();
			yasaklilar.putAll(yeni);
		}
	}

	// ── bildirim ve listeleme ──────────────────────────────────────────────────

	static boolean yetkili(CommandSender s) {
		return KaliciYasak.rol(s) != null;
	}

	private static String oran(Oneri o) {
		return o.taban == 0 ? "?" : String.format(Locale.ROOT, "%%%.1f", 100.0 * o.normal / o.taban);
	}

	private Component oneriSatiri(Oneri o) {
		String ad = o.modId.isEmpty() ? o.proje : o.modId;
		return Component.text("[Yasak] Öneri: ", NamedTextColor.GOLD)
			.append(Component.text("\"" + ad + "\" ", NamedTextColor.WHITE))
			.append(Component.text("(Modrinth " + o.proje + ") " + o.hileci + " yasaklı hilecide var, yasaksız oyuncuların " + oran(o)
				+ "'inde (" + o.normal + "/" + o.taban + ") ", NamedTextColor.GRAY))
			.append(Component.text("[Yasakla]", NamedTextColor.RED, TextDecoration.BOLD)
				.clickEvent(ClickEvent.runCommand("/cevherauth projeyasakla " + o.proje))
				.hoverEvent(HoverEvent.showText(Component.text("Bu modla gelen oyuncu bir sonraki girişte reddedilir. Etkilenecek yasaksız oyuncu: " + o.normal))))
			.append(Component.text(" "))
			.append(Component.text("[Yoksay]", NamedTextColor.GRAY)
				.clickEvent(ClickEvent.runCommand("/cevherauth projeyoksay " + o.proje)));
	}

	private void duyur(Oneri o) {
		Component m = oneriSatiri(o);
		plugin.getLogger().warning("[PROJE] Öneri: " + o.proje + " (" + o.modId + ") " + o.hileci + " yasaklı hilecide, yasaksız " + o.normal + "/" + o.taban
			+ " — /cevherauth projeyasakla " + o.proje);
		for (Player p : Bukkit.getOnlinePlayers()) {
			if (yetkili(p)) p.sendMessage(m);
		}
	}

	/** /cevherauth projeler */
	void listele(CommandSender s) {
		List<Oneri> bekleyen = oneriler.values().stream().filter(o -> "BEKLIYOR".equals(o.durum)).toList();
		s.sendMessage(Component.text("[Yasak] Bekleyen proje önerisi: " + bekleyen.size(), NamedTextColor.GOLD));
		for (Oneri o : bekleyen) s.sendMessage(oneriSatiri(o));
		s.sendMessage(Component.text("[Yasak] Yasaklı Modrinth projeleri: " + yasaklilar.size(), NamedTextColor.GOLD));
		for (Yasak y : yasaklilar.values()) {
			s.sendMessage(Component.text(" " + y.proje + (y.modId.isEmpty() ? "" : " (" + y.modId + ")") + " — " + y.yapan + " ", NamedTextColor.GRAY)
				.append(Component.text("[Kaldır]", NamedTextColor.GREEN).clickEvent(ClickEvent.suggestCommand("/cevherauth projeizin " + y.proje))));
		}
	}
}
