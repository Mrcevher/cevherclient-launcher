package net.cevherclient.client.vaka;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import net.cevherclient.client.CevherClientMod;
import net.cevherclient.client.Keys;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;

/**
 * OP hile bildirimleri: sunucudan gelen vakaları saklar, yeni/önemli vakada ekranın üst ortasında bir bildirim
 * kutusu gösterir; atanmış tuş (varsayılan J) ile {@link VakaEkrani} açılır.
 */
public final class VakaMerkezi {
	static final int MAX = 60;
	private static final long BILDIRIM_MS = 14_000;

	private static final List<JsonObject> vakalar = new ArrayList<>();
	private static volatile JsonObject bildirim;
	private static volatile long bildirimZamani;
	private static volatile int okunmamis;

	private VakaMerkezi() {
	}

	public static void init() {
		PayloadTypeRegistry.playS2C().register(VakaPayload.TYPE, VakaPayload.CODEC);
		ClientPlayNetworking.registerGlobalReceiver(VakaPayload.TYPE, (payload, context) -> al(payload.text()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			synchronized (vakalar) {
				vakalar.clear();
			}
			bildirim = null;
			okunmamis = 0;
		});
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(CevherClientMod.MOD_ID, "vaka"),
			(graphics, delta) -> render(graphics, Minecraft.getInstance()));
	}

	/** İstemci tikinde çağrılır: tuşa basıldıysa ekranı aç. */
	public static void tick(Minecraft mc) {
		while (Keys.VAKA.consumeClick()) {
			if (mc.screen == null) {
				okunmamis = 0;
				bildirim = null;
				mc.setScreen(new VakaEkrani(null));
			}
		}
	}

	static List<JsonObject> liste() {
		synchronized (vakalar) {
			return new ArrayList<>(vakalar);
		}
	}

	private static void al(String metin) {
		try {
			JsonElement e = JsonParser.parseString(metin);
			if (!e.isJsonObject()) {
				return;
			}
			JsonObject o = e.getAsJsonObject();
			String t = str(o, "t");
			if ("liste".equals(t) && o.has("vakalar")) {
				synchronized (vakalar) {
					vakalar.clear();
					for (JsonElement x : o.getAsJsonArray("vakalar")) {
						if (x.isJsonObject() && vakalar.size() < MAX) {
							vakalar.add(x.getAsJsonObject());
						}
					}
				}
			} else if ("vaka".equals(t) && o.has("vaka")) {
				JsonObject v = o.getAsJsonObject("vaka");
				String id = str(v, "id");
				synchronized (vakalar) {
					vakalar.removeIf(x -> id.equals(str(x, "id")));
					vakalar.add(0, v);
					while (vakalar.size() > MAX) {
						vakalar.remove(vakalar.size() - 1);
					}
				}
				if (o.has("uyar") && o.get("uyar").getAsBoolean()) {
					bildirim = v;
					bildirimZamani = Util.getMillis();
					okunmamis++;
					Minecraft mc = Minecraft.getInstance();
					mc.execute(() -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6f, 0.6f)));
				}
			}
		} catch (RuntimeException ex) {
			CevherClientMod.LOGGER.warn("Hile vakası okunamadı: {}", ex.toString());
		}
	}

	static String str(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}

	static int renk(String durum) {
		return switch (durum) {
			case "HİLE" -> 0xFFFF5555;
			case "TEMİZ" -> 0xFF55FF55;
			case "İNCELENİYOR" -> 0xFFFFAA00;
			default -> 0xFFFFFF55;
		};
	}

	static String sigdir(Font font, String s, int genislik) {
		if (font.width(s) <= genislik) {
			return s;
		}
		while (!s.isEmpty() && font.width(s + "…") > genislik) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "…";
	}

	private static void render(GuiGraphics g, Minecraft mc) {
		JsonObject v = bildirim;
		if (v == null || mc.player == null || mc.screen instanceof VakaEkrani) {
			return;
		}
		long gecen = Util.getMillis() - bildirimZamani;
		if (gecen > BILDIRIM_MS) {
			bildirim = null;
			return;
		}
		Font font = mc.font;
		String durum = str(v, "durum");
		int guven = v.has("guven") ? v.get("guven").getAsInt() : -1;
		String tus = Keys.VAKA.getTranslatedKeyMessage().getString();
		List<String> satir = new ArrayList<>();
		List<Integer> renk = new ArrayList<>();
		satir.add("⚠ Hile vakası: " + str(v, "ad"));
		renk.add(0xFFFFFFFF);
		satir.add(durum + (guven >= 0 ? " (LLM güveni %" + guven + ")" : ""));
		renk.add(renk(durum));
		StringBuilder k = new StringBuilder();
		if (v.has("kontroller")) {
			for (JsonElement x : v.getAsJsonArray("kontroller")) {
				JsonObject c = x.getAsJsonObject();
				if (k.length() > 0) {
					k.append(", ");
				}
				k.append(str(c, "etiket")).append(" ×").append(str(c, "sayi"));
			}
		}
		satir.add(k.toString());
		renk.add(0xFFD0D0D0);
		String ozet = str(v, "ozet");
		if (ozet.isEmpty()) {
			ozet = str(v, "llmHata").isEmpty() ? "LLM inceliyor…" : "LLM: " + str(v, "llmHata");
		}
		satir.add(ozet);
		renk.add(0xFFA0A0A0);
		satir.add("[" + tus + "] detaylar" + (okunmamis > 1 ? " · " + okunmamis + " yeni vaka" : ""));
		renk.add(0xFF55FFFF);

		int max = 280;
		int w = 0;
		for (int i = 0; i < satir.size(); i++) {
			satir.set(i, sigdir(font, satir.get(i), max));
			w = Math.max(w, font.width(satir.get(i)));
		}
		int satirH = font.lineHeight + 2;
		int h = satir.size() * satirH + 6;
		int sw = mc.getWindow().getGuiScaledWidth();
		int x = (sw - w) / 2;
		// İlk 250 ms yukarıdan kayarak gelir.
		int y = 22 - (int) (Math.max(0, 250 - gecen) / 250f * 30);
		g.fill(x - 6, y - 4, x + w + 6, y + h - 4, 0xD0100808);
		g.fill(x - 6, y - 4, x + w + 6, y - 3, renk(durum));
		g.fill(x - 6, y + h - 5, x - 6 + (int) ((w + 12) * (1f - gecen / (float) BILDIRIM_MS)), y + h - 4, renk(durum));
		for (int i = 0; i < satir.size(); i++) {
			g.drawString(font, satir.get(i), x, y + i * satirH, renk.get(i), true);
		}
	}
}
