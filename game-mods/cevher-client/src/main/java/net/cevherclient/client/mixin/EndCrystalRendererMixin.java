package net.cevherclient.client.mixin;

import net.cevherclient.client.module.impl.CustomCrystalModule;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Özel Kristal: boyut çizimdeki ölçeğe, dönüş hızı ve taban görünürlüğü çizim durumuna uygulanır. */
@Mixin(EndCrystalRenderer.class)
public abstract class EndCrystalRendererMixin {
	@ModifyArgs(method = "submit(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;"
		+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V", ordinal = 0))
	private void cevher$size(Args args) {
		for (int i = 0; i < 3; i++) {
			args.set(i, CustomCrystalModule.scale(args.<Float>get(i)));
		}
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;"
		+ "Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;F)V", at = @At("TAIL"))
	private void cevher$spin(EndCrystal crystal, EndCrystalRenderState state, float partialTick, CallbackInfo ci) {
		state.ageInTicks = CustomCrystalModule.age(state.ageInTicks);
		state.showsBottom = CustomCrystalModule.showsBottom(state.showsBottom);
	}
}
