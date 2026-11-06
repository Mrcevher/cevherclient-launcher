package net.cevherclient.client.module.impl;

import net.cevherclient.client.mixin.BossHealthOverlayAccessor;
import net.cevherclient.client.module.HudModule;
import net.cevherclient.client.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Boss can çubuklarını istenen yere taşır. Çubukları oyunun kendisi çizer: oyun onları ekranın ortasına ve
 * y=12'ye koyar, burada çizimden önce koordinatlar modülün konumuna kaydırılır.
 */
public final class BossBarModule extends HudModule {
	private static final int BAR_W = 182;
	private static final int ROW = 19;

	public BossBarModule() {
		super("boss_bar", "Boss Çubuğu", "BOS", false, 0.36f, 0.012f);
	}

	public static boolean replacesVanilla() {
		BossBarModule module = ModuleManager.get(BossBarModule.class);
		return module != null && module.isEnabled();
	}

	private static int count(Minecraft mc) {
		return ((BossHealthOverlayAccessor) mc.gui.getBossOverlay()).cevher$events().size();
	}

	@Override
	public int width(Minecraft mc, boolean preview) {
		return BAR_W;
	}

	@Override
	public int height(Minecraft mc, boolean preview) {
		return Math.max(1, count(mc)) * ROW - 2;
	}

	@Override
	public void draw(GuiGraphics graphics, Minecraft mc, boolean preview) {
		if (count(mc) == 0) {
			if (preview) {
				graphics.fill(0, 9, BAR_W, 14, 0xFFAA2222);
				graphics.drawCenteredString(mc.font, "Boss Çubuğu", BAR_W / 2, 0, 0xFFFFFFFF);
			}
			return;
		}
		var pose = graphics.pose();
		pose.pushMatrix();
		// Oyun ilk çubuğun adını y=3'e, çubuğu x = genişlik/2 - 91'e çizer.
		pose.translate(-(graphics.guiWidth() / 2f - 91), -3);
		mc.gui.getBossOverlay().render(graphics);
		pose.popMatrix();
	}
}
