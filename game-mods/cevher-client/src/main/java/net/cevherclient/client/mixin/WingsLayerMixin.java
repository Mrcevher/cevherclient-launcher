package net.cevherclient.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.cevherclient.client.module.impl.HideArmorModule;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Zırhı Gizle: istenirse elytra da çizilmez. */
@Mixin(WingsLayer.class)
public abstract class WingsLayerMixin {
	@Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I"
		+ "Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V", at = @At("HEAD"), cancellable = true)
	private void cevher$hideElytra(PoseStack pose, SubmitNodeCollector collector, int light, HumanoidRenderState state, float yRot,
		float xRot, CallbackInfo ci) {
		if (HideArmorModule.hidesElytra(state)) {
			ci.cancel();
		}
	}
}
