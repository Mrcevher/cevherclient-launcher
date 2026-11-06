package net.cevherclient.client.module;

import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/** Renk seçen ayarların ortak listesi. Son seçenek "Gökkuşağı"dır; rengi zamanla döner. */
public final class Palette {
	private static final String[] NAMES = {"Beyaz", "Kırmızı", "Turuncu", "Sarı", "Yeşil", "Turkuaz", "Mavi", "Mor", "Pembe", "Siyah",
		"Gökkuşağı"};
	private static final int[] COLORS = {0xFFFFFFFF, 0xFFFF5555, 0xFFFFAA33, 0xFFFFE066, 0xFF55FF77, 0xFF55FFFF, 0xFF55AAFF, 0xFFC27BFF,
		0xFFFF77CC, 0xFF111111};

	private Palette() {
	}

	/** Renk listesinden seçim yapan ayar. */
	public static Option.Choice option(String id, String label, int defaultIndex) {
		return new Option.Choice(id, label, defaultIndex, NAMES);
	}

	/** Seçimin şu anki rengi (opak ARGB). */
	public static int color(Option.Choice choice) {
		return color(choice.index);
	}

	public static int color(int index) {
		return color(index, 0f);
	}

	/** {@code hueOffset} yalnızca gökkuşağında işe yarar: listedeki satırlar renk dalgası gibi kayar. */
	public static int color(Option.Choice choice, float hueOffset) {
		return color(choice.index, hueOffset);
	}

	private static int color(int index, float hueOffset) {
		if (index < COLORS.length) {
			return COLORS[index];
		}
		float hue = (Util.getMillis() % 4000L) / 4000f + hueOffset;
		return Mth.hsvToArgb(hue - (float) Math.floor(hue), 0.75f, 1f, 255);
	}

	/** Rengin saydamlığını {@code alpha} (0..1) yapar. */
	public static int withAlpha(int argb, float alpha) {
		int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
		return (a << 24) | (argb & 0xFFFFFF);
	}
}
