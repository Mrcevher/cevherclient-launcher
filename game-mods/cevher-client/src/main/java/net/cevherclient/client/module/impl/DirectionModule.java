package net.cevherclient.client.module.impl;

import net.cevherclient.client.module.TextHudModule;
import net.minecraft.client.Minecraft;

public final class DirectionModule extends TextHudModule {
	// Minecraft'ta yaw 0 güneye bakar, 90 batıya, 180 kuzeye, 270 doğuya.
	private static final String[] NAMES = {"Güney", "Güney-Batı", "Batı", "Kuzey-Batı", "Kuzey", "Kuzey-Doğu", "Doğu", "Güney-Doğu"};

	public DirectionModule() {
		super("direction", "Yön HUD", "YN", false, 0.005f, 0.302f);
	}

	@Override
	protected String text(Minecraft mc, boolean preview) {
		if (mc.player == null) {
			return "Yön: Kuzey";
		}
		int index = Math.floorMod(Math.round(mc.player.getYRot() / 45f), 8);
		return "Yön: " + NAMES[index];
	}
}
