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

	private RiskHud() {
	}

	public static void init() {
		PayloadTypeRegistry.playS2C().register(RiskPayload.TYPE, RiskPayload.CODEC);
		ClientPlayNetworking.registerGlobalReceiver(RiskPayload.TYPE, (payload, context) -> al(payload.text()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> liste = List.of());
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(CevherClientMod.MOD_ID, "risk"),
			(graphics, delta) -> render(graphics, Minecraft.getInstance()));
	}

	private static void al(String metin) {
		try {
			JsonElement e = JsonParser.parseString(metin);
			if (!e.isJsonObject() || !"liste".equals(str(e.getAsJsonObject(), "t"))) {
				return;
			}
			List<Satir> yeni = new ArrayList<>();
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
			}
			liste = List.copyOf(yeni);
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
