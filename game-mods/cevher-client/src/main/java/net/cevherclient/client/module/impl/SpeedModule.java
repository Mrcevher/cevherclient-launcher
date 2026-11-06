package net.cevherclient.client.module.impl;

import java.util.Locale;
import net.cevherclient.client.module.TextHudModule;
import net.minecraft.client.Minecraft;

/** Yatay hızı blok/saniye olarak gösterir; konum farkı her tikte ölçülür ve yumuşatılır. */
public final class SpeedModule extends TextHudModule {
	private double lastX = Double.NaN;
	private double lastZ;
	private float speed;

	public SpeedModule() {
		super("speed", "Hız Göstergesi", "HIZ", false, 0.16f, 0.07f);
	}

	@Override
	public void onTick(Minecraft mc) {
		double x = mc.player.getX();
		double z = mc.player.getZ();
		if (!Double.isNaN(lastX)) {
			float now = (float) Math.sqrt((x - lastX) * (x - lastX) + (z - lastZ) * (z - lastZ)) * 20f;
			// Işınlanma gibi ani sıçramalar göstergeyi bozmasın.
			speed = now > 100f ? speed : speed + (now - speed) * 0.5f;
		}
		lastX = x;
		lastZ = z;
	}

	@Override
	protected String text(Minecraft mc, boolean preview) {
		return String.format(Locale.ROOT, "Hız: %.2f b/s", preview ? 5.61f : speed);
	}
}
