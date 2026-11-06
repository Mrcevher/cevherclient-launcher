package net.cevherclient.client.module.impl;

import net.cevherclient.client.module.Module;
import net.cevherclient.client.module.ModuleManager;
import net.cevherclient.client.module.Option;
import net.minecraft.world.level.Level;

/**
 * Dünyanın saatini yalnızca kendi ekranında değiştirir (gökyüzü, ışık). LevelMixin istemci dünyasının
 * {@code getDayTime()} değerini buradan geçirir; sunucuya hiçbir şey gönderilmez. Gün sayısı korunur ki
 * ay evresi değişmesin.
 */
public final class TimeChangerModule extends Module {
	private final Option.Range hour = add(new Option.Range("hour", "Saat", 12f, 0f, 24f, 0.5f));

	public TimeChangerModule() {
		super("time_changer", "Zaman Değiştirici", "ZD", Category.VISUAL, false);
	}

	/** Gösterilecek gün zamanı; modül kapalıysa gerçek değer. */
	public static long apply(long real) {
		TimeChangerModule module = ModuleManager.get(TimeChangerModule.class);
		if (module == null || !module.isEnabled()) {
			return real;
		}
		// Oyunda 0 tick sabah 06:00'dır.
		long ticks = (long) (((module.hour.value - 6f + 24f) % 24f) * 1000f);
		return real / 24000L * 24000L + ticks;
	}

	/** HUD'lar için dünyanın gerçek gün zamanı (modülün değiştirdiği değil). */
	public static long realDayTime(Level level) {
		return level.getLevelData().getDayTime();
	}
}
