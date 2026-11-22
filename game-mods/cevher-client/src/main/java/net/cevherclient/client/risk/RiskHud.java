package net.cevherclient.client.risk;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import net.cevherclient.client.CevherClientMod;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/**
 * OP risk listesi: sunucu (CevherAuth) yalnızca OP'lara {@code cevherclient:risk} kanalından hileyle gelen
 * oyuncuları gönderir; liste ekranın sağ üst köşesinde risk sınıflarına göre gruplanarak çizilir.
 * Kanal kaydedildiği için sunucu modun yüklü olduğunu anlar. Liste boşsa hiçbir şey çizilmez.
 */
public final class RiskHud {
	private record Satir(String ad, String seviye, String neden, boolean online) {
	}

	private static final String[] SINIFLAR = {"KRITIK", "YUKSEK", "ORTA", "DUSUK"};
	private static final String[] ETIKET = {"KRİTİK", "YÜKSEK", "ORTA", "DÜŞÜK"};
	private static final int[] RENK = {0xFFFF5555, 0xFFFFAA00, 0xFFFFFF55, 0xFFAAAAAA};
	private static final int MAX_GENISLIK = 230;

	private static volatile List<Satir> liste = List.of();
	/** Sunucudan gelen ham oyuncu kayıtları (vaka ekranının "Karantina & risk" sekmesi için). */
	private static volatile List<JsonObject> ham = List.of();
	/** Liste her değiştiğinde artar. */
	public static volatile int surum;
	/** Kalıcı yasaklar (CevherAuth {"t":"yasaklar"}); vaka ekranının "Yasaklar" sekmesi. */
	private static volatile List<JsonObject> yasaklar = List.of();
	public static volatile int yasakSurum;
	/** Sunucunun bildirdiği yasak yetkim: KURUCU / ÜST YETKİLİ; boş = yetkim yok; null = sunucu bildirmedi (eski sürüm). */
	public static volatile String yasakRol;

	public static List<JsonObject> yasaklar() {
		return yasaklar;
	}

	private RiskHud() {
	}

	/** Yalnızca geliştirme ortamı: vaka ekranının karantina sekmesini sunucusuz denemek için örnek liste. */
	public static void devOrnek() {
		long t = System.currentTimeMillis();
		al("{\"t\":\"liste\",\"oyuncular\":[{\"ad\":\"Gladiator\",\"seviye\":\"KRITIK\",\"neden\":\"lobi:launcher-karantina: opsec.jar — hile kelimesi\",\"online\":true,"
			+ "\"ip\":\"78.180.12.4\",\"son\":" + t + ",\"bulgular\":[{\"zaman\":" + (t - 60000) + ",\"seviye\":\"KRITIK\",\"kaynak\":\"lobi:launcher-karantina\","
			+ "\"detay\":\"opsec-1.21.jar — bilinmeyen jar, içinde 'killaura' sınıfı\",\"eski\":false},{\"zaman\":" + (t - 3600000) + ",\"seviye\":\"DUSUK\","
			+ "\"kaynak\":\"ayni-ip\",\"detay\":\"Kral34 (YÜKSEK) ile aynı IP\",\"eski\":true}]},"
			+ "{\"ad\":\"Kral34\",\"seviye\":\"YUKSEK\",\"neden\":\"koruma: yetkili onayladı: Reach ×4\",\"online\":false,\"ip\":\"78.180.12.4\",\"son\":" + (t - 900000)
			+ ",\"bulgular\":[{\"zaman\":" + (t - 900000) + ",\"seviye\":\"YUKSEK\",\"kaynak\":\"koruma\",\"detay\":\"yetkili onayladı (arda): Vuruş mesafesi (Reach) ×4\",\"eski\":false}]}]}");
	}

