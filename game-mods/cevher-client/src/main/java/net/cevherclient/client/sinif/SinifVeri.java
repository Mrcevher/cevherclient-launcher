package net.cevherclient.client.sinif;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Sunucudan gelen sınıf ekranı verisi. Renkli yazılar satır = parça dizisi olarak gelir
 * ({@code {"t": yazı, "c": "#rrggbb", "b": kalın}}); sunucu MMOCore/MiniMessage renklerini zaten çözmüştür.
 */
public final class SinifVeri {
	private SinifVeri() {
	}

	public record Parca(String metin, int renk, boolean kalin) {
	}

	public record Satir(List<Parca> parcalar) {
		public static final Satir BOS = new Satir(List.of());

		public boolean bos() {
			for (Parca p : parcalar) {
				if (!p.metin().isBlank()) {
					return false;
				}
			}
			return true;
		}

		public String duz() {
			StringBuilder sb = new StringBuilder();
			for (Parca p : parcalar) {
				sb.append(p.metin());
			}
			return sb.toString();
		}

		/** Önbellek anahtarı için: yazı + renk + kalınlık. */
		public String anahtar() {
			StringBuilder sb = new StringBuilder();
			for (Parca p : parcalar) {
				sb.append(Integer.toHexString(p.renk())).append(p.kalin() ? '!' : ':').append(p.metin()).append('\u0001');
			}
			return sb.toString();
		}

		public static Satir duz(String metin, int renk) {
			return new Satir(List.of(new Parca(metin, renk, false)));
		}

		public static Satir kalin(String metin, int renk) {
			return new Satir(List.of(new Parca(metin, renk, true)));
		}
	}

	public record Sinif(String id, Satir ad, int renk, String amblem, boolean aktif, int seviye, List<Satir> aciklama,
			Satir durum) {
	}

	public record Dugum(String id, int x, int y, String boyut, String durum, int seviye, int max, Satir ad,
			List<Satir> lore) {
		public boolean kilitli() {
			return durum.equals("LOCKED") || durum.equals("FULLY_LOCKED");
		}
	}

	public record Kenar(String a, String b, int durum) {
	}

	public record Agac(String sinif, boolean degistirebilir, List<Sinif> siniflar, Satir agacAd, int puan, int sifirlama,
			Satir puanYazi, List<Satir> sifirlaIpucu, List<Dugum> dugumler, List<Kenar> kenarlar, Satir bildirim) {
	}

	public record Secim(boolean zorunlu, String mevcut, Satir baslik, Satir altbaslik, List<Sinif> siniflar,
			Satir bildirim) {
	}

	public record Yetenek(String id, boolean pasif, Satir ad, int acilis, boolean acik, int slot, List<Satir> lore) {
	}

	public record Yetenekler(String sinif, int seviye, List<Yetenek> liste) {
	}

	// --- Ayrıştırma ---

	static Yetenekler yetenekler(JsonObject o) {
		List<Yetenek> liste = new ArrayList<>();
		for (JsonElement e : dizi(o, "yetenekler")) {
			if (e.isJsonObject()) {
				JsonObject y = e.getAsJsonObject();
				liste.add(new Yetenek(str(y, "id"), bool(y, "pasif"), satir(y.get("ad")), num(y, "acilis"), bool(y, "acik"), num(y, "slot"),
					satirlar(y.get("lore"))));
			}
		}
		return new Yetenekler(str(o, "sinif"), num(o, "seviye"), liste);
	}

	static Agac agac(JsonObject o) {
		JsonObject ag = obj(o, "agac");
		List<Dugum> dugumler = new ArrayList<>();
		for (JsonElement e : dizi(o, "dugumler")) {
			if (!e.isJsonObject()) {
				continue;
			}
			JsonObject n = e.getAsJsonObject();
			dugumler.add(new Dugum(str(n, "id"), num(n, "x"), num(n, "y"), str(n, "boyut"), str(n, "durum"),
				num(n, "seviye"), num(n, "max"), satir(n.get("ad")), satirlar(n.get("lore"))));
		}
		List<Kenar> kenarlar = new ArrayList<>();
		for (JsonElement e : dizi(o, "kenarlar")) {
			if (e.isJsonObject()) {
				JsonObject k = e.getAsJsonObject();
				kenarlar.add(new Kenar(str(k, "a"), str(k, "b"), num(k, "d")));
			}
		}
		return new Agac(str(o, "sinif"), bool(o, "degistirebilir"), siniflar(dizi(o, "siniflar")), satir(ag.get("ad")),
			num(ag, "puan"), num(ag, "sifirlama"), satir(ag.get("puanYazi")), satirlar(ag.get("sifirlaIpucu")),
			dugumler, kenarlar, satir(o.get("bildirim")));
	}

	static Secim secim(JsonObject o) {
		return new Secim(bool(o, "zorunlu"), str(o, "mevcut"), satir(o.get("baslik")), satir(o.get("altbaslik")),
			siniflar(dizi(o, "siniflar")), satir(o.get("bildirim")));
	}

	private static List<Sinif> siniflar(JsonArray a) {
		List<Sinif> liste = new ArrayList<>();
		for (JsonElement e : a) {
			if (!e.isJsonObject()) {
				continue;
			}
			JsonObject s = e.getAsJsonObject();
			liste.add(new Sinif(str(s, "id"), satir(s.get("ad")), renk(str(s, "renk"), 0xFFFFFFFF), str(s, "amblem"),
				bool(s, "aktif"), num(s, "seviye"), satirlar(s.get("aciklama")), satir(s.get("durum"))));
		}
		return liste;
	}

	static Satir satir(JsonElement e) {
		if (e == null || !e.isJsonArray()) {
			return Satir.BOS;
		}
		List<Parca> parcalar = new ArrayList<>();
		for (JsonElement p : e.getAsJsonArray()) {
			if (!p.isJsonObject()) {
				continue;
			}
			JsonObject o = p.getAsJsonObject();
			String metin = str(o, "t");
			if (!metin.isEmpty()) {
				parcalar.add(new Parca(metin, renk(str(o, "c"), 0xFFFFFFFF), bool(o, "b")));
			}
		}
		return new Satir(List.copyOf(parcalar));
	}

	static List<Satir> satirlar(JsonElement e) {
		List<Satir> liste = new ArrayList<>();
		if (e != null && e.isJsonArray()) {
			for (JsonElement s : e.getAsJsonArray()) {
				liste.add(satir(s));
			}
		}
		return liste;
	}

	static int renk(String hex, int varsayilan) {
		if (hex == null || !hex.matches("#[0-9a-fA-F]{6}")) {
			return varsayilan;
		}
		return 0xFF000000 | Integer.parseInt(hex.substring(1), 16);
	}

	private static JsonObject obj(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
	}

	private static JsonArray dizi(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
	}

	static String str(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}

	private static int num(JsonObject o, String k) {
		JsonElement e = o.get(k);
		try {
			return e != null && e.isJsonPrimitive() ? e.getAsInt() : 0;
		} catch (NumberFormatException ex) {
			return 0;
		}
	}

	private static boolean bool(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e != null && e.isJsonPrimitive() && e.getAsBoolean();
	}
}
