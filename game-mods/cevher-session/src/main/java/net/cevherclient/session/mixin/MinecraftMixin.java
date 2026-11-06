package net.cevherclient.session.mixin;

import net.cevherclient.session.CevherSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Oyuncu sunucudan ayrıldığında (ESC → Bağlantıyı Kes, sunucudan atılma, bağlantı
 * kopması) oyun ana menüye / sunucu listesine dönmek yerine düzgünce kapanır.
 *
 * Atılma veya bağlantı hatasında önce sebebin yazdığı ekran gösterilir; oyuncu
 * "Geri" dediğinde oyun kapanır. Kapanış Minecraft#stop() ile yapılır, yani ayarlar
 * normal çıkıştaki gibi kaydedilir.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Shadow
	public abstract void stop();

	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void cevher$exitOnLeave(@Nullable Screen screen, CallbackInfo ci) {
		if (!CevherSession.EXIT_ON_LEAVE) {
			return;
		}
		if (screen instanceof ConnectScreen) {
			CevherSession.connectionAttempted = true;
			return;
		}
		if (CevherSession.connectionAttempted
				&& (screen instanceof TitleScreen || screen instanceof JoinMultiplayerScreen)) {
			ci.cancel();
			this.stop();
		}
	}
}
