package net.cevherclient.client.module.impl;

import net.cevherclient.client.module.Module;
import net.cevherclient.client.module.ModuleManager;
import net.cevherclient.client.module.Option;

/**
 * Karanlıkta her şeyi aydınlık gösterir. Işık haritası her tik yeniden hesaplanırken (LightTextureMixin)
 * parlaklık (gama) ayarı yükseltilir ve/veya gece görüşü etkisi tam verilir; oyuncuya gerçek bir etki eklenmez.
 */
public final class FullbrightModule extends Module {
	private final Option.Choice mode = add(new Option.Choice("mode", "Yöntem", 0, "Gama", "Gece görüşü", "İkisi"));
	private final Option.Range brightness = add(new Option.Range("brightness", "Parlaklık", 10f, 1f, 15f, 0.5f));

	public FullbrightModule() {
		super("fullbright", "Tam Parlaklık", "TP", Category.VISUAL, false);
	}

	private static FullbrightModule active() {
		FullbrightModule module = ModuleManager.get(FullbrightModule.class);
		return module != null && module.isEnabled() ? module : null;
	}

	public static float gamma(float vanilla) {
		FullbrightModule module = active();
		return module != null && module.mode.index != 1 ? Math.max(vanilla, module.brightness.value) : vanilla;
	}

	public static float nightVision(float vanilla) {
		FullbrightModule module = active();
		return module != null && module.mode.index != 0 ? 1f : vanilla;
	}
}
