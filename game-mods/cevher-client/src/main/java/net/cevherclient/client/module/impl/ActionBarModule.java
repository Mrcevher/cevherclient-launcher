package net.cevherclient.client.module.impl;

import net.cevherclient.client.mixin.GuiAccessor;
import net.cevherclient.client.module.HudModule;
import net.cevherclient.client.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/** Eylem çubuğu mesajını (hotbar üstündeki yazı) istenen yere taşır; açıkken oyunun kendi mesajı çizilmez. */
public final class ActionBarModule extends HudModule {
	private static final Component SAMPLE = Component.literal("Eylem çubuğu mesajı");

	public ActionBarModule() {
		super("action_bar", "Eylem Çubuğu", "EÇ", false, 0.38f, 0.70f);
	}

	/** Oyunun eylem çubuğu gizlensin mi (CevherClientMod'daki HUD değişimi sorar). */
	public static boolean replacesVanilla() {
		ActionBarModule module = ModuleManager.get(ActionBarModule.class);
		return module != null && module.isEnabled();
	}

	private static Component message(Minecraft mc, boolean preview) {
		GuiAccessor gui = (GuiAccessor) mc.gui;
		if (gui.cevher$overlayMessageTime() > 0 && gui.cevher$overlayMessage() != null) {
			return gui.cevher$overlayMessage();
		}
		return preview ? SAMPLE : null;
	}

	@Override
	public int width(Minecraft mc, boolean preview) {
		Component message = message(mc, preview);
		return mc.font.width(message == null ? SAMPLE : message) + 4;
	}

	@Override
	public int height(Minecraft mc, boolean preview) {
		return mc.font.lineHeight + 4;
	}

	@Override
	public void draw(GuiGraphics graphics, Minecraft mc, boolean preview) {
		Component message = message(mc, preview);
		if (message == null) {
			return;
		}
		GuiAccessor gui = (GuiAccessor) mc.gui;
		int time = gui.cevher$overlayMessageTime();
		int alpha = preview && time <= 0 ? 255 : Mth.clamp(time * 255 / 20, 0, 255);
		if (alpha <= 8) {
			return;
		}
		int color = gui.cevher$animateOverlayMessageColor() ? Mth.hsvToArgb(time / 50f, 0.7f, 0.6f, alpha) : ARGB.white(alpha);
		int w = mc.font.width(message);
		graphics.drawStringWithBackdrop(mc.font, message, 2, 2, w, color);
	}
}
