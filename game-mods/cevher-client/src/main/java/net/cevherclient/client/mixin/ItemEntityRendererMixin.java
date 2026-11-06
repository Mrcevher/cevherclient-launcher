package net.cevherclient.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.cevherclient.client.module.impl.ItemPhysicModule;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Eşya Fiziği: oyun yerdeki eşyayı havada zıplatıp döndürür. Modül açıkken zıplama kaldırılır, dönüş sabit bir
 * yöne çevrilir; düz eşyalar (kılıç, elma…) 90° yatırılıp yere serilir, bloklar zemine oturur.
 */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
	private static final String SUBMIT = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;"
		+ "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;"
		+ "Lnet/minecraft/client/renderer/state/CameraRenderState;)V";

	/** Oyunun "derinliği bundan az olan model düzdür" eşiği. */
	private static boolean flat(AABB box) {
		return box.getZsize() <= 0.0625;
	}

	@WrapOperation(method = SUBMIT, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0))
	private void cevher$lieDown(PoseStack pose, float x, float y, float z, Operation<Void> original,
		@Local(argsOnly = true) ItemEntityRenderState state) {
		if (!ItemPhysicModule.isActive()) {
			original.call(pose, x, y, z);
			return;
		}
		AABB box = state.item.getModelBoundingBox();
		float height = flat(box) ? (float) (box.getZsize() / 2) + 0.01f : (float) -box.minY;
		original.call(pose, x, height, z);
	}

	@WrapOperation(method = SUBMIT, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V", ordinal = 0))
	private void cevher$rotate(PoseStack pose, Quaternionfc rotation, Operation<Void> original,
		@Local(argsOnly = true) ItemEntityRenderState state) {
		if (!ItemPhysicModule.isActive()) {
			original.call(pose, rotation);
			return;
		}
		original.call(pose, Axis.YP.rotation(ItemPhysicModule.yaw(state.bobOffset)));
		if (flat(state.item.getModelBoundingBox())) {
			original.call(pose, Axis.XP.rotationDegrees(90f));
		}
	}
}
