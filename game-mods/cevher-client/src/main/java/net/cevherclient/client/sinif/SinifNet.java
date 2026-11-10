package net.cevherclient.client.sinif;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.cevherclient.client.CevherClientMod;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Util;

/**
 * Sınıf ekranlarının istemci tarafı ağ işlemleri ({@code cevherclient:sinif}). Kanal kaydedildiği için sunucu
 * (SinifSistemi) oyuncunun modu olduğunu anlar ve sandık menüsü yerine veri gönderir.
 *
 * <p>Gelen ekran, oyuncu başka bir ekrandayken (sohbet dışında: kaynak paketi onayı, envanter, duraklatma)
 * hemen açılmaz; o ekran kapanınca açılır. Böylece zorunlu sınıf seçimi kaynak paketi ekranını ezmez.
 */
public final class SinifNet {
	/** Bekleyen ekranın en uzun bekleme süresi (zorunlu seçim için sınırsız). */
	private static final long BEKLEME_MS = 15_000;

	private static Screen bekleyen;
	private static long bekleyenSonu;

	private SinifNet() {
	}

	public static void init() {
		PayloadTypeRegistry.playC2S().register(SinifPayload.TYPE, SinifPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SinifPayload.TYPE, SinifPayload.CODEC);
		// İşleyici render thread'inde çağrılır.
		ClientPlayNetworking.registerGlobalReceiver(SinifPayload.TYPE, (payload, context) -> al(payload.text()));

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> bekleyen = null);
		ClientConfigurationConnectionEvents.INIT.register((handler, client) -> bekleyen = null);
		ClientTickEvents.END_CLIENT_TICK.register(SinifNet::tick);
		ClientTickEvents.START_CLIENT_TICK.register(SinifNet::tuslar);
	}

	/**
	 * 1-6 tuşları: sunucu kanalı varken eşya slotu seçmez, o slottaki yeteneği kullanır. Vanilla tuş işlemesinden
	 * önce (tick başında) tıklamalar tüketilir.
	 */
	private static void tuslar(Minecraft mc) {
		if (mc.player == null || mc.screen != null || !ClientPlayNetworking.canSend(SinifPayload.TYPE)) {
			return;
		}
		for (int i = 0; i < 6; i++) {
			while (mc.options.keyHotbarSlots[i].consumeClick()) {
				JsonObject o = new JsonObject();
				o.addProperty("t", "at");
				o.addProperty("slot", i + 1);
				gonder(o);
			}
		}
	}

	/** Sunucuya istek gönderir; sunucu kanalı dinlemiyorsa hiçbir şey yapmaz. */
	public static void gonder(JsonObject o) {
		if (ClientPlayNetworking.canSend(SinifPayload.TYPE)) {
			ClientPlayNetworking.send(new SinifPayload(o.toString()));
		}
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
			CevherClientMod.LOGGER.warn("Sınıf mesajı okunamadı: {}", e.toString());
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		try {
			switch (SinifVeri.str(o, "t")) {
				case "secim" -> {
					SinifVeri.Secim veri = SinifVeri.secim(o);
					if (mc.screen instanceof SinifSecimEkrani ekran) {
						ekran.guncelle(veri);
					} else {
						ac(new SinifSecimEkrani(veri), veri.zorunlu() ? Long.MAX_VALUE : BEKLEME_MS);
					}
				}
				case "agac" -> {
					SinifVeri.Agac veri = SinifVeri.agac(o);
					if (mc.screen instanceof SinifAgaciEkrani ekran) {
						ekran.guncelle(veri);
					} else {
						ac(new SinifAgaciEkrani(veri), BEKLEME_MS);
					}
				}
				case "yetenek" -> {
					SinifVeri.Yetenekler y = SinifVeri.yetenekler(o);
					SinifAgaciEkrani.yetenekler = y;
					if (mc.screen instanceof SinifAgaciEkrani ekran) {
						ekran.yetenekYenile();
					}
				}
				case "kapat" -> {
					bekleyen = null;
					if (mc.screen instanceof SinifEkrani ekran) {
						ekran.sunucuKapatti();
						mc.setScreen(null);
					}
				}
				default -> {
					// bilinmeyen mesaj: yok say
				}
			}
		} catch (RuntimeException e) {
			CevherClientMod.LOGGER.warn("Sınıf ekranı açılamadı: {}", e.toString());
		}
	}

	private static void ac(Screen ekran, long sure) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.screen == null || mc.screen instanceof SinifEkrani
			|| mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen) {
			if (mc.screen instanceof SinifEkrani eski) {
				eski.sunucuKapatti();
			}
			bekleyen = null;
			mc.setScreen(ekran);
		} else {
			bekleyen = ekran;
			bekleyenSonu = sure == Long.MAX_VALUE ? Long.MAX_VALUE : Util.getMillis() + sure;
		}
	}

	private static void tick(Minecraft mc) {
		if (bekleyen == null || mc.player == null) {
			return;
		}
		if (Util.getMillis() > bekleyenSonu) {
			bekleyen = null;
			return;
		}
		if (mc.screen == null && mc.getOverlay() == null) {
			Screen ekran = bekleyen;
			bekleyen = null;
			mc.setScreen(ekran);
		}
	}
}
