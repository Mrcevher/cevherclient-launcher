package net.cevherclient.client.module.impl;

import net.cevherclient.client.module.Module;
import net.cevherclient.client.module.ModuleManager;
import net.cevherclient.client.module.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * Oyuncuların giydiği zırhı (ve elytrayı) gizler ki deri görünsün. Yalnızca görünüş; zırh yine korur.
 * Kendine ve/veya diğer oyunculara uygulanabilir; zombi gibi diğer canlılar etkilenmez.
 */
public final class HideArmorModule extends Module {
	private final Option.Bool self = add(new Option.Bool("self", "Kendimde", true));
	private final Option.Bool others = add(new Option.Bool("others", "Diğer oyuncularda", false));
	private final Option.Bool helmet = add(new Option.Bool("helmet", "Kask", true));
	private final Option.Bool chest = add(new Option.Bool("chest", "Göğüslük", true));
	private final Option.Bool legs = add(new Option.Bool("legs", "Pantolon", true));
	private final Option.Bool feet = add(new Option.Bool("feet", "Bot", true));
	private final Option.Bool elytra = add(new Option.Bool("elytra", "Elytra", false));

	public HideArmorModule() {
		super("hide_armor", "Zırhı Gizle", "ZG", Category.VISUAL, false);
	}

	private static HideArmorModule forState(HumanoidRenderState state) {
		HideArmorModule module = ModuleManager.get(HideArmorModule.class);
		if (module == null || !module.isEnabled() || !(state instanceof AvatarRenderState avatar)) {
			return null;
		}
		Minecraft mc = Minecraft.getInstance();
		boolean isSelf = mc.player != null && avatar.id == mc.player.getId();
		return (isSelf ? module.self.value : module.others.value) ? module : null;
	}

	public static boolean hides(HumanoidRenderState state, EquipmentSlot slot) {
		HideArmorModule module = forState(state);
		if (module == null) {
			return false;
		}
		return switch (slot) {
			case HEAD -> module.helmet.value;
			case CHEST -> module.chest.value;
			case LEGS -> module.legs.value;
			case FEET -> module.feet.value;
			default -> false;
		};
	}

	public static boolean hidesElytra(HumanoidRenderState state) {
		HideArmorModule module = forState(state);
		return module != null && module.elytra.value;
	}
}
