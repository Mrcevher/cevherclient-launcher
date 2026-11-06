package net.cevherclient.client.module.impl;

import net.cevherclient.client.module.Module;
import net.minecraft.client.Minecraft;

/** İleri tuşuna basılıyken koşma tuşunu basılı tutmaya gerek bırakmaz. */
public final class ToggleSprintModule extends Module {
	public ToggleSprintModule() {
		super("toggle_sprint", "Otomatik Koşu", "TS", Category.MOVEMENT, false);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null || mc.screen != null) {
			return;
		}
		if (mc.options.keyUp.isDown() && !mc.player.isShiftKeyDown() && !mc.player.isUsingItem()) {
			mc.options.keySprint.setDown(true);
		}
	}
}
