package net.cevherclient.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.cevherclient.client.module.impl.HeldItemModule;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** El Eşyası Boyutu: birinci şahısta eldeki eşya çizilirken boyut/konum dönüşümü uygulanır. */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	@WrapOperation(method = "renderArmWithItem", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;"
			+ "Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"))
	private void cevher$heldItem(ItemInHandRenderer renderer, LivingEntity entity, ItemStack stack, ItemDisplayContext context,
		PoseStack pose, SubmitNodeCollector collector, int light, Operation<Void> original) {
		boolean pushed = HeldItemModule.apply(stack, pose);
		original.call(renderer, entity, stack, context, pose, collector, light);
		if (pushed) {
			pose.popPose();
		}
	}
}
