package net.cevherclient.client.duello;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.regex.Pattern;
import net.cevherclient.client.CevherClientMod;
import net.cevherclient.client.sao.Sao;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;

/**
 * SAO düellosu, istemci tarafı ({@code cevherclient:duello}). Kanal kaydedildiği için sunucu (CevherDuello)
 * oyuncunun modu olduğunu anlar; sandık menüsü, boss çubuğu ve başlık yerine bu sınıfın pencere ve HUD'ı kullanılır.
 *
 * <p>Sunucu → istemci (JSON): {@code istek} (düello penceresi), {@code durum} (hazırlık/dövüş, kalan süre, rakip canı),
 * {@code bitis} (ZAFER/YENİLGİ/BERABERE). İstemci → sunucu: {@code kabul}, {@code reddet}, {@code teslim}; sunucu
 * bunları komutla aynı doğrulamadan geçirir, istemciye güvenmez.
 */
public final class DuelloNet {
	private static final Pattern AD = Pattern.compile("[A-Za-z0-9_.]{1,20}");
	private static final long BANNER_MS = 3200;

	private static final int YESIL = 0xFF8AD34B;
	private static final int SARI = 0xFFF2C233;
	private static final int KIRMIZI = 0xFFD9453A;

	// Bekleyen istek penceresi (başka ekran açıkken hemen açılmaz).
	private static Screen bekleyen;
	private static long bekleyenSonu;

	// Düello durumu (yalnızca render thread'inde dokunulur).
	private static String faz = "";
	private static int kalanSn;
	private static long kalanAt;
	private static String mod = "";
	private static String rakip = "";
	private static float rakipCan = 1f;

	private static String bannerMetin = "";
	private static String bannerAlt = "";
	private static int bannerRenk = Sao.ACCENT;
	private static long bannerAt;

	private DuelloNet() {
	}

