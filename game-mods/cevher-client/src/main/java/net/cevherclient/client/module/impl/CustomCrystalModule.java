package net.cevherclient.client.module.impl;

import net.cevherclient.client.module.Module;
import net.cevherclient.client.module.ModuleManager;
import net.cevherclient.client.module.Option;

/** End kristallerinin boyutunu, dönüş hızını ve taban plakasının görünmesini değiştirir (EndCrystalRendererMixin). */
public final class CustomCrystalModule extends Module {
	private final Option.Range size = add(new Option.Range("size", "Boyut", 1f, 0.3f, 2f, 0.1f));
	private final Option.Range spin = add(new Option.Range("spin", "Dönüş hızı", 1f, 0f, 3f, 0.1f));
	private final Option.Choice base = add(new Option.Choice("base", "Taban", 0, "Oyundaki gibi", "Her zaman", "Hiçbir zaman"));

	public CustomCrystalModule() {
		super("custom_crystal", "Özel Kristal", "KR", Category.VISUAL, false);
	}

	private static CustomCrystalModule active() {
		CustomCrystalModule module = ModuleManager.get(CustomCrystalModule.class);
		return module != null && module.isEnabled() ? module : null;
	}

	public static float scale(float vanilla) {
		CustomCrystalModule module = active();
		return module == null ? vanilla : vanilla * module.size.value;
	}

	public static float age(float vanilla) {
		CustomCrystalModule module = active();
		return module == null ? vanilla : vanilla * module.spin.value;
	}

	public static boolean showsBottom(boolean vanilla) {
		CustomCrystalModule module = active();
		if (module == null || module.base.index == 0) {
			return vanilla;
		}
		return module.base.index == 1;
	}
}
