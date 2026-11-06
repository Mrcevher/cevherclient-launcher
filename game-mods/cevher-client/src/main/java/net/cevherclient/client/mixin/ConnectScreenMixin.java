package net.cevherclient.client.mixin;

import net.cevherclient.client.LastServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin {
	/** Her bağlantı denemesinde hedefi hatırlar (sunucu listesi, doğrudan bağlan, yeniden bağlan). */
	@Inject(method = "startConnecting", at = @At("HEAD"))
	private static void cevher$remember(Screen parent, Minecraft minecraft, ServerAddress address, ServerData data,
			boolean quickPlay, TransferState transferState, CallbackInfo ci) {
		LastServer.remember(address, data);
	}
}