	/** Yalnızca geliştirme ortamı: örnek yasak (kanıt, onay ve itirazla). */
	public static void devYasakOrnek() {
		long t = System.currentTimeMillis();
		al("{\"t\":\"yasaklar\",\"yasaklar\":[{\"id\":\"aB3dE9xQ\",\"ana\":\"CtrlAltDefeate\",\"adlar\":[\"ctrlaltdefeate\",\"yanhesap01\"],\"ipler\":[\"88.230.149.252\"],"
			+ "\"cihaz\":3,\"damga\":1,\"sebep\":\"Hile (Reach)\",\"yetkili\":\"arda\",\"vaka\":\"311W2\",\"zaman\":" + (t - 7200000) + ","
			+ "\"gecmis\":[\"20.11.2026 05:30 yasaklandı: CtrlAltDefeate — Hile (Reach) (arda, vaka 311W2)\",\"20.11.2026 06:10 yan hesap yakalandı: yanhesap01 (cihaz izi)\"],"
			+ "\"kanit\":{\"guven\":95,\"karar\":\"HILE\",\"tur\":\"Reach\",\"ozet\":\"Oyuncu 5+ blok mesafeden defalarca vurdu.\",\"gerekce\":\"Ping 11 ms ve TPS 20 iken 13 kez 5.14-5.47 blok; gecikmeyle açıklanamaz.\","
			+ "\"not\":\"arkadaş testi\",\"dunya\":\"world\",\"konum\":\"12, 64, -30\",\"istemci\":\"fabric\",\"ping\":11,\"tps\":20.0,\"puan\":40.9,\"vakaBas\":" + (t - 7300000) + ",\"vakaBit\":" + (t - 7290000) + ","
			+ "\"kontroller\":[\"Vuruş mesafesi (Reach) ×13 — en yüksek: 5.47 blok (sınır 3.92) hedef arda\"],"
			+ "\"satirlar\":[\"05:22:12 Vuruş mesafesi (Reach): 5.14 blok (sınır 3.92) [ping 11 ms, TPS 20.0]\",\"05:22:14 Vuruş mesafesi (Reach): 5.47 blok (sınır 3.92) [ping 11 ms, TPS 20.0]\"]},"
			+ "\"onaylar\":[{\"ad\":\"yetkili1\",\"rol\":\"ÜST YETKİLİ\",\"zaman\":" + (t - 600000) + "}],"
			+ "\"talepler\":[{\"id\":\"q1w2e3\",\"zaman\":" + (t - 1200000) + ",\"hesap\":\"CtrlAltDefeate\",\"mesaj\":\"Hile kullanmadım, o gün arkadaşımla test yapıyorduk. Lütfen yasağı kaldırın.\",\"durum\":\"BEKLIYOR\",\"islemYapan\":\"\",\"islemNeden\":\"\",\"islemZaman\":0}]}]}");
	}

	public static List<JsonObject> oyuncular() {
		return ham;
	}

	/** Bir kaydın "yeni bulgu" anahtarı: ad + en son bulgunun zamanı (ya da seviye). */
	public static String anahtar(JsonObject o) {
		String z = "";
		if (o.has("bulgular") && o.get("bulgular").isJsonArray() && !o.getAsJsonArray("bulgular").isEmpty()) {
			JsonElement b = o.getAsJsonArray("bulgular").get(0);
			z = b.isJsonObject() ? String.valueOf(str(b.getAsJsonObject(), "zaman")) : "";
		}
		return str(o, "ad") + "|" + str(o, "seviye") + "|" + z;
	}

