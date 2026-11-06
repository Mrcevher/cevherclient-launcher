package net.cevherclient.client.ui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Oyuncuların kaç totem patlattığını sayar (sunucunun gönderdiği 35 numaralı varlık olayı). Oyuncu ölünce,
 * dünyadan çıkınca ya da sunucu değişince sayı sıfırlanır.
 */
public final class TotemPops {
	private static final Map<UUID, Integer> POPS = new HashMap<>();

	private TotemPops() {
	}

	/** ClientPacketListenerMixin totem olayında çağırır. */
	public static void onPop(Entity entity) {
		if (entity instanceof Player player) {
			POPS.merge(player.getUUID(), 1, Integer::sum);
		}
	}

	public static int pops(UUID player) {
		return POPS.getOrDefault(player, 0);
	}

	public static void tick(Minecraft mc) {
		if (mc.level == null) {
			POPS.clear();
			return;
		}
		POPS.keySet().removeIf(uuid -> {
			Player player = mc.level.getPlayerByUUID(uuid);
			return player != null && player.isDeadOrDying();
		});
	}

	public static void clear() {
		POPS.clear();
	}
}
