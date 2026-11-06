package net.cevherclient.client.module.impl;

import java.util.ArrayList;
import java.util.List;
import net.cevherclient.client.module.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;

/** Oyuncunun seçtiği kaynak paketlerinin adları (oyunun ve modların zorunlu paketleri hariç). */
public final class PackDisplayModule extends HudModule {
	private static final int ROW = 10;

	public PackDisplayModule() {
		super("pack_display", "Paket Göstergesi", "PKT", false, 0.005f, 0.62f);
	}

	private static List<Component> titles(Minecraft mc, boolean preview) {
		List<Component> titles = new ArrayList<>();
		for (Pack pack : mc.getResourcePackRepository().getSelectedPacks()) {
			if (!pack.isRequired()) {
				titles.add(pack.getTitle());
			}
		}
		if (titles.isEmpty()) {
			titles.add(Component.literal(preview ? "Faithful 32x" : "Kaynak paketi yok"));
		}
		return titles;
	}

	@Override
	public int width(Minecraft mc, boolean preview) {
		int max = mc.font.width("Paketler");
		for (Component title : titles(mc, preview)) {
			max = Math.max(max, mc.font.width(title));
		}
		return max + 8;
	}

	@Override
	public int height(Minecraft mc, boolean preview) {
		return (titles(mc, preview).size() + 1) * ROW + 4;
	}

	@Override
	public void draw(GuiGraphics graphics, Minecraft mc, boolean preview) {
		graphics.fill(0, 0, width(mc, preview), height(mc, preview), 0x70000000);
		graphics.drawString(mc.font, "Paketler", 4, 3, 0xFFF2A233, true);
		int y = 3 + ROW;
		for (Component title : titles(mc, preview)) {
			graphics.drawString(mc.font, title, 4, y, 0xFFFFFFFF, true);
			y += ROW;
		}
	}
}
