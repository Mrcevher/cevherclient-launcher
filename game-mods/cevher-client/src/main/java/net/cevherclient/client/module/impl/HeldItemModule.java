package net.cevherclient.client.module.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import net.cevherclient.client.module.Module;
import net.cevherclient.client.module.ModuleManager;
import net.cevherclient.client.module.Option;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Birinci şahısta elde tutulan eşyanın boyutunu türüne göre ayarlar (kılıç, balta, kalkan, totem…) ve kalkanı
 * aşağı indirebilir. ItemInHandRendererMixin eşya çizilmeden önce buradaki dönüşümü uygular.
 */
public final class HeldItemModule extends Module {
	private final Option.Range sword = add(new Option.Range("sword", "Kılıç", 1f, 0.3f, 1.5f, 0.05f));
	private final Option.Range axe = add(new Option.Range("axe", "Balta", 1f, 0.3f, 1.5f, 0.05f));
	private final Option.Range shield = add(new Option.Range("shield", "Kalkan", 1f, 0.3f, 1.5f, 0.05f));
	private final Option.Range totem = add(new Option.Range("totem", "Totem", 1f, 0.3f, 1.5f, 0.05f));
	private final Option.Range food = add(new Option.Range("golden_apple", "Altın elma", 1f, 0.3f, 1.5f, 0.05f));
	private final Option.Range bow = add(new Option.Range("bow", "Yay / arbalet", 1f, 0.3f, 1.5f, 0.05f));
	private final Option.Range other = add(new Option.Range("other", "Diğer eşyalar", 1f, 0.3f, 1.5f, 0.05f));
	private final Option.Range shieldDrop = add(new Option.Range("shield_drop", "Kalkanı indir", 0f, 0f, 0.5f, 0.05f));

	public HeldItemModule() {
		super("held_item", "El Eşyası Boyutu", "EE", Category.VISUAL, false);
	}

	/** Dönüşüm uygulandıysa true; çağıran, çizimden sonra pose'u geri almalıdır. */
	public static boolean apply(ItemStack stack, PoseStack pose) {
		HeldItemModule module = ModuleManager.get(HeldItemModule.class);
		if (module == null || !module.isEnabled() || stack.isEmpty()) {
			return false;
		}
		pose.pushPose();
		if (stack.is(Items.SHIELD) && module.shieldDrop.value > 0f) {
			pose.translate(0f, -module.shieldDrop.value, 0f);
		}
		float scale = module.scaleFor(stack);
		pose.scale(scale, scale, scale);
		return true;
	}

	private float scaleFor(ItemStack stack) {
		if (stack.is(ItemTags.SWORDS)) {
			return sword.value;
		}
		if (stack.is(ItemTags.AXES)) {
			return axe.value;
		}
		if (stack.is(Items.SHIELD)) {
			return shield.value;
		}
		if (stack.is(Items.TOTEM_OF_UNDYING)) {
			return totem.value;
		}
		if (stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
			return food.value;
		}
		if (stack.is(Items.BOW) || stack.is(Items.CROSSBOW)) {
			return bow.value;
		}
		return other.value;
	}
}
