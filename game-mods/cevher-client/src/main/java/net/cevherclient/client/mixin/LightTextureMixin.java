package net.cevherclient.client.mixin;

import net.cevherclient.client.module.impl.FullbrightModule;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Tam Parlaklık: ışık haritası gölgelendiricisine giden değerlerden gece görüşü (4. değer) ve parlaklık
 * (7. değer) değiştirilir. Sıra updateLightTexture'daki Std140Builder.putFloat zincirindekidir.
 */
@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
	private static final String PUT_FLOAT = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;";

	@ModifyArg(method = "updateLightTexture", at = @At(value = "INVOKE", target = PUT_FLOAT, ordinal = 3))
	private float cevher$nightVision(float value) {
		return FullbrightModule.nightVision(value);
	}

	@ModifyArg(method = "updateLightTexture", at = @At(value = "INVOKE", target = PUT_FLOAT, ordinal = 6))
	private float cevher$gamma(float value) {
		return FullbrightModule.gamma(value);
	}
}
