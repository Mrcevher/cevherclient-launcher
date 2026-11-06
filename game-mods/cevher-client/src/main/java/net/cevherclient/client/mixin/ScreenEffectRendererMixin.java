package net.cevherclient.client.mixin;

import net.cevherclient.client.module.impl.VisionModule;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Görüş Ayarları: yanarken ekranı kaplayan ateş aşağı kaydırılır. */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
	@ModifyArg(method = "renderFire", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"), index = 1)
	private static float cevher$lowerFire(float y) {
		return VisionModule.fireY(y);
	}
}
