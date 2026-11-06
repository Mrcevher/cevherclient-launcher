package net.cevherclient.client.mixin;

import net.cevherclient.client.module.impl.NoHurtCamModule;
import net.cevherclient.client.module.impl.ZoomModule;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	/** Yakınlaştırma modülü dünya görüşünü daraltır; el modeli (useFovSetting=false) etkilenmez. */
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void cevher$zoom(Camera camera, float partialTicks, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
		if (useFovSetting) {
			cir.setReturnValue(ZoomModule.applyFov(cir.getReturnValueF()));
		}
	}

	/** bobHurt'teki üçüncü döndürme kameranın hasarla yana yatmasıdır (ilk ikisi ölüm ve yön düzeltmesi). */
	@ModifyArg(method = "bobHurt", at = @At(value = "INVOKE", target = "Lcom/mojang/math/Axis;rotationDegrees(F)Lorg/joml/Quaternionf;", ordinal = 2))
	private float cevher$noHurtCam(float degrees) {
		return NoHurtCamModule.apply(degrees);
	}
}
