package net.cevherclient.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.cevherclient.client.module.impl.VisionModule;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Görüş Ayarları: kafaya takılan balkabağının ekranı kaplayan dokusu çizilmez. */
@Mixin(Gui.class)
public abstract class GuiMixin {
	@WrapOperation(method = "renderCameraOverlays", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/Gui;renderTextureOverlay(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/Identifier;F)V",
		ordinal = 0))
	private void cevher$pumpkin(Gui gui, GuiGraphics graphics, Identifier texture, float alpha, Operation<Void> original) {
		if (VisionModule.hidePumpkin() && texture.getPath().contains("pumpkinblur")) {
			return;
		}
		original.call(gui, graphics, texture, alpha);
	}
}
