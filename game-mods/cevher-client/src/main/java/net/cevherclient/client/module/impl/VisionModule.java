package net.cevherclient.client.module.impl;

import net.cevherclient.client.module.Module;
import net.cevherclient.client.module.ModuleManager;
import net.cevherclient.client.module.Option;

/** Görüşü kapatan ekran efektlerini hafifletir: balkabağı maskesi ve yanarken ekranı kaplayan ateş. */
public final class VisionModule extends Module {
	private final Option.Bool pumpkin = add(new Option.Bool("pumpkin", "Balkabağı maskesini gizle", true));
	private final Option.Range fireLower = add(new Option.Range("fire_lower", "Ateşi indir", 0.25f, 0f, 0.6f, 0.05f));

	public VisionModule() {
		super("vision", "Görüş Ayarları", "GA", Category.VISUAL, false);
	}

	private static VisionModule active() {
		VisionModule module = ModuleManager.get(VisionModule.class);
		return module != null && module.isEnabled() ? module : null;
	}

	public static boolean hidePumpkin() {
		VisionModule module = active();
		return module != null && module.pumpkin.value;
	}

	/** Ateş efektinin ekrandaki yüksekliği (oyunda -0,3). */
	public static float fireY(float vanilla) {
		VisionModule module = active();
		return module == null ? vanilla : vanilla - module.fireLower.value;
	}
}