	public static void init() {
		PayloadTypeRegistry.playS2C().register(RiskPayload.TYPE, RiskPayload.CODEC);
		ClientPlayNetworking.registerGlobalReceiver(RiskPayload.TYPE, (payload, context) -> al(payload.text()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			liste = List.of();
			ham = List.of();
			yasaklar = List.of();
			surum++;
			yasakSurum++;
		});
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(CevherClientMod.MOD_ID, "risk"),
			(graphics, delta) -> render(graphics, Minecraft.getInstance()));
	}

	private static void al(String metin) {
		try {
			JsonElement e = JsonParser.parseString(metin);
			if (e.isJsonObject() && "yasaklar".equals(str(e.getAsJsonObject(), "t")) && e.getAsJsonObject().has("yasaklar")) {
				List<JsonObject> y = new ArrayList<>();
				for (JsonElement x : e.getAsJsonObject().getAsJsonArray("yasaklar")) {
					if (x.isJsonObject()) {
						y.add(x.getAsJsonObject());
					}
				}
				yasaklar = List.copyOf(y);
				yasakRol = e.getAsJsonObject().has("rol") ? str(e.getAsJsonObject(), "rol") : null;
				yasakSurum++;
				return;
			}
			if (!e.isJsonObject() || !"liste".equals(str(e.getAsJsonObject(), "t"))) {
				return;
			}
			List<Satir> yeni = new ArrayList<>();
			List<JsonObject> yeniHam = new ArrayList<>();
			for (JsonElement x : e.getAsJsonObject().getAsJsonArray("oyuncular")) {
				if (!x.isJsonObject() || yeni.size() >= 20) {
					continue;
				}
				JsonObject o = x.getAsJsonObject();
				String ad = str(o, "ad");
				if (ad == null) {
					continue;
				}
				yeni.add(new Satir(ad, String.valueOf(str(o, "seviye")), String.valueOf(str(o, "neden")),
					o.has("online") && o.get("online").getAsBoolean()));
				yeniHam.add(o);
			}
			liste = List.copyOf(yeni);
			ham = List.copyOf(yeniHam);
			surum++;
		} catch (RuntimeException ex) {
			CevherClientMod.LOGGER.warn("Risk listesi okunamadı: {}", ex.toString());
		}
	}

	private static String str(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
	}

	private static String sigdir(Font font, String s, int genislik) {
		if (font.width(s) <= genislik) {
			return s;
		}
		while (!s.isEmpty() && font.width(s + "…") > genislik) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "…";
	}

	private static void render(GuiGraphics g, Minecraft mc) {
		List<Satir> l = liste;
		if (l.isEmpty() || mc.player == null) {
			return;
		}
		Font font = mc.font;
		List<String> metin = new ArrayList<>();
		List<Integer> renk = new ArrayList<>();
		metin.add("⚠ Riskli oyuncular (" + l.size() + ")");
		renk.add(0xFFFFFFFF);
		for (int i = 0; i < SINIFLAR.length; i++) {
			boolean baslik = false;
			for (Satir s : l) {
				if (!SINIFLAR[i].equals(s.seviye())) {
					continue;
				}
				if (!baslik) {
					metin.add(ETIKET[i]);
					renk.add(RENK[i]);
					baslik = true;
				}
				metin.add(" " + s.ad() + (s.online() ? "" : " (çıktı)") + " — " + s.neden());
				renk.add(s.online() ? 0xFFE0E0E0 : 0xFF808080);
			}
		}
		int w = 0;
		for (int i = 0; i < metin.size(); i++) {
			metin.set(i, sigdir(font, metin.get(i), MAX_GENISLIK));
			w = Math.max(w, font.width(metin.get(i)));
		}
		int satirH = font.lineHeight + 1;
		int h = metin.size() * satirH + 4;
		int sw = mc.getWindow().getGuiScaledWidth();
		// Sağ üstteki iksir efekti simgelerinin altına in.
		int y = mc.player.getActiveEffects().isEmpty() ? 4 : 56;
		int x = sw - w - 8;
		g.fill(x - 3, y - 2, sw - 2, y + h - 2, 0x90000000);
		g.fill(x - 3, y - 2, x - 2, y + h - 2, 0xFFFF5555);
		for (int i = 0; i < metin.size(); i++) {
			g.drawString(font, metin.get(i), x, y + i * satirH, renk.get(i), true);
		}
	}
}
