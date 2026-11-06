package net.cevherclient.client.ui;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Kendi vuruşlarımızı izler: art arda isabet (kombo) ve son vuruşun mesafesi (erişim). Kombo, hasar alınca ya da
 * bir süre vurulmayınca sıfırlanır. Yalnızca istemcide, saldırı tuşuyla bir varlığa vurulduğu anda ölçülür.
 */
public final class CombatTracker {
	/** Bu kadar süre vuruş olmazsa kombo sıfırlanır (ms). */
	private static final long COMBO_TIMEOUT_MS = 2500;

	private static int combo;
	private static long lastHitAt;
	private static float lastReach = -1f;
	private static int lastHurtTime;

	private CombatTracker() {
	}

	public static void init() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (level.isClientSide() && player == Minecraft.getInstance().player) {
				onAttack(player, entity);
			}
			return InteractionResult.PASS;
		});
	}

	private static void onAttack(Player player, Entity target) {
		if (!(target instanceof LivingEntity)) {
			return;
		}
		lastReach = (float) Math.sqrt(target.getBoundingBox().distanceToSqr(player.getEyePosition()));
		combo++;
		lastHitAt = Util.getMillis();
	}

	/** CevherClientMod her tikte çağırır. */
	public static void tick(Minecraft mc) {
		if (mc.player == null) {
			return;
		}
		int hurt = mc.player.hurtTime;
		if (hurt > lastHurtTime) {
			combo = 0;
		}
		lastHurtTime = hurt;
		if (combo > 0 && Util.getMillis() - lastHitAt > COMBO_TIMEOUT_MS) {
			combo = 0;
		}
	}

	public static int combo() {
		return combo;
	}

	/** Son vuruşun mesafesi; {@code maxAgeMs} içinde vuruş yoksa -1. */
	public static float reach(long maxAgeMs) {
		return lastReach >= 0 && Util.getMillis() - lastHitAt <= maxAgeMs ? lastReach : -1f;
	}
}
