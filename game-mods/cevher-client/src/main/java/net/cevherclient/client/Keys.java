package net.cevherclient.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** Modun kayıtlı tuş atamaları. Oyuncu bunları Seçenekler → Kontroller'den değiştirebilir. */
public final class Keys {
	public static KeyMapping MENU;
	public static KeyMapping ZOOM;
	public static KeyMapping FREELOOK;

	private Keys() {
	}

	static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(CevherClientMod.MOD_ID, "main"));
		MENU = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.cevher_client.menu", InputConstants.Type.KEYSYM, InputConstants.KEY_RSHIFT, category));
		ZOOM = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.cevher_client.zoom", InputConstants.Type.KEYSYM, InputConstants.KEY_C, category));
		FREELOOK = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.cevher_client.freelook", InputConstants.Type.KEYSYM, InputConstants.KEY_LALT, category));
	}
}
