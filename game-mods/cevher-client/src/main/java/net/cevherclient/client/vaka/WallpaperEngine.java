package net.cevherclient.client.vaka;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.cevherclient.client.CevherClientMod;

/**
 * Wallpaper Engine (Steam) bağlantısı: masaüstünde o an seçili duvar kağıdını bulur. Video duvar kağıdında videonun
 * kendisi, sahne / web duvar kağıdında (Minecraft içinde çalıştırılamaz) hareketli önizlemesi kullanılır.
 */
final class WallpaperEngine {
	/** Görünüm ayarında "arka plan = Wallpaper Engine" anlamına gelen değer. */
	static final String SECIM = "wallpaperengine";

	private static final Pattern VDF_YOL = Pattern.compile("\"path\"\\s+\"((?:[^\"\\\\]|\\\\.)*)\"");
	private static List<File> kurulumlar;

	/** Çözülen duvar kağıdı: denenecek dosyalar (sırayla), başlık, tür; bulunamadıysa hata. */
	record Sonuc(List<File> adaylar, String ad, String tur, String hata) {
		static Sonuc hata(String h) {
			return new Sonuc(List.of(), "", "", h);
		}
	}

	private WallpaperEngine() {
	}

	/** Steam kütüphanelerindeki wallpaper_engine klasörleri (bir kez aranır). */
	private static synchronized List<File> kurulumlar() {
		if (kurulumlar != null) {
			return kurulumlar;
		}
		Set<File> kokler = new LinkedHashSet<>();
		String r1 = kayit("HKCU\\Software\\Valve\\Steam", "SteamPath");
		String r2 = kayit("HKLM\\SOFTWARE\\WOW6432Node\\Valve\\Steam", "InstallPath");
		for (String r : new String[] {r1, r2}) {
			if (r != null && !r.isBlank()) {
				kokler.add(new File(r.trim()));
			}
		}
		kokler.add(new File("C:/Program Files (x86)/Steam"));
		kokler.add(new File("C:/Program Files/Steam"));
		for (File surucu : File.listRoots()) {
			for (String alt : new String[] {"SteamLibrary", "Steam", "Games/Steam", "Games/SteamLibrary", "Program Files (x86)/Steam", "Program Files/Steam"}) {
				kokler.add(new File(surucu, alt));
			}
		}
		// libraryfolders.vdf: diğer kütüphaneler
		Set<File> kutuphaneler = new LinkedHashSet<>();
		for (File k : kokler) {
			if (!new File(k, "steamapps").isDirectory()) {
				continue;
			}
			kutuphaneler.add(k);
			File vdf = new File(k, "steamapps/libraryfolders.vdf");
			if (vdf.isFile()) {
				try {
					Matcher m = VDF_YOL.matcher(Files.readString(vdf.toPath(), StandardCharsets.UTF_8));
					while (m.find()) {
						kutuphaneler.add(new File(m.group(1).replace("\\\\", "\\")));
					}
				} catch (Exception e) {
					CevherClientMod.LOGGER.debug("libraryfolders.vdf okunamadı: {}", e.toString());
				}
			}
		}
		List<File> l = new ArrayList<>();
		for (File k : kutuphaneler) {
			File we = new File(k, "steamapps/common/wallpaper_engine");
			if (new File(we, "config.json").isFile() && !l.contains(we)) {
				l.add(we);
			}
		}
		kurulumlar = l;
		CevherClientMod.LOGGER.info("Wallpaper Engine kurulumu: {}", l.isEmpty() ? "bulunamadı" : l);
		return l;
	}

