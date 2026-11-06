package net.cevherclient.client.module.impl;

import java.util.Locale;
import net.cevherclient.client.module.TextHudModule;
import net.minecraft.client.Minecraft;

/** Açlık çubuğunda görünmeyen doygunluk değeri ve açlık puanı. */
public final class SaturationModule extends TextHudModule {
	public SaturationModule() {
		super("saturation", "Doygunluk HUD", "DOY", false, 0.16f, 0.302f);
	}

	@Override
	protected String text(Minecraft mc, boolean preview) {
		if (preview || mc.player == null) {
			return "Doygunluk: 5.0 | Açlık: 18";
		}
		var food = mc.player.getFoodData();
		return String.format(Locale.ROOT, "Doygunluk: %.1f | Açlık: %d", food.getSaturationLevel(), food.getFoodLevel());
	}
}
