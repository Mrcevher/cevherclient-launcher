package net.cevherclient.client.module.impl;

import java.util.ArrayList;
import java.util.List;
import net.cevherclient.client.module.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.effect.MobEffectInstance;

/** Aktif iksir etkilerini ve kalan sürelerini listeler. */
public final class PotionModule extends HudModule {
	private static final int PAD = 4;
	private static final int ROW = 11;

	public PotionModule() {
		super("potions", "İksir HUD", "İX", false, 0.005f, 0.43f);
	}

	private static List<String> lines(Minecraft mc, boolean preview) {
		List<String> result = new ArrayList<>();
		if (preview) {
			result.add("Hız II 1:30");
			result.add("Güç 0:45");
			return result;
		}
		if (mc.player != null) {
			for (MobEffectInstance effect : mc.player.getActiveEffects()) {
				String name = effect.getEffect().value().getDisplayName().getString();
				String level = effect.getAmplifier() > 0 ? " " + (effect.getAmplifier() + 1) : "";
				result.add(name + level + " " + time(effect));
			}
		}
		return result;
	}

	private static String time(MobEffectInstance effect) {
		if (effect.isInfiniteDuration()) {
			return "**:**";
		}
		int seconds = effect.getDuration() / 20;
		return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
	}

	@Override
	public int width(Minecraft mc, boolean preview) {
		int widest = 40;
		for (String line : lines(mc, preview)) {
			widest = Math.max(widest, mc.font.width(line));
		}
		return widest + PAD * 2;
	}

	@Override
	public int height(Minecraft mc, boolean preview) {
		return Math.max(1, lines(mc, preview).size()) * ROW + PAD;
	}

	@Override
	public void draw(GuiGraphics graphics, Minecraft mc, boolean preview) {
		List<String> lines = lines(mc, preview);
		if (lines.isEmpty()) {
			return;
		}
		graphics.fill(0, 0, width(mc, preview), height(mc, preview), 0x70000000);
		int y = PAD / 2 + 1;
		for (String line : lines) {
			graphics.drawString(mc.font, line, PAD, y, 0xFFFFFFFF, true);
			y += ROW;
		}
	}
}
