package net.cevherclient.client.module.impl;

import java.util.Locale;
import net.cevherclient.client.module.TextHudModule;
import net.cevherclient.client.ui.ServerTps;
import net.minecraft.client.Minecraft;

/** Sunucunun tahmini TPS değeri; düştükçe sarı, sonra kırmızı yazar. */
public final class TpsModule extends TextHudModule {
	public TpsModule() {
		super("tps", "TPS Göstergesi", "TPS", false, 0.16f, 0.012f);
	}

	@Override
	protected String text(Minecraft mc, boolean preview) {
		return String.format(Locale.ROOT, "TPS: %.1f", preview ? 20f : ServerTps.tps());
	}

	@Override
	protected int textColor(Minecraft mc, boolean preview) {
		float tps = preview ? 20f : ServerTps.tps();
		return tps >= 18f ? super.textColor(mc, preview) : tps >= 14f ? 0xFFFFD84D : 0xFFFF5555;
	}
}
