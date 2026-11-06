package net.cevherclient.client.module.impl;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import net.cevherclient.client.module.Module;
import net.cevherclient.client.module.ModuleManager;
import net.cevherclient.client.module.Option;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Sohbete gelen her mesajın başına bilgisayarın saatini ekler (ChatComponentMixin). */
public final class ChatTimestampsModule extends Module {
	private static final ChatFormatting[] COLORS = {ChatFormatting.GRAY, ChatFormatting.DARK_GRAY, ChatFormatting.GOLD, ChatFormatting.AQUA};

	private final Option.Bool seconds = add(new Option.Bool("seconds", "Saniyeyi göster", false));
	private final Option.Choice color = add(new Option.Choice("color", "Renk", 0, "Gri", "Koyu gri", "Altın", "Turkuaz"));

	public ChatTimestampsModule() {
		super("chat_timestamps", "Sohbet Saati", "SS", Category.MISC, false);
	}

	public static Component decorate(Component message) {
		ChatTimestampsModule module = ModuleManager.get(ChatTimestampsModule.class);
		if (module == null || !module.isEnabled()) {
			return message;
		}
		String time = LocalTime.now().format(DateTimeFormatter.ofPattern(module.seconds.value ? "HH:mm:ss" : "HH:mm"));
		MutableComponent prefix = Component.literal("[" + time + "] ").withStyle(COLORS[module.color.index]);
		return prefix.append(message);
	}
}
