package net.cevherclient.client.module.impl;

import net.cevherclient.client.module.TextHudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/** Oyuncunun bulunduğu biyomun oyun dilindeki adı. */
public final class BiomeModule extends TextHudModule {
	public BiomeModule() {
		super("biome", "Biyom HUD", "BYM", false, 0.16f, 0.128f);
	}

	@Override
	protected String text(Minecraft mc, boolean preview) {
		if (preview || mc.player == null || mc.level == null) {
			return "Biyom: Ova";
		}
		String name = mc.level.getBiome(mc.player.blockPosition()).unwrapKey()
			.map(key -> Component.translatable(Util.makeDescriptionId("biome", key.identifier())).getString())
			.orElse("?");
		return "Biyom: " + name;
	}
}
