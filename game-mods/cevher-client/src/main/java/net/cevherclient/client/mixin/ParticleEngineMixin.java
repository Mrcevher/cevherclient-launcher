package net.cevherclient.client.mixin;

import net.cevherclient.client.module.impl.ParticlesModule;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Parçacık Ayarları: seçili türlerin bir kısmı hiç oluşturulmaz. */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
	@Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
	private void cevher$reduce(ParticleOptions options, double x, double y, double z, double vx, double vy, double vz,
		CallbackInfoReturnable<Particle> cir) {
		if (ParticlesModule.skip(options)) {
			cir.setReturnValue(null);
		}
	}
}
