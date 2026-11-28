package net.cevherclient.client.uibridge;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import net.cevherclient.client.CevherClientMod;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * CevherUIBridge eklentisiyle el sıkışma: girişte {@code cevherclient:hello} gönderir, böylece sunucu bu oyuncuyu
 * Cevherclient kullanıcısı olarak tanır ("hello gelmedi" uyarısı çıkmaz). {@code cevherclient:ui} (sao_on / sao_off)
 * mesajını da kaydeder; sunucu gönderince "bilinmeyen kanal" hatası oluşmaz. Mesaj bu modda yalnızca durum olarak
 * tutulur; SAO HUD'unu ayrı cevher_ui modu çizer.
 *
 * <p>Ayrı cevher_ui modu da yüklüyse aynı kanalları o kaydettiği için bu sınıf hiçbir şey yapmaz (çift kayıt
 * oyunu çökertirdi) ve hello'yu o mod gönderir.
 *
 * <p>hello içeriği yalnızca bilgidir; sunucu ona dayanarak yetki kararı vermez.
 */
public final class UiHelloNet {
	private static final Identifier HELLO_ID = Identifier.fromNamespaceAndPath("cevherclient", "hello");
	private static final Identifier UI_ID = Identifier.fromNamespaceAndPath("cevherclient", "ui");
	private static final int PROTOCOL = 1;
	private static final int MAX_UI_BYTES = 64;
	/** JOIN'den sonra hello için en fazla beklenecek tick (kanal listesi birkaç tick içinde gelir). */
	private static final int SEND_TIMEOUT_TICKS = 100;

	private static int pendingTicks = -1;
	/** Sunucunun istediği SAO durumu (yalnızca bilgi). */
	private static volatile boolean saoRequested;

	private UiHelloNet() {
	}

	public record HelloPayload(String text) implements CustomPacketPayload {
		static final Type<HelloPayload> TYPE = new Type<>(HELLO_ID);
		static final StreamCodec<ByteBuf, HelloPayload> CODEC = StreamCodec.of(
			(buf, payload) -> buf.writeBytes(payload.text().getBytes(StandardCharsets.UTF_8)),
			buf -> {
				// Sunucu bu kanala yazmaz; gelirse içeriği okumadan atılır.
				buf.skipBytes(buf.readableBytes());
				return new HelloPayload("");
			});

		@Override
		public Type<HelloPayload> type() {
			return TYPE;
		}
	}

	public record UiPayload(String text) implements CustomPacketPayload {
		static final Type<UiPayload> TYPE = new Type<>(UI_ID);
		static final StreamCodec<ByteBuf, UiPayload> CODEC = StreamCodec.of(
			(buf, payload) -> buf.writeBytes(payload.text().getBytes(StandardCharsets.UTF_8)),
			buf -> {
				int length = buf.readableBytes();
				if (length > MAX_UI_BYTES) {
					buf.skipBytes(length);
					return new UiPayload("");
				}
				byte[] bytes = new byte[length];
				buf.readBytes(bytes);
				return new UiPayload(new String(bytes, StandardCharsets.UTF_8));
			});

		@Override
		public Type<UiPayload> type() {
			return TYPE;
		}
	}

	public static boolean saoRequested() {
		return saoRequested;
	}

	public static void init() {
		if (FabricLoader.getInstance().isModLoaded("cevher_ui")) {
			CevherClientMod.LOGGER.info("cevher_ui yüklü; hello'yu o gönderir, UiHelloNet devre dışı");
			return;
		}
		PayloadTypeRegistry.playC2S().register(HelloPayload.TYPE, HelloPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(UiPayload.TYPE, UiPayload.CODEC);

		// İşleyici render thread'inde çağrılır.
		ClientPlayNetworking.registerGlobalReceiver(UiPayload.TYPE, (payload, context) -> {
			switch (payload.text().trim()) {
				case "sao_on" -> saoRequested = true;
				case "sao_off" -> saoRequested = false;
				default -> {
				}
			}
		});

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			saoRequested = false;
			pendingTicks = client.hasSingleplayerServer() ? -1 : 0;
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
		ClientConfigurationConnectionEvents.INIT.register((handler, client) -> reset());
		ClientTickEvents.END_CLIENT_TICK.register(UiHelloNet::tick);
	}

	private static void reset() {
		pendingTicks = -1;
		saoRequested = false;
	}

	private static void tick(Minecraft mc) {
		if (pendingTicks < 0 || mc.player == null) {
			return;
		}
		if (ClientPlayNetworking.canSend(HelloPayload.TYPE)) {
			ClientPlayNetworking.send(new HelloPayload(helloJson()));
			pendingTicks = -1;
		} else if (++pendingTicks > SEND_TIMEOUT_TICKS) {
			// Sunucu kanalı dinlemiyor (eklenti yok): sessizce vazgeç.
			pendingTicks = -1;
		}
	}

	private static String helloJson() {
		String mod = FabricLoader.getInstance().getModContainer(CevherClientMod.MOD_ID)
			.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("0");
		String mc = FabricLoader.getInstance().getModContainer("minecraft")
			.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("0");
		return "{\"client\":\"cevherclient\",\"mod\":\"" + safe(mod) + "\",\"mc\":\"" + safe(mc)
			+ "\",\"protocol\":" + PROTOCOL + "}";
	}

	/** JSON'a girecek sürüm metni: yalnızca sürüm karakterleri. */
	private static String safe(String value) {
		return value.replaceAll("[^A-Za-z0-9._+\\-]", "");
	}
}
