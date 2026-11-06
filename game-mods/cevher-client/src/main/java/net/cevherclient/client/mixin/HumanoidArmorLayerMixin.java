package net.cevherclient.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.cevherclient.client.module.impl.HideArmorModule;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Zırhı Gizle: seçili zırh parçaları oyuncularda çizilmez. */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {
	@Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true)
	private void cevher$hideArmor(PoseStack pose, SubmitNodeCollector collector, ItemStack stack, EquipmentSlot slot, int light,
		HumanoidRenderState state, CallbackInfo ci) {
		if (HideArmorModule.hides(state, slot)) {
			ci.cancel();
		}
	}
}
