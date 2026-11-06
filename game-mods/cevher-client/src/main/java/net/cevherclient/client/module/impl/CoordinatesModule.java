package net.cevherclient.client.module.impl;

import net.cevherclient.client.module.Option;
import net.cevherclient.client.module.TextHudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

public final class CoordinatesModule extends TextHudModule {
	private final Option.Bool decimals = add(new Option.Bool("decimals", "Ondalık göster", false));

	public CoordinatesModule() {
		super("coordinates", "Koordinat Görüntüsü", "XYZ", false, 0.005f, 0.186f);
	}

	@Override
	protected String text(Minecraft mc, boolean preview) {
		if (mc.player == null) {
			return "XYZ: 128 64 -312";
		}
		if (decimals.value) {
			return String.format("XYZ: %.1f %.1f %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ());
		}
		return "XYZ: " + Mth.floor(mc.player.getX()) + " " + Mth.floor(mc.player.getY()) + " " + Mth.floor(mc.player.getZ());
	}
}