	/** Windows kayıt defterinden bir değer okur ("reg query"); yoksa null. */
	private static String kayit(String anahtar, String deger) {
		if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
			return null;
		}
		try {
			Process p = new ProcessBuilder("reg", "query", anahtar, "/v", deger).redirectErrorStream(true).start();
			String cikti = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			if (!p.waitFor(3, TimeUnit.SECONDS)) {
				p.destroyForcibly();
				return null;
			}
			for (String satir : cikti.split("\\R")) {
				int i = satir.indexOf("REG_SZ");
				if (satir.trim().startsWith(deger) && i >= 0) {
					return satir.substring(i + "REG_SZ".length()).trim();
				}
			}
		} catch (Exception e) {
			// reg yok / izin yok
		}
		return null;
	}

	/** Şu an masaüstünde seçili Wallpaper Engine duvar kağıdı. */
	static Sonuc aktif() {
		List<File> kurulum = kurulumlar();
		if (kurulum.isEmpty()) {
			return Sonuc.hata("Wallpaper Engine bulunamadı (Steam kütüphanelerinde wallpaper_engine yok)");
		}
		String secili = null;
		for (File we : kurulum) {
			try {
				JsonElement kok = JsonParser.parseString(Files.readString(new File(we, "config.json").toPath(), StandardCharsets.UTF_8));
				secili = seciliDosya(kok, false);
				if (secili != null) {
					break;
				}
			} catch (Exception e) {
				CevherClientMod.LOGGER.warn("Wallpaper Engine config.json okunamadı: {}", e.toString());
			}
		}
		if (secili == null) {
			return Sonuc.hata("Wallpaper Engine'de seçili duvar kağıdı bulunamadı");
		}
		File f = new File(secili);
		File klasor = f.isDirectory() ? f : f.getParentFile();
		if (klasor == null || !klasor.isDirectory()) {
			return Sonuc.hata("Duvar kağıdı klasörü yok: " + secili);
		}
		String ad = klasor.getName();
		String tur = "";
		List<File> adaylar = new ArrayList<>();
		File proje = new File(klasor, "project.json");
		String anaDosya = f.isFile() && !f.getName().equalsIgnoreCase("project.json") ? f.getName() : "";
		String onizleme = "";
		if (proje.isFile()) {
			try {
				JsonObject p = JsonParser.parseString(Files.readString(proje.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
				ad = yazi(p, "title", ad);
				tur = yazi(p, "type", "").toLowerCase(Locale.ROOT);
				if (anaDosya.isEmpty()) {
					anaDosya = yazi(p, "file", "");
				}
				onizleme = yazi(p, "preview", "");
			} catch (Exception e) {
				CevherClientMod.LOGGER.warn("Wallpaper Engine project.json okunamadı: {}", e.toString());
			}
		}
		// Video duvar kağıdı → önce videonun kendisi (H.264 MP4/MOV; WebM oynatılamaz).
		if (!anaDosya.isEmpty() && ArkaPlan.video(anaDosya)) {
			ekle(adaylar, new File(klasor, anaDosya));
			if (tur.isEmpty()) {
				tur = "video";
			}
		}
		// Sonra önizleme (çoğunlukla hareketli preview.gif) ve resim duvar kağıtları.
		if (!onizleme.isEmpty()) {
			ekle(adaylar, new File(klasor, onizleme));
		}
		for (String s : new String[] {"preview.gif", "preview.png", "preview.jpg", "preview.jpeg"}) {
			ekle(adaylar, new File(klasor, s));
		}
		if (!anaDosya.isEmpty() && resim(anaDosya)) {
			ekle(adaylar, new File(klasor, anaDosya));
		}
		if (adaylar.isEmpty()) {
			return Sonuc.hata("\"" + ad + "\" için oynatılabilir video ya da önizleme yok");
		}
		return new Sonuc(adaylar, ad, tur, "");
	}

	private static void ekle(List<File> l, File f) {
		if (f.isFile() && !l.contains(f)) {
			l.add(f);
		}
	}

	private static boolean resim(String ad) {
		String s = ad.toLowerCase(Locale.ROOT);
		return s.endsWith(".png") || s.endsWith(".jpg") || s.endsWith(".jpeg") || s.endsWith(".bmp") || s.endsWith(".gif");
	}

	private static String yazi(JsonObject o, String k, String varsayilan) {
		JsonElement e = o.get(k);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : varsayilan;
	}

	/**
	 * config.json içinde "selectedwallpapers" altındaki ilk "file" değerini bulur (kullanıcı adı ve monitör anahtarları
	 * sürüme göre değişir, bu yüzden ağaç taranır).
	 */
	private static String seciliDosya(JsonElement e, boolean secimIcinde) {
		if (e == null) {
			return null;
		}
		if (e.isJsonObject()) {
			for (Map.Entry<String, JsonElement> g : e.getAsJsonObject().entrySet()) {
				String k = g.getKey();
				JsonElement v = g.getValue();
				if (secimIcinde && k.equalsIgnoreCase("file") && v.isJsonPrimitive() && !v.getAsString().isBlank()) {
					return v.getAsString();
				}
				String bulunan = seciliDosya(v, secimIcinde || k.equalsIgnoreCase("selectedwallpapers"));
				if (bulunan != null) {
					return bulunan;
				}
			}
		} else if (e.isJsonArray()) {
			for (JsonElement x : e.getAsJsonArray()) {
				String bulunan = seciliDosya(x, secimIcinde);
				if (bulunan != null) {
					return bulunan;
				}
			}
		}
		return null;
	}
}
