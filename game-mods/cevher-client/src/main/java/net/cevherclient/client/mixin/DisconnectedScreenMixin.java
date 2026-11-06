package net.cevherclient.client.mixin;

import net.cevherclient.client.LastServer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DisconnectedScreen.class)
public abstract class DisconnectedScreenMixin extends Screen {
	@Shadow
	@Final
	private Screen parent;

	protected DisconnectedScreenMixin(Component title) {
		super(title);
	}

	/**
	 * init() sondaki geri dönüş düğmesini (Sunucu Listesine Dön) yerleşime ekler; ondan hemen önce
	 * "Yeniden Bağlan" düğmesini ekleyerek geri dönüş düğmesini bir basamak aşağı iter.
	 */
	@Redirect(method = "init", at = @At(value = "INVOKE", ordinal = 2,
		target = "Lnet/minecraft/client/gui/layouts/LinearLayout;addChild(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;"))
	private LayoutElement cevher$addReconnect(LinearLayout layout, LayoutElement backButton) {
		if (LastServer.isKnown() && this.minecraft.allowsMultiplayer()) {
			layout.addChild(Button.builder(Component.literal("Yeniden Bağlan"), button ->
				ConnectScreen.startConnecting(this.parent, this.minecraft, LastServer.address(), LastServer.data(), false, null))
				.width(200).build());
		}
		return layout.addChild(backButton);
	}
}
