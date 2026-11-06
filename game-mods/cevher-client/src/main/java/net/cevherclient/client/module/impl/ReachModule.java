package net.cevherclient.client.module.impl;

import java.util.Locale;
import net.cevherclient.client.module.Option;
import net.cevherclient.client.module.TextHudModule;
import net.cevherclient.client.ui.CombatTracker;
import net.minecraft.client.Minecraft;

/** Son vuruşun göz ile hedefin hitbox'ı arasındaki mesafesi. */
public final class ReachModule extends TextHudModule {
	private final Option.Range hideAfter = add(new Option.Range("hide_after", "Gizleme süresi (sn)", 3f, 1f, 10f, 0.5f));
	private final Option.Bool gradient = add(new Option.Bool("gradient", "Mesafeye göre renk", true));

	public ReachModule() {
		super("reach", "Erişim Göstergesi", "ERŞ", false, 0.16f, 0.476f);
	}

	private float reach(boolean preview) {
		return preview ? 2.87f : CombatTracker.reach((long) (hideAfter.value * 1000));
	}

	@Override
	protected String text(Minecraft mc, boolean preview) {
		float reach = reach(preview);
		return reach < 0 ? "Erişim: -" : String.format(Locale.ROOT, "Erişim: %.2f", reach);
	}

	@Override
	protected int textColor(Minecraft mc, boolean preview) {
		float reach = reach(preview);
		if (!gradient.value || reach < 0) {
			return super.textColor(mc, preview);
		}
		return reach < 2.5f ? 0xFF55FF77 : reach < 2.9f ? 0xFFFFE066 : 0xFFFF5555;
	}
}
