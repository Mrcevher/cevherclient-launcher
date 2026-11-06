package net.cevherclient.client.module.impl;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import net.cevherclient.client.module.Option;
import net.cevherclient.client.module.TextHudModule;
import net.minecraft.client.Minecraft;

public final class ClockModule extends TextHudModule {
	private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("HH:mm:ss");

	private final Option.Bool seconds = add(new Option.Bool("seconds", "Saniyeyi göster", false));

	public ClockModule() {
		super("clock", "Saat HUD", "SA", false, 0.005f, 0.244f);
	}

	@Override
	protected String text(Minecraft mc, boolean preview) {
		return LocalTime.now().format(seconds.value ? LONG : SHORT);
	}
}
