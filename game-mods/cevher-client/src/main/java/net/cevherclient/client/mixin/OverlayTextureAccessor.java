package net.cevherclient.client.mixin;

import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** İsabet Rengi modülü, hasar kaplamasının dokusunu yeniden boyamak için okur. */
@Mixin(OverlayTexture.class)
public interface OverlayTextureAccessor {
	@Accessor("texture")
	DynamicTexture cevher$texture();
}
