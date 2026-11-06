package net.cevherclient.client.cosmetic;

import java.util.UUID;
import java.util.regex.Pattern;
import net.cevherclient.client.CevherClientMod;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;

/**
 * Kozmetik eşitlemesi, istemci tarafı. Sunucu {@code cevherclient:cosmetic} kanalını dinlemiyorsa (köprü
 * eklentisi yok, tek oyunculu dünya) hiçbir şey gönderilmez ve kozmetik yalnızca kendi karakterimizde görünür.
 *
 * <ul>
 * <li>Girişten kısa süre sonra seçim yuva başına bir mesajla gönderilir: {@code head=<id>}, {@code back=<id>},
 *     {@code cape=<id>}; seçim değişince yalnızca o yuva yeniden. Sırt ve pelerin, seçili değilse girişte
 *     gönderilmez (yalnızca baş yuvasını bilen eski eklenti sürümleri bunları geçersiz sayar).</li>
 * <li>Sunucudan {@code <uuid> <id|->} (baş yuvası, eski biçim) ya da {@code <uuid> <yuva>=<id|->} satırları gelir;
 *     diğer oyuncuların kozmetikleri {@link RemoteCosmetics}'e yazılır.</li>
 * <li>Sunucu kendi satırımızı gönderirse seçimimiz reddedilmiştir; yerel seçim sunucunun söylediğine çekilir.</li>
 * </ul>
 */
public final class CosmeticNet {
	private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,32}");
	/** JOIN'den sonra seçimi göndermek için en fazla beklenecek tick (kanal listesi birkaç tick içinde gelir). */
	private static final int SEND_TIMEOUT_TICKS = 100;

	private static int pendingTicks = -1;

	private CosmeticNet() {
	}

	public static void init() {
		PayloadTypeRegistry.playC2S().register(CosmeticPayload.TYPE, CosmeticPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(CosmeticPayload.TYPE, CosmeticPayload.CODEC);

		// İşleyici render thread'inde çağrılır.
		ClientPlayNetworking.registerGlobalReceiver(CosmeticPayload.TYPE, (payload, context) -> receive(payload.text()));

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			RemoteCosmetics.clear();
			pendingTicks = client.hasSingleplayerServer() ? -1 : 0;
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			pendingTicks = -1;
			RemoteCosmetics.clear();
		});
		// Proxy ile sunucu değişiminde bağlantı configuration fazına döner; eski sunucunun verisi geçersizdir.
		ClientConfigurationConnectionEvents.INIT.register((handler, client) -> {
			pendingTicks = -1;
			RemoteCosmetics.clear();
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (pendingTicks < 0 || client.getConnection() == null) {
				return;
			}
			if (sendAll()) {
				pendingTicks = -1;
			} else if (++pendingTicks > SEND_TIMEOUT_TICKS) {
				pendingTicks = -1;
			}
		});
	}

	/** Girişte: baş yuvası her zaman, diğerleri seçiliyse. Sunucu kanalı dinlemiyorsa false. */
	private static boolean sendAll() {
		if (!ClientPlayNetworking.canSend(CosmeticPayload.TYPE)) {
			return false;
		}
		for (Cosmetic.Slot slot : Cosmetic.Slot.values()) {
			if (slot == Cosmetic.Slot.HEAD || CosmeticSelection.id(slot) != null) {
				sendSelection(slot);
			}
		}
		return true;
	}

	/**
	 * Bir yuvanın seçimini sunucuya bildirir.
	 *
	 * @return mesaj gönderildiyse true; sunucu kanalı dinlemiyorsa (ya da henüz bildirmediyse) false
	 */
	public static boolean sendSelection(Cosmetic.Slot slot) {
		if (!ClientPlayNetworking.canSend(CosmeticPayload.TYPE)) {
			return false;
		}
		String id = CosmeticSelection.id(slot);
		ClientPlayNetworking.send(new CosmeticPayload(slot.key + "=" + (id == null ? "" : id)));
		return true;
	}

	private static void receive(String text) {
		for (String line : text.split("\n")) {
			String[] parts = line.trim().split(" ");
			if (parts.length != 2) {
				continue;
			}
			UUID player;
			try {
				player = UUID.fromString(parts[0]);
			} catch (IllegalArgumentException e) {
				continue;
			}
			Cosmetic.Slot slot = Cosmetic.Slot.HEAD;
			String value = parts[1];
			int eq = value.indexOf('=');
			if (eq >= 0) {
				try {
					slot = Cosmetic.Slot.parse(value.substring(0, eq));
				} catch (IllegalArgumentException e) {
					continue;
				}
				value = value.substring(eq + 1);
			}
			String id = value.equals("-") || value.isEmpty() ? null : value;
			if (id != null && !ID.matcher(id).matches()) {
				continue;
			}
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null && player.equals(mc.player.getUUID())) {
				CevherClientMod.LOGGER.info("Sunucu {} kozmetiği seçimimizi düzeltti: {}", slot.key, id);
				CosmeticSelection.applyServer(slot, id);
			} else {
				RemoteCosmetics.set(player, slot, id);
			}
		}
	}
}
