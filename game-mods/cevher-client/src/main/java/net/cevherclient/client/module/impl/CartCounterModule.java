package net.cevherclient.client.module.impl;

import net.cevherclient.client.module.HudModule;
import net.cevherclient.client.module.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Envanterdeki TNT'li vagon sayısı; azaldıkça turuncu, sonra kırmızı yazar (kristal/vagon PvP'si için). */
public final class CartCounterModule extends HudModule {
	private final Option.Range red = add(new Option.Range("red", "Kırmızı eşik", 2f, 0f, 16f, 1f));
	private final Option.Range orange = add(new Option.Range("orange", "Turuncu eşik", 5f, 0f, 32f, 1f));
	private final Option.Bool creative = add(new Option.Bool("creative", "Yaratıcıda göster", false));
	private ItemStack icon;

	public CartCounterModule() {
		super("cart_counter", "TNT Vagon Sayacı", "VGN", false, 0.53f, 0.80f);
	}

	private static int count(Minecraft mc, boolean preview) {
		if (preview || mc.player == null) {
			return 12;
		}
		int total = 0;
		var inventory = mc.player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(Items.TNT_MINECART)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private boolean hidden(Minecraft mc, boolean preview) {
		return !preview && mc.player != null && mc.player.isCreative() && !creative.value;
	}

	@Override
	public int width(Minecraft mc, boolean preview) {
		return 20 + mc.font.width("x" + count(mc, preview));
	}

	@Override
	public int height(Minecraft mc, boolean preview) {
		return 16;
	}

	@Override
	public void draw(GuiGraphics graphics, Minecraft mc, boolean preview) {
		if (hidden(mc, preview)) {
			return;
		}
		if (icon == null) {
			icon = new ItemStack(Items.TNT_MINECART);
		}
		int count = count(mc, preview);
		int color = count <= red.value ? 0xFFFF5555 : count <= orange.value ? 0xFFFFAA33 : 0xFF55FF77;
		graphics.renderItem(icon, 0, 0);
		graphics.drawString(mc.font, "x" + count, 20, 4, color, true);
	}
}
