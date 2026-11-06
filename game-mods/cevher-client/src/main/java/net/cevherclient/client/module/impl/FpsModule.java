package net.cevherclient.client.module.impl;

import net.cevherclient.client.module.TextHudModule;
import net.minecraft.client.Minecraft;

public final class FpsModule extends TextHudModule {
	public FpsModule() {
		super("fps", "FPS Görüntüsü", "FPS", true, 0.005f, 0.012f);
	}

	@Override
	protected String text(Minecraft mc, boolean preview) {
		return "FPS: " + mc.getFps();
	}
}
