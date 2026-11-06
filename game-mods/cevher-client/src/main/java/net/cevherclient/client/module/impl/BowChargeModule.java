package net.cevherclient.client.module.impl;

import net.cevherclient.client.module.TextHudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.BowItem;

/** Yay gerilirken atışın gücü (yüzde). Yay gerilmiyorken görünmez. */
public final class BowChargeModule extends TextHudModule {
	public BowChargeModule() {
		super("bow_charge", "Yay Gerginliği", "YAY", false, 0.47f, 0.58f);
	}

	private static float power(Minecraft mc, boolean preview) {
		if (preview) {
			return 0.75f;
		}
		if (mc.player == null || !mc.player.isUsingItem() || !(mc.player.getUseItem().getItem() instanceof BowItem)) {
			return -1f;
		}
		return BowItem.getPowerForTime(mc.player.getTicksUsingItem());
	}

	@Override
	protected String text(Minecraft mc, boolean preview) {
		float power = power(mc, preview);
		return power < 0 ? null : "Yay: %" + Math.round(power * 100);
	}

	@Override
	protected int textColor(Minecraft mc, boolean preview) {
		return power(mc, preview) >= 1f ? 0xFF55FF77 : super.textColor(mc, preview);
	}
}
