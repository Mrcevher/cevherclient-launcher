package net.cevherclient.client.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Tek satır metin gösteren HUD modülü: arka plan, gölge ve renk ayarları ortaktır. */
public abstract class TextHudModule extends HudModule {
	private static final int[] COLORS = {0xFFFFFFFF, 0xFF55AAFF, 0xFF55FF77, 0xFFFFAA33, 0xFFFF5555, 0xFFC27BFF, 0xFFFFE066};
	private static final int PAD_X = 4;
	private static final int PAD_Y = 3;

	protected final Option.Bool background = add(new Option.Bool("background", "Arka plan", true));
	protected final Option.Bool shadow = add(new Option.Bool("shadow", "Yazı gölgesi", true));
	protected final Option.Choice color = add(new Option.Choice("color", "Renk", 0,
		"Beyaz", "Mavi", "Yeşil", "Turuncu", "Kırmızı", "Mor", "Sarı"));

	protected TextHudModule(String id, String name, String badge, boolean enabledByDefault, float x, float y) {
		super(id, name, badge, enabledByDefault, x, y);
	}

	/**
	 * Gösterilecek metin; {@code preview} true iken oyunda olmayabilir, örnek veri döndürülmelidir.
	 * O an gösterilecek bir şey yoksa (ör. yay gerilmiyor) null döndürülür ve modül çizilmez.
	 */
	protected abstract String text(Minecraft mc, boolean preview);

	/** Metnin rengi; varsayılan olarak "Renk" ayarı. Değere göre renklenen modüller bunu değiştirir. */
	protected int textColor(Minecraft mc, boolean preview) {
		return COLORS[color.index];
	}

	@Override
	public int width(Minecraft mc, boolean preview) {
		String text = text(mc, preview);
		return mc.font.width(text == null ? "" : text) + PAD_X * 2;
	}

	@Override
	public int height(Minecraft mc, boolean preview) {
		return mc.font.lineHeight + PAD_Y * 2 - 1;
	}

	@Override
	public void draw(GuiGraphics graphics, Minecraft mc, boolean preview) {
		String text = text(mc, preview);
		if (text == null) {
			return;
		}
		if (background.value) {
			graphics.fill(0, 0, width(mc, preview), height(mc, preview), 0x70000000);
		}
		graphics.drawString(mc.font, text, PAD_X, PAD_Y, textColor(mc, preview), shadow.value);
	}
}
