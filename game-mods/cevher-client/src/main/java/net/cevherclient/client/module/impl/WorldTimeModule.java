package net.cevherclient.client.module.impl;

import java.util.Locale;
import net.cevherclient.client.module.Option;
import net.cevherclient.client.module.TextHudModule;
import net.minecraft.client.Minecraft;

/** Oyun içi saat. Oyunda gün 06:00'da başlar; 1000 tick bir saattir. */
public final class WorldTimeModule extends TextHudModule {
	private final Option.Bool twelveHour = add(new Option.Bool("twelve_hour", "12 saat biçimi", false));

	public WorldTimeModule() {
		super("world_time", "Oyun Saati", "OS", false, 0.16f, 0.244f);
	}

	@Override
	protected String text(Minecraft mc, boolean preview) {
		long ticks = preview || mc.level == null ? 8000 : TimeChangerModule.realDayTime(mc.level) % 24000L;
		int hours = (int) ((ticks / 1000 + 6) % 24);
		int minutes = (int) (ticks % 1000 * 60 / 1000);
		String icon = ticks < 12500 || ticks > 23500 ? "☀ " : "☾ ";
		if (twelveHour.value) {
			int h = hours % 12 == 0 ? 12 : hours % 12;
			return icon + String.format(Locale.ROOT, "%d:%02d %s", h, minutes, hours < 12 ? "ÖÖ" : "ÖS");
		}
		return icon + String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
	}
}
