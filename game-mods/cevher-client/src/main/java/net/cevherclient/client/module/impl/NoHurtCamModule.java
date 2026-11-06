package net.cevherclient.client.module.impl;

import net.cevherclient.client.module.Module;
import net.cevherclient.client.module.ModuleManager;
import net.cevherclient.client.module.Option;

/** Hasar alınca kameranın yana yatmasını azaltır ya da tamamen kaldırır (GameRendererMixin). */
public final class NoHurtCamModule extends Module {
	private final Option.Range reduction = add(new Option.Range("reduction", "Azaltma (%)", 100f, 0f, 100f, 5f));

	public NoHurtCamModule() {
		super("no_hurt_cam", "Hasar Sarsıntısı", "HS", Category.VISUAL, false);
	}

	public static float apply(float degrees) {
		NoHurtCamModule module = ModuleManager.get(NoHurtCamModule.class);
		if (module == null || !module.isEnabled()) {
			return degrees;
		}
		return degrees * (1f - module.reduction.value / 100f);
	}
}
