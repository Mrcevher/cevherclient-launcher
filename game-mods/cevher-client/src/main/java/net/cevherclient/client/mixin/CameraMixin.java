package net.cevherclient.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.cevherclient.client.module.impl.FreeLookModule;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Serbest bakış açıkken kamera, karakterin değil serbest bakışın yönünü kullanır. */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@WrapOperation(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
	private float cevher$freeLookYaw(Entity entity, float partialTick, Operation<Float> original) {
		return FreeLookModule.isActive() && entity == Minecraft.getInstance().player ? FreeLookModule.yaw() : original.call(entity, partialTick);
	}

	@WrapOperation(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
	private float cevher$freeLookPitch(Entity entity, float partialTick, Operation<Float> original) {
		return FreeLookModule.isActive() && entity == Minecraft.getInstance().player ? FreeLookModule.pitch() : original.call(entity, partialTick);
	}
}
