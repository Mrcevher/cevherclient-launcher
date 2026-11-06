package net.cevherclient.client.module.impl;

import net.cevherclient.client.module.TextHudModule;
import net.minecraft.client.Minecraft;

public final class WatermarkModule extends TextHudModule {
	public WatermarkModule() {
		super("watermark", "Cevher İmzası", "CV", true, 0.9f, 0.965f);
		background.value = false;
	}

	@Override
	protected String text(Minecraft mc, boolean preview) {
		return "CevherClient";
	}
}
