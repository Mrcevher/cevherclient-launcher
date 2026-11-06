package net.cevherclient.client.module.impl;

import net.cevherclient.client.module.Module;
import net.cevherclient.client.module.ModuleManager;
import net.cevherclient.client.module.Option;

/**
 * Yere düşen eşyalar dönüp zıplamak yerine yere yatar; bloklar zemine oturur. Yalnızca görünüş:
 * ItemEntityRendererMixin çizimdeki konum ve dönüşü değiştirir.
 */
public final class ItemPhysicModule extends Module {
	private final Option.Bool randomYaw = add(new Option.Bool("random_yaw", "Rastgele yön", true));

	public ItemPhysicModule() {
		super("item_physic", "Eşya Fiziği", "EF", Category.VISUAL, false);
	}

	public static boolean isActive() {
		ItemPhysicModule module = ModuleManager.get(ItemPhysicModule.class);
		return module != null && module.isEnabled();
	}

	/** Eşyanın yerdeki yönü (radyan): her eşya için sabit, eşyanın kendi rastgele değerinden türetilir. */
	public static float yaw(float bobOffset) {
		ItemPhysicModule module = ModuleManager.get(ItemPhysicModule.class);
		return module != null && module.randomYaw.value ? bobOffset * 7.3f : 0f;
	}
}
