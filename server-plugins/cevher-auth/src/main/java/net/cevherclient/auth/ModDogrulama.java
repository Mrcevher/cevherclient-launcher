package net.cevherclient.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Launcher'la gelen oyunun yüklü modlarını <b>hash</b> ile denetler. cevher-session (1.1+) her
 * modun jar SHA-1'ini bilet kanıtına ekler ve bilet sırrıyla imzalar.
 *
 * Karar dosya adına ya da mod kimliğine göre verilmez: hile modlarının adını, kimliğini veya
 * paketlerini değiştiren araçlar jar'ın içeriğini, dolayısıyla hash'ini de değiştirir. Bir jar
 * yalnızca hash'i şunlardan birindeyse geçer:
 * <ul>
 *   <li>izinli-hashler, jar-klasorleri (launcher'ın kendi modları), modpack-dosyalari (.mrpack),</li>
 *   <li>Modrinth'te yayımlanmış bir sürümün değiştirilmemiş dosyası (yasakli-projeler hariç).</li>
 * </ul>
 * Oyuna eklenmiş Java agent'ı ({@code !agent}) ve jar dışından yüklenen modlar da reddedilebilir.
 *
 * Sınır: liste istemciden gelir; oturum modunu bilerek değiştiren biri yalan söyleyebilir.
 */
final class ModDogrulama {
	private static final Pattern SHA1 = Pattern.compile("[0-9a-f]{40}");
	private static final String MODRINTH = "https://api.modrinth.com/v2/version_files";

	private final CevherAuthPlugin plugin;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
	/** sha1 → Modrinth proje kimliği; yalnızca bulunanlar (dosyada kalıcı). */
	private final Map<String, String> modrinthCache = new ConcurrentHashMap<>();
	private final Path cacheFile;
	/** Ad (küçük harf) → son görülen liste; aynı liste tekrar tekrar günlüğe yazılmasın. */
	private final Map<String, String> sonListe = new ConcurrentHashMap<>();
	private volatile Ayar ayar;

	enum Bilinmeyen {
		REDDET, KAYDET
	}

	private record Ayar(boolean acik, boolean listeZorunlu, boolean kaydet, boolean hashKontrol, Bilinmeyen bilinmeyen,
		boolean modrinth, boolean modrinthYoksaGecir, boolean agentReddet, boolean attachZorunlu, Set<String> izinliHash, Set<String> yasakliHash,
		Set<String> yasakliProje, Set<String> yasakliId, Set<String> hileKelimeleri) {
	}

	ModDogrulama(CevherAuthPlugin plugin) {
		this.plugin = plugin;
		this.cacheFile = plugin.getDataFolder().toPath().resolve("modrinth-hash-cache.json");
		try {
			if (Files.isRegularFile(cacheFile)) {
				Map<String, String> m = JsonFile.GSON.fromJson(Files.readString(cacheFile, StandardCharsets.UTF_8),
					new TypeToken<Map<String, String>>() { }.getType());
				if (m != null) {
					modrinthCache.putAll(m);
				}
			}
		} catch (IOException | RuntimeException e) {
			plugin.getLogger().warning("modrinth-hash-cache.json okunamadı: " + e.getMessage());
		}
		yenile();
	}

	void yenile() {
		ConfigurationSection c = plugin.getConfig().getConfigurationSection("mod-dogrulama");
		if (c == null) {
			c = plugin.getConfig().createSection("mod-dogrulama");
		}
		Set<String> izinli = new HashSet<>(kucuk(c.getStringList("izinli-hashler")));
		Path serverRoot = plugin.getDataFolder().toPath().toAbsolutePath().getParent().getParent();
		// Kapı rolünde mod denetimi yapılmaz (lobide yapılır); klasörler taranmaz.
		for (String klasor : plugin.isGate() ? List.<String>of() : c.getStringList("jar-klasorleri")) {
			izinli.addAll(klasorHashleri(serverRoot.resolve(klasor).normalize()));
		}
		for (String mrpack : plugin.isGate() ? List.<String>of() : c.getStringList("modpack-dosyalari")) {
			izinli.addAll(mrpackHashleri(serverRoot.resolve(mrpack).normalize()));
		}
		ayar = new Ayar(
			c.getBoolean("enabled", true),
			c.getBoolean("liste-zorunlu", false),
			c.getBoolean("kaydet", true),
			c.getBoolean("hash-kontrol", true),
			"KAYDET".equalsIgnoreCase(c.getString("bilinmeyen", "REDDET")) ? Bilinmeyen.KAYDET : Bilinmeyen.REDDET,
			c.getBoolean("modrinth-kontrol", true),
			"GECIR".equalsIgnoreCase(c.getString("modrinth-erisilemezse", "REDDET")),
			c.getBoolean("agent-reddet", true),
			c.getBoolean("attach-engeli-zorunlu", false),
			Set.copyOf(izinli),
			kucuk(c.getStringList("yasakli-hashler")),
			kucuk(c.getStringList("yasakli-projeler")),
			kucuk(c.getStringList("yasakli")),
			kucuk(c.getStringList("hile-kelimeleri")));
		if (ayar.acik() && ayar.hashKontrol()) {
			plugin.getLogger().info("Mod hash denetimi açık: " + izinli.size() + " izinli hash, Modrinth "
				+ (ayar.modrinth() ? "açık" : "kapalı") + ", bilinmeyen jar: " + ayar.bilinmeyen());
		}
	}

	private static Set<String> kucuk(List<String> l) {
		Set<String> s = new HashSet<>();
		for (String x : l) {
			if (x != null && !x.isBlank()) {
				s.add(x.trim().toLowerCase(Locale.ROOT));
			}
		}
		return Set.copyOf(s);
	}

	private Set<String> klasorHashleri(Path dir) {
		Set<String> out = new HashSet<>();
		if (!Files.isDirectory(dir)) {
			plugin.getLogger().warning("mod-dogrulama.jar-klasorleri bulunamadı: " + dir);
			return out;
		}
		try (Stream<Path> s = Files.list(dir)) {
			for (Path p : s.filter(x -> x.toString().toLowerCase(Locale.ROOT).endsWith(".jar")).toList()) {
				try (InputStream in = Files.newInputStream(p)) {
					out.add(sha1(in));
				}
			}
		} catch (IOException e) {
			plugin.getLogger().warning("Jar klasörü okunamadı: " + dir + " (" + e.getMessage() + ")");
		}
		return out;
	}

	/** .mrpack: modrinth.index.json'daki dosya hash'leri + overrides altındaki jar'ların hash'i. */
	private Set<String> mrpackHashleri(Path file) {
		Set<String> out = new HashSet<>();
		try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file))) {
			for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
				String name = e.getName();
				if (name.equals("modrinth.index.json")) {
					JsonObject idx = JsonParser.parseString(new String(zip.readNBytes(4 * 1024 * 1024), StandardCharsets.UTF_8)).getAsJsonObject();
					for (JsonElement f : idx.getAsJsonArray("files")) {
						JsonObject h = f.getAsJsonObject().getAsJsonObject("hashes");
						if (h != null && h.has("sha1")) {
							out.add(h.get("sha1").getAsString().toLowerCase(Locale.ROOT));
						}
					}
				} else if (name.endsWith(".jar") && (name.startsWith("overrides/") || name.startsWith("client-overrides/"))) {
					out.add(sha1(zip));
				}
			}
		} catch (IOException | RuntimeException e) {
			plugin.getLogger().warning("Modpack okunamadı: " + file + " (" + e.getMessage() + ")");
		}
		return out;
	}

	private static String sha1(InputStream in) throws IOException {
		try {
			MessageDigest d = MessageDigest.getInstance("SHA-1");
			byte[] buf = new byte[65536];
			for (int n; (n = in.read(buf)) > 0; ) {
				d.update(buf, 0, n);
			}
			return HexFormat.of().formatHex(d.digest());
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Launcher'ın oyun açılmadan önce uyguladığı politika (GET /api/mod-policy). */
	JsonObject politika() {
		Ayar a = ayar;
		JsonObject o = new JsonObject();
		o.addProperty("ok", true);
		JsonArray izin = new JsonArray();
		a.izinliHash().forEach(izin::add);
		JsonArray yasakHash = new JsonArray();
		a.yasakliHash().forEach(yasakHash::add);
		JsonArray yasakProje = new JsonArray();
		a.yasakliProje().forEach(yasakProje::add);
		o.add("allowedSha1", izin);
		o.add("blockedSha1", yasakHash);
		o.add("blockedProjects", yasakProje);
		return o;
	}

	private record Girdi(String id, String hash) {
	}

	/**
	 * Asenkron login iş parçacığından çağrılır (Modrinth sorgusu bekleyebilir).
	 *
	 * @param modlar oturum modunun bildirdiği liste; eski launcher'da (kanıt v1) null
	 * @return reddetme nedeni (oyuncuya gösterilir) ya da izinliyse null
	 */
	String kontrol(String ad, String ip, String modlar) {
		Ayar a = ayar;
		if (!a.acik()) {
			return null;
		}
		if (modlar == null) {
			if (a.listeZorunlu()) {
				plugin.getLogger().info(ad + " mod listesi göndermedi (eski launcher), reddedildi");
				return "launcher eski";
			}
			return null;
		}
		if (a.kaydet() && !modlar.equals(sonListe.put(ad.toLowerCase(Locale.ROOT), modlar))) {
			plugin.getLogger().info(ad + " (" + ip + ") modları: " + (modlar.isEmpty() ? "(yok)" : modlar));
		}
		if (sonListe.size() > 5000) {
			sonListe.clear();
		}

		List<Girdi> girdiler = new ArrayList<>();
		Set<String> bayraklar = new HashSet<>();
		for (String raw : modlar.split(",")) {
			String e = raw.trim().toLowerCase(Locale.ROOT);
			if (e.isEmpty()) {
				continue;
			}
			if (e.equals("!agent")) {
				if (a.agentReddet()) {
					return red(ad, ip, "java agent", "oyuna Java agent eklenmiş");
				}
				continue;
			}
			if (e.startsWith("!sinif:")) {
				// Oturum modu (1.2+) bilinen bir hile istemcisinin ana sınıfını sınıf yolunda buldu.
				return kritik(ad, ip, e.substring(7), "bilinen hile istemcisinin sınıfı yüklü");
			}
			if (e.startsWith("!")) {
				return red(ad, ip, "istemci", "oturum modu şüpheli durum bildirdi (" + e + ")");
			}
			if (e.startsWith("+")) {
				// Oturum modu 1.2+ yetenek bayrakları: +sorgu (oyun içi sorgu), +noattach (agent engeli açık).
				bayraklar.add(e);
				continue;
			}
			if (e.startsWith("…+")) {
				if (a.hashKontrol() && a.bilinmeyen() == Bilinmeyen.REDDET) {
					return red(ad, ip, "çok fazla mod", "liste sığmadı (" + e + ")");
				}
				continue;
			}
			int at = e.lastIndexOf('@');
			String id = at < 0 ? e : e.substring(0, at);
			String hash = at < 0 ? "" : e.substring(at + 1);
			if (!id.isEmpty() && a.yasakliId().contains(id)) {
				return kritik(ad, ip, id, "yasaklı mod kimliği");
			}
			girdiler.add(new Girdi(id, hash));
		}
		if (a.attachZorunlu() && !bayraklar.contains("+noattach")) {
			return red(ad, ip, "launcher eski", "agent engeli (-XX:+DisableAttachMechanism) yok; launcher güncellenmeli");
		}
		if (!a.hashKontrol()) {
			return null;
		}

		List<String> bilinmeyen = new ArrayList<>();
		for (Girdi g : girdiler) {
			if (a.yasakliHash().contains(g.hash())) {
				return red(ad, ip, ad(g), "yasaklı hash " + g.hash());
			}
			if (!SHA1.matcher(g.hash()).matches()) {
				if (a.bilinmeyen() == Bilinmeyen.REDDET) {
					return red(ad, ip, ad(g), "jar dışından yüklenmiş ya da hash'i bildirilmemiş mod");
				}
				continue;
			}
			if (!a.izinliHash().contains(g.hash()) && !modrinthCache.containsKey(g.hash())) {
				bilinmeyen.add(g.hash());
			}
		}
		boolean modrinthErisildi = true;
		if (!bilinmeyen.isEmpty() && a.modrinth()) {
			modrinthErisildi = modrinthSor(bilinmeyen);
		}
		for (Girdi g : girdiler) {
			if (!SHA1.matcher(g.hash()).matches() || a.izinliHash().contains(g.hash())) {
				continue;
			}
			String proje = modrinthCache.get(g.hash());
			if (proje != null) {
				if (a.yasakliProje().contains(proje.toLowerCase(Locale.ROOT))) {
					return red(ad, ip, ad(g), "yasaklı Modrinth projesi " + proje);
				}
				continue;
			}
			List<String> kelimeler = kelimeler(a, g.id());
			if (a.modrinth() && !modrinthErisildi) {
				// Modrinth'e (sunucunun kendi bağlantısıyla) ulaşılamadı: jar Modrinth'te olabilir (ör. Better
				// Combat). Hile kelimeli olan yine de alınmaz ama IP engellenmez; kanıt kesin değil.
				if (!kelimeler.isEmpty()) {
					return red(ad, ip, ad(g), "doğrulanamadı (Modrinth'e ulaşılamadı) ve hile kelimesi " + kelimeler);
				}
				if (a.modrinthYoksaGecir()) {
					plugin.getLogger().info(ad + ": " + ad(g) + " doğrulanamadı (Modrinth'e ulaşılamadı), geçirildi");
					continue;
				}
			}
			if (!kelimeler.isEmpty()) {
				// Tanınmayan jar + hile kelimesi: bilinmeyen KAYDET olsa da reddedilir (KRİTİK).
				return kritik(ad, ip, ad(g), "tanınmayan jar, hile kelimesi " + kelimeler + " " + g.hash());
			}
			if (a.bilinmeyen() == Bilinmeyen.REDDET) {
				return red(ad, ip, ad(g), "hash hiçbir listede yok (değiştirilmiş / yeniden adlandırılmış / tanınmayan jar) " + g.hash());
			}
			plugin.getLogger().warning(ad + " (" + ip + ") tanınmayan jar: " + ad(g) + " " + g.hash() + " (bilinmeyen: KAYDET, geçirildi)");
		}
		return null;
	}

	private static String ad(Girdi g) {
		return g.id().isEmpty() ? g.hash().substring(0, Math.min(12, g.hash().length())) : g.id();
	}

	/** KRİTİK: reddeder ve (guvenlik.hile-istemcisi.ip-engelle açıksa) IP'yi engeller. */
	private String kritik(String ad, String ip, String mod, String neden) {
		plugin.riskTakip().ekle(ad, ip, RiskTakip.Seviye.KRITIK, "mod-dogrulama", mod + " — " + neden);
		plugin.getLogger().severe("[KRİTİK] " + ad + " (" + ip + ") hile modu: " + mod + " — " + neden + ", reddedildi");
		plugin.guvenlik().hileEngeli(ip, ad + ": hile modu " + mod);
		return mod;
	}

	/** Yanlış eşleşmesi çok olan kısa kelimeler yalnızca tam kelimeyken sayılır (launcher mod-integrity.ts ile aynı). */
	private static final Set<String> TAM_KELIME = Set.of("lua", "ares", "exec", "jex", "sigma");

	/**
	 * Mod kimliğindeki hile kelimeleri. Kimlik -, _, . ile parçalara bölünür; kelime parçanın içinde
	 * geçerse sayılır ("killaura" → aura, "thunderhack" → hack). TAM_KELIME'dekiler yalnızca parçanın
	 * kendisiyse sayılır ("squares" → ares değil).
	 */
	static List<String> kelimeler(Set<String> hileKelimeleri, String id) {
		List<String> out = new ArrayList<>();
		if (id == null || id.isEmpty()) {
			return out;
		}
		String ayrik = id.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2").toLowerCase(Locale.ROOT);
		for (String w : ayrik.split("[^a-z0-9ğüşıöç]+")) {
			if (w.isEmpty()) {
				continue;
			}
			for (String k : hileKelimeleri) {
				if ((TAM_KELIME.contains(k) ? w.equals(k) : w.contains(k)) && !out.contains(k)) {
					out.add(k);
				}
			}
		}
		return out;
	}

	private static List<String> kelimeler(Ayar a, String id) {
		return kelimeler(a.hileKelimeleri(), id);
	}

	private String red(String ad, String ip, String mod, String neden) {
		plugin.riskTakip().ekle(ad, ip, RiskTakip.Seviye.YUKSEK, "mod-dogrulama", mod + " — " + neden);
		plugin.getLogger().warning(ad + " (" + ip + ") izinsiz modla girmeye çalıştı: " + mod + " — " + neden + ", reddedildi");
		return mod;
	}

	/** Bilinmeyen hash'leri Modrinth'te toplu arar; bulunanları önbelleğe yazar. Ulaşılamazsa false. */
	private boolean modrinthSor(List<String> hashes) {
		JsonObject body = new JsonObject();
		JsonArray arr = new JsonArray();
		hashes.stream().distinct().limit(200).forEach(arr::add);
		body.add("hashes", arr);
		body.addProperty("algorithm", "sha1");
		try {
			HttpRequest req = HttpRequest.newBuilder(URI.create(MODRINTH))
				.timeout(Duration.ofSeconds(4))
				.header("Content-Type", "application/json")
				.header("User-Agent", "CevherAuth/" + plugin.getPluginMeta().getVersion())
				.POST(HttpRequest.BodyPublishers.ofString(JsonFile.GSON.toJson(body), StandardCharsets.UTF_8))
				.build();
			HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (res.statusCode() != 200) {
				plugin.getLogger().warning("Modrinth hash sorgusu: HTTP " + res.statusCode());
				return false;
			}
			JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
			Map<String, String> yeni = new HashMap<>();
			for (Map.Entry<String, JsonElement> e : o.entrySet()) {
				JsonElement p = e.getValue().isJsonObject() ? e.getValue().getAsJsonObject().get("project_id") : null;
				if (p != null && p.isJsonPrimitive()) {
					yeni.put(e.getKey().toLowerCase(Locale.ROOT), p.getAsString());
				}
			}
			if (!yeni.isEmpty()) {
				modrinthCache.putAll(yeni);
				Files.createDirectories(cacheFile.getParent());
				Files.writeString(cacheFile, JsonFile.GSON.toJson(new HashMap<>(modrinthCache)), StandardCharsets.UTF_8);
			}
			return true;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		} catch (IOException | RuntimeException e) {
			plugin.getLogger().warning("Modrinth hash sorgusu yapılamadı: " + e.getMessage());
			return false;
		}
	}
}
