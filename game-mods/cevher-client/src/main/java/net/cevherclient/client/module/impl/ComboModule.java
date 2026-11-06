package net.cevherclient.client.module.impl;

import net.cevherclient.client.module.Option;
import net.cevherclient.client.module.TextHudModule;
import net.cevherclient.client.ui.CombatTracker;
import net.minecraft.client.Minecraft;

/** Hasar almadan art arda vurulan darbe sayısı. */
public final class ComboModule extends TextHudModule {
	private final Option.Bool hideZero = add(new Option.Bool("hide_zero", "Kombo yokken gizle", false));

	public ComboModule() {
		super("combo", "Kombo Sayacı", "KMB", false, 0.16f, 0.418f);
	}

	@Override
	protected String text(Minecraft mc, boolean preview) {
		int combo = preview ? 4 : CombatTracker.combo();
		if (combo == 0 && hideZero.value) {
			return null;
		}
		return combo == 0 ? "Kombo yok" : combo + " Kombo";
	}
}
