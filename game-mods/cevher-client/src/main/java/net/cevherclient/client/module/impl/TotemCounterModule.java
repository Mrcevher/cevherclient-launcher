package net.cevherclient.client.module.impl;

import net.cevherclient.client.module.HudModule;
import net.cevherclient.client.module.ModuleManager;
import net.cevherclient.client.module.Option;
import net.cevherclient.client.ui.TotemPops;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Envanterdeki ölümsüzlük totemi sayısı (ya da kendi patlattığın totem sayısı). Açıkken diğer oyuncuların
 * patlattığı totem sayısı isim etiketlerinin yanına da yazılabilir.
 */
public final class TotemCounterModule extends HudModule {
	private final Option.Choice mode = add(new Option.Choice("mode", "Gösterilen", 0, "Envanterdeki", "Patlatılan"));
	private final Option.Bool colors = add(new Option.Bool("colors", "Renkli sayı", true));
	private final Option.Bool nametag = add(new Option.Bool("nametag", "İsimde patlatma", true));
	private ItemStack icon;

	public TotemCounterModule() {
		super("totem_counter", "Totem Sayacı", "TOT", false, 0.40f, 0.80f);
	}

	/** İsim etiketlerine patlatma sayısı eklenecek mi (EntityRendererMixin sorar). */
	public static boolean showOnNametag() {
		TotemCounterModule module = ModuleManager.get(TotemCounterModule.class);
		return module != null && module.isEnabled() && module.nametag.value;
	}

	private int count(Minecraft mc, boolean preview) {
		if (preview || mc.player == null) {
			return 3;
		}
		if (mode.index == 1) {
			return TotemPops.pops(mc.player.getUUID());
		}
		int total = 0;
		var inventory = mc.player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(Items.TOTEM_OF_UNDYING)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private String text(Minecraft mc, boolean preview) {
		int count = count(mc, preview);
		return mode.index == 1 ? count + " patlatma" : "x" + count;
	}

	@Override
	public int width(Minecraft mc, boolean preview) {
		return 20 + mc.font.width(text(mc, preview));
	}

	@Override
	public int height(Minecraft mc, boolean preview) {
		return 16;
	}

	@Override
	public void draw(GuiGraphics graphics, Minecraft mc, boolean preview) {
		if (icon == null) {
			icon = new ItemStack(Items.TOTEM_OF_UNDYING);
		}
		graphics.renderItem(icon, 0, 0);
		int count = count(mc, preview);
		int color = 0xFFFFFFFF;
		if (colors.value && mode.index == 0) {
			color = count == 0 ? 0xFFFF5555 : count <= 2 ? 0xFFFFAA33 : 0xFF55FF77;
		}
		graphics.drawString(mc.font, text(mc, preview), 20, 4, color, true);
	}
}
