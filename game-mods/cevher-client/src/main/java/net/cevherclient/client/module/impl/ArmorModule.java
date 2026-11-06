package net.cevherclient.client.module.impl;

import java.util.ArrayList;
import java.util.List;
import net.cevherclient.client.module.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Giyilen zırh parçalarını simgesi ve kalan dayanıklılığıyla alt alta listeler. */
public final class ArmorModule extends HudModule {
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final int ROW = 18;

	public ArmorModule() {
		super("armor", "Zırh HUD", "ZR", false, 0.005f, 0.76f);
	}

	private static List<ItemStack> stacks(Minecraft mc, boolean preview) {
		List<ItemStack> result = new ArrayList<>();
		if (preview || mc.player == null) {
			result.add(new ItemStack(Items.DIAMOND_HELMET));
			result.add(new ItemStack(Items.DIAMOND_CHESTPLATE));
			result.add(new ItemStack(Items.DIAMOND_LEGGINGS));
			result.add(new ItemStack(Items.DIAMOND_BOOTS));
			return result;
		}
		for (EquipmentSlot slot : SLOTS) {
			ItemStack stack = mc.player.getItemBySlot(slot);
			if (!stack.isEmpty()) {
				result.add(stack);
			}
		}
		return result;
	}

	private static String durability(ItemStack stack) {
		return stack.isDamageableItem() ? Integer.toString(stack.getMaxDamage() - stack.getDamageValue()) : "";
	}

	@Override
	public int width(Minecraft mc, boolean preview) {
		int textWidth = 0;
		for (ItemStack stack : stacks(mc, preview)) {
			textWidth = Math.max(textWidth, mc.font.width(durability(stack)));
		}
		return 16 + 4 + Math.max(textWidth, 20);
	}

	@Override
	public int height(Minecraft mc, boolean preview) {
		return Math.max(1, stacks(mc, preview).size()) * ROW;
	}

	@Override
	public void draw(GuiGraphics graphics, Minecraft mc, boolean preview) {
		int y = 0;
		for (ItemStack stack : stacks(mc, preview)) {
			graphics.renderItem(stack, 0, y);
			String text = durability(stack);
			if (!text.isEmpty()) {
				float ratio = 1f - (float) stack.getDamageValue() / stack.getMaxDamage();
				int color = ratio > 0.5f ? 0xFFFFFFFF : ratio > 0.2f ? 0xFFFFD84D : 0xFFFF5555;
				graphics.drawString(mc.font, text, 20, y + (16 - mc.font.lineHeight) / 2 + 1, color, true);
			}
			y += ROW;
		}
	}
}
