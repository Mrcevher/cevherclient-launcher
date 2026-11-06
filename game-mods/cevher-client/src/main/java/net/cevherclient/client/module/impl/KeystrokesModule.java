package net.cevherclient.client.module.impl;

import net.cevherclient.client.module.HudModule;
import net.cevherclient.client.ui.ClickTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** W A S D tuşları ile sol/sağ tık kutuları; tık kutularında saniyedeki tık sayısı yazar. */
public final class KeystrokesModule extends HudModule {
	private static final int KEY = 18;
	private static final int GAP = 2;
	private static final int MOUSE_W = (KEY * 3 + GAP) / 2;
	private static final int MOUSE_H = 20;
	private static final int BG = 0x70000000;
	private static final int BG_DOWN = 0xB0FFFFFF;

	public KeystrokesModule() {
		super("keystrokes", "Tuş Vuruşları", "WASD", true, 0.88f, 0.6f);
	}

	@Override
	public int width(Minecraft mc, boolean preview) {
		return KEY * 3 + GAP * 2;
	}

	@Override
	public int height(Minecraft mc, boolean preview) {
		return KEY * 2 + MOUSE_H + GAP * 2;
	}

	@Override
	public void draw(GuiGraphics graphics, Minecraft mc, boolean preview) {
		var o = mc.options;
		key(graphics, mc, KEY + GAP, 0, "W", o.keyUp, preview);
		key(graphics, mc, 0, KEY + GAP, "A", o.keyLeft, preview);
		key(graphics, mc, KEY + GAP, KEY + GAP, "S", o.keyDown, preview);
		key(graphics, mc, (KEY + GAP) * 2, KEY + GAP, "D", o.keyRight, preview);
		int y = (KEY + GAP) * 2;
		mouse(graphics, mc, 0, y, MOUSE_W, "LMB", 0, o.keyAttack, preview);
		int rightX = MOUSE_W + GAP;
		mouse(graphics, mc, rightX, y, width(mc, preview) - rightX, "RMB", 1, o.keyUse, preview);
	}

	private static void key(GuiGraphics g, Minecraft mc, int x, int y, String label, KeyMapping mapping, boolean preview) {
		boolean down = !preview && mapping.isDown();
		g.fill(x, y, x + KEY, y + KEY, down ? BG_DOWN : BG);
		int color = down ? 0xFF111111 : 0xFFFFFFFF;
		g.drawString(mc.font, label, x + (KEY - mc.font.width(label)) / 2, y + (KEY - mc.font.lineHeight) / 2 + 1, color, false);
	}

	private static void mouse(GuiGraphics g, Minecraft mc, int x, int y, int w, String label, int button, KeyMapping mapping, boolean preview) {
		boolean down = !preview && mapping.isDown();
		g.fill(x, y, x + w, y + MOUSE_H, down ? BG_DOWN : BG);
		int color = down ? 0xFF111111 : 0xFFFFFFFF;
		g.drawString(mc.font, label, x + (w - mc.font.width(label)) / 2, y + 3, color, false);
		String cps = (preview ? 0 : ClickTracker.cps(button)) + " CPS";
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(x + w / 2f, y + 12);
		pose.scale(0.7f, 0.7f);
		g.drawString(mc.font, cps, -mc.font.width(cps) / 2, 0, down ? 0xFF333333 : 0xFFB0B0B0, false);
		pose.popMatrix();
	}
}
