package net.cevherclient.client.cosmetic;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Sunucunun bildirdiği, diğer oyuncuların her yuvadaki kozmetikleri (oyuncu UUID'si → kozmetik kimliği). */
public final class RemoteCosmetics {
	private static final Map<Cosmetic.Slot, Map<UUID, String>> BY_SLOT = new EnumMap<>(Cosmetic.Slot.class);

	static {
		for (Cosmetic.Slot slot : Cosmetic.Slot.values()) {
			BY_SLOT.put(slot, new ConcurrentHashMap<>());
		}
	}

	private RemoteCosmetics() {
	}

	/** {@code id} null ise oyuncunun o yuvadaki kozmetiği kalkmıştır. */
	static void set(UUID player, Cosmetic.Slot slot, String id) {
		if (id == null) {
			BY_SLOT.get(slot).remove(player);
		} else {
			BY_SLOT.get(slot).put(player, id);
		}
	}

	/** Oyuncunun kozmetiği; seçimi yoksa ya da bu istemci o kozmetiği tanımıyorsa null. */
	public static Cosmetic get(UUID player, Cosmetic.Slot slot) {
		return CosmeticRegistry.find(BY_SLOT.get(slot).get(player));
	}

	static void clear() {
		for (Map<UUID, String> map : BY_SLOT.values()) {
			map.clear();
		}
	}
}
