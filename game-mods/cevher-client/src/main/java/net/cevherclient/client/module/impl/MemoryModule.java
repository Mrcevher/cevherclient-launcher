package net.cevherclient.client.module.impl;

import net.cevherclient.client.module.TextHudModule;
import net.minecraft.client.Minecraft;

public final class MemoryModule extends TextHudModule {
	public MemoryModule() {
		super("memory", "Bellek HUD", "RAM", false, 0.005f, 0.36f);
	}

	@Override
	protected String text(Minecraft mc, boolean preview) {
		Runtime runtime = Runtime.getRuntime();
		long used = (runtime.totalMemory() - runtime.freeMemory()) >> 20;
		long max = runtime.maxMemory() >> 20;
		return "RAM: " + used + "/" + max + " MB";
	}
}
