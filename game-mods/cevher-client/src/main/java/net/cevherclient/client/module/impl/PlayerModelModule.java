package net.cevherclient.client.module.impl;

import net.cevherclient.client.module.HudModule;
import net.cevherclient.client.module.Option;
import net.cevherclient.client.ui.HudRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/**
 * Karakterin küçük bir modeli (envanterdeki gibi). Varlık çizimi ekran koordinatı ister; modülün ekrandaki
 * sınırları {@link HudRenderer#bounds} ile hesaplanır, bu yüzden kaydırma/ölçek dönüşümü kullanılmaz.
 */
public final class PlayerModelModule extends HudModule {
	private static final int W = 40;
	private static final int H = 70;

	private final Option.Range turn = add(new Option.Range("turn", "Dönüş", 25f, -60f, 60f, 5f));

	public PlayerModelModule() {
		super("player_model", "Oyuncu Modeli", "OYN", false, 0.02f, 0.52f);
	}

	@Override
	public int width(Minecraft mc, boolean preview) {
		return W;
	}

	@Override
	public int height(Minecraft mc, boolean preview) {
		return H;
	}

	@Override
	public void draw(GuiGraphics graphics, Minecraft mc, boolean preview) {
		if (mc.player == null) {
			return;
		}
		HudRenderer.Bounds b = HudRenderer.bounds(mc, this, preview);
		int size = Math.round(30 * scale.value);
		float cx = b.x() + b.w() / 2f;
		float cy = b.y() + b.h() / 2f;
		// "Fareyi izleyen" çizimde fare konumu modelin bakış yönünü belirler; sabit bir nokta verilir.
		InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), size, 0.0625f,
			cx - turn.value, cy - 10, mc.player);
	}
}
