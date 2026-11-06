package net.cevherclient.client.module.impl;

import net.cevherclient.client.module.TextHudModule;
import net.minecraft.client.Minecraft;

/** Dünyanın kaçıncı gününde olunduğu (F3'teki gün sayısı). Zaman Değiştirici'den etkilenmez. */
public final class DayCounterModule extends TextHudModule {
	public DayCounterModule() {
		super("day_counter", "Gün Sayacı", "GÜN", false, 0.16f, 0.186f);
	}

	@Override
	protected String text(Minecraft mc, boolean preview) {
		long day = preview || mc.level == null ? 12 : TimeChangerModule.realDayTime(mc.level) / 24000L;
		return "Gün " + day;
	}
}
