package net.cevherclient.client.mixin;

import net.cevherclient.client.module.impl.TimeChangerModule;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zaman Değiştirici: yalnızca istemci dünyasının gördüğü gün zamanı değişir (gökyüzü ve ışık bunu kullanır). */
@Mixin(Level.class)
public abstract class LevelMixin {
	@Inject(method = "getDayTime", at = @At("RETURN"), cancellable = true)
	private void cevher$timeChanger(CallbackInfoReturnable<Long> cir) {
		if ((Object) this instanceof ClientLevel) {
			cir.setReturnValue(TimeChangerModule.apply(cir.getReturnValueJ()));
		}
	}
}
