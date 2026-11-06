package net.cevherclient.client.mixin;

import net.cevherclient.client.module.impl.TotemCounterModule;
import net.cevherclient.client.ui.TotemPops;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Totem Sayacı: oyuncuların isim etiketinin sonuna patlattıkları totem sayısı eklenir. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void cevher$totemPops(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
		if (state.nameTag == null || !(entity instanceof Player player) || !TotemCounterModule.showOnNametag()) {
			return;
		}
		int pops = TotemPops.pops(player.getUUID());
		if (pops > 0) {
			state.nameTag = state.nameTag.copy().append(Component.literal(" -" + pops).withStyle(ChatFormatting.GOLD));
		}
	}
}
