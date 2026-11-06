package net.cevherclient.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.cevherclient.client.module.impl.FreeLookModule;
import net.cevherclient.client.module.impl.ZoomModule;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	/**
	 * Fare hareketi karaktere gitmeden önce: yakınlaşmışken hassasiyet düşürülür, serbest bakış açıksa hareket
	 * karakter yerine kameraya verilir.
	 */
	@WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
	private void cevher$turn(LocalPlayer player, double dx, double dy, Operation<Void> original) {
		double divisor = ZoomModule.sensitivityDivisor();
		dx /= divisor;
		dy /= divisor;
		if (!FreeLookModule.turn(dx, dy)) {
			original.call(player, dx, dy);
		}
	}

	/** Yakınlaşmışken fare tekerleği hotbar yerine yakınlığı değiştirir. */
	@Inject(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/ScrollWheelHandler;onMouseScroll(DD)Lorg/joml/Vector2i;"), cancellable = true)
	private void cevher$zoomScroll(long window, double dx, double dy, CallbackInfo ci) {
		if (ZoomModule.onScroll(dy)) {
			ci.cancel();
		}
	}
}
