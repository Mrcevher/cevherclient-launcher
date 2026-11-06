package net.cevherclient.client.module.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.cevherclient.client.module.HudModule;
import net.cevherclient.client.module.Module;
import net.cevherclient.client.module.ModuleManager;
import net.cevherclient.client.module.Option;
import net.cevherclient.client.module.Palette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Açık modüllerin adları, uzundan kısaya, kenara yaslı bir liste olarak. */
public final class ArrayListModule extends HudModule {
	private static final int ROW = 11;
	private static final List<String> SAMPLE = List.of("Serbest Bakış", "Tam Parlaklık", "Otomatik Koşu", "Zoom");

	private final Option.Choice color = add(Palette.option("color", "Renk", 10));
	private final Option.Choice align = add(new Option.Choice("align", "Hizalama", 0, "Sağa", "Sola"));
	private final Option.Bool background = add(new Option.Bool("background", "Arka plan", true));
	private final Option.Bool showHud = add(new Option.Bool("show_hud", "HUD modülleri de", false));

	public ArrayListModule() {
		super("array_list", "Modül Listesi", "LST", false, 0.86f, 0.012f);
	}

	private List<String> names(Minecraft mc, boolean preview) {
		List<String> names = new ArrayList<>();
		for (Module module : ModuleManager.all()) {
			if (module.isEnabled() && module != this && (showHud.value || !module.isHud())) {
				names.add(module.name);
			}
		}
		if (names.isEmpty() && preview) {
			names.addAll(SAMPLE);
		}
		names.sort(Comparator.comparingInt((String name) -> mc.font.width(name)).reversed());
		return names;
	}

	@Override
	public int width(Minecraft mc, boolean preview) {
		int max = 40;
		for (String name : names(mc, preview)) {
			max = Math.max(max, mc.font.width(name));
		}
		return max + 6;
	}

	@Override
	public int height(Minecraft mc, boolean preview) {
		return Math.max(1, names(mc, preview).size()) * ROW;
	}

	@Override
	public void draw(GuiGraphics graphics, Minecraft mc, boolean preview) {
		List<String> names = names(mc, preview);
		int total = width(mc, preview);
		boolean right = align.index == 0;
		for (int i = 0; i < names.size(); i++) {
			String name = names.get(i);
			int w = mc.font.width(name);
			int y = i * ROW;
			int x = right ? total - w - 4 : 4;
			int rgb = Palette.color(color, i * 0.06f);
			if (background.value) {
				graphics.fill(x - 3, y, x + w + 3, y + ROW, 0x70000000);
			}
			int barX = right ? total - 1 : 0;
			graphics.fill(barX, y, barX + 1, y + ROW, rgb);
			graphics.drawString(mc.font, name, x, y + 2, rgb, true);
		}
	}
}
