package net.cevherclient.client.ui;

import net.cevherclient.client.module.HudModule;
import net.cevherclient.client.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Açık HUD modüllerini kayıtlı konum ve boyutlarında çizer. */
public final class HudRenderer {
	private HudRenderer() {
	}

	/** Ekran koordinatında (GUI pikseli) ölçeklenmiş sınırlar: x, y, genişlik, yükseklik. */
	public record Bounds(int x, int y, int w, int h) {
		public boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	public static void render(GuiGraphics graphics, Minecraft mc) {
		ClickTracker.poll(mc);
		for (HudModule module : ModuleManager.enabledHud()) {
			renderOne(graphics, mc, module, false);
		}
	}

	public static Bounds bounds(Minecraft mc, HudModule module, boolean preview) {
		float scale = module.scale.value;
		int w = Math.max(1, (int) Math.ceil(module.width(mc, preview) * scale));
		int h = Math.max(1, (int) Math.ceil(module.height(mc, preview) * scale));
		int sw = mc.getWindow().getGuiScaledWidth();
		int sh = mc.getWindow().getGuiScaledHeight();
		int x = Math.max(0, Math.min(Math.round(module.xFraction() * sw), sw - w));
		int y = Math.max(0, Math.min(Math.round(module.yFraction() * sh), sh - h));
		return new Bounds(x, y, w, h);
	}

	public static void renderOne(GuiGraphics graphics, Minecraft mc, HudModule module, boolean preview) {
		Bounds b = bounds(mc, module, preview);
		float scale = module.scale.value;
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(b.x(), b.y());
		pose.scale(scale, scale);
		module.draw(graphics, mc, preview);
		pose.popMatrix();
	}
}
