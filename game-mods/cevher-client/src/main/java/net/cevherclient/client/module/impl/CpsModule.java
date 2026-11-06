package net.cevherclient.client.module.impl;

import net.cevherclient.client.module.TextHudModule;
import net.cevherclient.client.ui.ClickTracker;
import net.minecraft.client.Minecraft;

public final class CpsModule extends TextHudModule {
	public CpsModule() {
		super("cps", "CPS Sayacı", "CPS", false, 0.005f, 0.128f);
	}

	@Override
	protected String text(Minecraft mc, boolean preview) {
		if (preview) {
			return "CPS: 7 | 3";
		}
		return "CPS: " + ClickTracker.cps(0) + " | " + ClickTracker.cps(1);
	}
}