	public static void init() {
		PayloadTypeRegistry.playC2S().register(DuelloPayload.TYPE, DuelloPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(DuelloPayload.TYPE, DuelloPayload.CODEC);
		// İşleyici render thread'inde çağrılır.
		ClientPlayNetworking.registerGlobalReceiver(DuelloPayload.TYPE, (payload, context) -> al(payload.text()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> sifirla());
		ClientConfigurationConnectionEvents.INIT.register((handler, client) -> sifirla());
		ClientTickEvents.END_CLIENT_TICK.register(DuelloNet::tick);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(CevherClientMod.MOD_ID, "duello"),
			(graphics, delta) -> render(graphics, Minecraft.getInstance()));
	}

	private static void sifirla() {
		bekleyen = null;
		faz = "";
		bannerMetin = "";
	}

	/** Sunucuya istek gönderir; sunucu kanalı dinlemiyorsa hiçbir şey yapmaz. */
	static void gonder(JsonObject o) {
		if (ClientPlayNetworking.canSend(DuelloPayload.TYPE)) {
			ClientPlayNetworking.send(new DuelloPayload(o.toString()));
		}
	}

	static void ses(float perde) {
		Minecraft.getInstance().getSoundManager()
			.play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, perde, 0.5f));
	}

	// --- Gelen mesajlar ---

	private static String str(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}

	private static int sayi(JsonObject o, String k, int varsayilan, int en, int fazla) {
		JsonElement e = o.get(k);
		if (e == null || !e.isJsonPrimitive()) {
			return varsayilan;
		}
		try {
			return Math.max(en, Math.min(fazla, e.getAsInt()));
		} catch (RuntimeException ex) {
			return varsayilan;
		}
	}

	private static String modAdi(String m) {
		return switch (m) {
			case "ilk", "yari", "tam" -> m;
			default -> "";
		};
	}

	private static void al(String metin) {
		if (metin.isEmpty()) {
			return;
		}
		JsonObject o;
		try {
			JsonElement e = JsonParser.parseString(metin);
			if (!e.isJsonObject()) {
				return;
			}
			o = e.getAsJsonObject();
		} catch (RuntimeException e) {
			CevherClientMod.LOGGER.warn("Düello mesajı okunamadı: {}", e.toString());
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		try {
			switch (str(o, "t")) {
				case "istek" -> {
					String ad = str(o, "ad");
					if (!AD.matcher(ad).matches()) {
						return;
					}
					int sn = sayi(o, "sn", 60, 5, 300);
					ac(new DuelloEkrani(ad, sn), sn * 1000L);
				}
				case "durum" -> {
					String yeniFaz = str(o, "faz");
					if (!yeniFaz.equals("hazirlik") && !yeniFaz.equals("dovus")) {
						return;
					}
					String r = str(o, "rakip");
					if (!AD.matcher(r).matches()) {
						return;
					}
					boolean basladi = yeniFaz.equals("dovus") && !faz.equals("dovus");
					faz = yeniFaz;
					rakip = r;
					mod = modAdi(str(o, "mod"));
					kalanSn = sayi(o, "kalan", 0, 0, 86400);
					kalanAt = Util.getMillis();
					float hp = 1f;
					try {
						hp = Math.max(0f, Math.min(1f, o.get("hp").getAsFloat()));
					} catch (RuntimeException ignored) {
						// eksik/bozuk değer: dolu say
					}
					rakipCan = hp;
					if (basladi) {
						afis("DUEL!!", modEtiketi(mod), Sao.ACCENT);
						ses(1.4f);
					}
					if (mc.screen instanceof DuelloEkrani) {
						mc.setScreen(null); // düello başladıysa eski istek penceresi kapanır
					}
					bekleyen = null;
				}
				case "bitis" -> {
					String sonuc = str(o, "s");
					String kazanan = str(o, "kazanan");
					faz = "";
					switch (sonuc) {
						case "zafer" -> afis("WINNER", AD.matcher(kazanan).matches() ? kazanan : "", Sao.ACCENT_LIGHT);
						case "yenilgi" -> afis("LOSE", AD.matcher(kazanan).matches() ? kazanan + " kazandı" : "", 0xFF9A9A9A);
						case "berabere" -> afis("DRAW", "", Sao.TITLE);
						default -> bannerMetin = "";
					}
					if (!sonuc.equals("iptal")) {
						ses(sonuc.equals("zafer") ? 1.7f : 0.8f);
					}
				}
				default -> {
					// bilinmeyen mesaj: yok say
				}
			}
		} catch (RuntimeException e) {
			CevherClientMod.LOGGER.warn("Düello mesajı işlenemedi: {}", e.toString());
		}
	}

	private static void afis(String metin, String alt, int renk) {
		bannerMetin = metin;
		bannerAlt = alt;
		bannerRenk = renk;
		bannerAt = Util.getMillis();
	}

	static String modEtiketi(String m) {
		return switch (m) {
			case "ilk" -> "First-Strike  ·  İlk Vuruş";
			case "yari" -> "Half-Loss  ·  Yarı Can";
			case "tam" -> "Total-Loss  ·  Tam Kayıp";
			default -> "";
		};
	}

	private static void ac(Screen ekran, long sure) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.screen == null || mc.screen instanceof ChatScreen || mc.screen instanceof DuelloEkrani) {
			bekleyen = null;
			mc.setScreen(ekran);
		} else {
			bekleyen = ekran;
			bekleyenSonu = Util.getMillis() + Math.min(sure, 15_000);
		}
	}

	private static void tick(Minecraft mc) {
		if (bekleyen == null || mc.player == null) {
			return;
		}
		if (Util.getMillis() > bekleyenSonu) {
			bekleyen = null;
		} else if (mc.screen == null && mc.getOverlay() == null) {
			Screen ekran = bekleyen;
			bekleyen = null;
			mc.setScreen(ekran);
		}
	}

	// --- HUD ---

	private static int canRengi(float p) {
		return p > 0.5f ? YESIL : p > 0.2f ? SARI : KIRMIZI;
	}

	private static void cubuk(GuiGraphics g, float x, float y, float w, float h, float oran, boolean sagaYasla) {
		Sao.rect(g, x - 1, y - 1, w + 2, h + 2, 0xC0F4F4F4);
		Sao.rect(g, x, y, w, h, 0xE0585858);
		float dolu = w * Math.max(0f, Math.min(1f, oran));
		if (dolu > 0) {
			Sao.rect(g, sagaYasla ? x + w - dolu : x, y, dolu, h, canRengi(oran));
		}
	}

	private static void render(GuiGraphics g, Minecraft mc) {
		if (mc.player == null || mc.options.hideGui) {
			return;
		}
		float eskiAlpha = Sao.alpha;
		Sao.alpha = 1f;
		try {
			int sw = mc.getWindow().getGuiScaledWidth();
			int sh = mc.getWindow().getGuiScaledHeight();
			long simdi = Util.getMillis();
			float cx = sw / 2f;

			if (!faz.isEmpty()) {
				float ben = mc.player.getMaxHealth() <= 0 ? 0f : mc.player.getHealth() / mc.player.getMaxHealth();
				float bw = 110;
				float y = 16;
				int kalan = (int) Math.max(0, kalanSn - (simdi - kalanAt) / 1000);
				String sure = (kalan / 60) + ":" + (kalan % 60 < 10 ? "0" : "") + (kalan % 60);

				// orta kutu: süre ve evre
				Sao.rect(g, cx - 30, 6, 60, 20, 0xB0E2E2E2);
				Sao.centered(g, faz.equals("hazirlik") ? "HAZIRLIK" : "DUEL", cx, 8, 4.5f, Sao.MUTED);
				Sao.centered(g, sure, cx, 15, 7.5f, kalan <= 10 ? KIRMIZI : Sao.TEXT);

				// sol: ben, sağ: rakip (can ortaya doğru dolu, dışarı doğru azalır)
				cubuk(g, cx - 36 - bw, y, bw, 7, ben, true);
				cubuk(g, cx + 36, y, bw, 7, rakipCan, false);
				Sao.text(g, mc.player.getName().getString(), cx - 36 - bw, 6, 5.5f, 0xFFFFFFFF);
				String r = Sao.fit(rakip, bw, 5.5f);
				Sao.text(g, r, cx + 36 + bw - Sao.textWidth(r, 5.5f), 6, 5.5f, 0xFFFFFFFF);
				if (!mod.isEmpty()) {
					Sao.centered(g, modEtiketi(mod), cx, 30, 4.8f, 0xFFFFFFFF);
				}
				if (faz.equals("hazirlik") && kalan <= 5 && kalan > 0) {
					Sao.alpha = 1f;
					Sao.centered(g, String.valueOf(kalan), cx, sh * 0.30f, 30f, Sao.ACCENT);
				}
			}

			if (!bannerMetin.isEmpty()) {
				long gecen = simdi - bannerAt;
				if (gecen > BANNER_MS) {
					bannerMetin = "";
				} else {
					float giris = Sao.easeOut(Sao.progress(gecen, 0, 220));
					float cikis = 1f - Sao.progress(gecen, BANNER_MS - 600, 600);
					Sao.alpha = Math.max(0f, Math.min(1f, giris * cikis));
					float by = sh * 0.28f;
					float bh = bannerAlt.isEmpty() ? 38 : 50;
					Sao.rect(g, 0, by - 8, sw, bh, 0x90000000);
					Sao.rect(g, 0, by - 8, sw, 1, bannerRenk);
					Sao.rect(g, 0, by - 8 + bh - 1, sw, 1, bannerRenk);
					Sao.centered(g, bannerMetin, cx, by - 2, 24f, bannerRenk);
					if (!bannerAlt.isEmpty()) {
						Sao.centered(g, bannerAlt, cx, by + 28, 6.5f, 0xFFFFFFFF);
					}
				}
			}
		} finally {
			Sao.alpha = eskiAlpha;
		}
	}
}
