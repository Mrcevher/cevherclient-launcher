package net.cevherclient.client.module.impl;

import java.util.concurrent.ThreadLocalRandom;
import net.cevherclient.client.module.Module;
import net.cevherclient.client.module.ModuleManager;
import net.cevherclient.client.module.Option;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;

/**
 * Yoğun parçacıkları azaltır: patlama, totem, kritik ve büyülü vuruş. Her tür için yüzde verilir; parçacık
 * oluşturulurken (ParticleEngineMixin) o oranda rastgele atlanır. %100 oyundaki gibidir.
 */
public final class ParticlesModule extends Module {
	private final Option.Range explosion = add(new Option.Range("explosion", "Patlama (%)", 100f, 0f, 100f, 5f));
	private final Option.Range totem = add(new Option.Range("totem", "Totem (%)", 100f, 0f, 100f, 5f));
	private final Option.Range crit = add(new Option.Range("crit", "Kritik vuruş (%)", 100f, 0f, 100f, 5f));
	private final Option.Range enchant = add(new Option.Range("enchant", "Büyülü vuruş (%)", 100f, 0f, 100f, 5f));

	public ParticlesModule() {
		super("particles", "Parçacık Ayarları", "PA", Category.VISUAL, false);
	}

	/** Parçacık atlansın mı. */
	public static boolean skip(ParticleOptions options) {
		ParticlesModule module = ModuleManager.get(ParticlesModule.class);
		if (module == null || !module.isEnabled()) {
			return false;
		}
		ParticleType<?> type = options.getType();
		Option.Range percent;
		if (type == ParticleTypes.EXPLOSION || type == ParticleTypes.EXPLOSION_EMITTER) {
			percent = module.explosion;
		} else if (type == ParticleTypes.TOTEM_OF_UNDYING) {
			percent = module.totem;
		} else if (type == ParticleTypes.CRIT) {
			percent = module.crit;
		} else if (type == ParticleTypes.ENCHANTED_HIT) {
			percent = module.enchant;
		} else {
			return false;
		}
		return ThreadLocalRandom.current().nextFloat() * 100f >= percent.value;
	}
}
