package net.cevherclient.client.module.impl;

import java.util.Locale;
import java.util.regex.Pattern;
import net.cevherclient.client.module.Module;
import net.cevherclient.client.module.ModuleManager;
import net.cevherclient.client.module.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Oyun bitince (ve isteğe bağlı olarak öldürünce ya da ölünce) sohbete "gg" yazar. Oyun sonu, sunucunun
 * sohbet mesajlarındaki yaygın ifadelerden anlaşılır. Art arda göndermemek için iki gg arasında en az
 * 10 saniye beklenir.
 */
public final class AutoGgModule extends Module {
	private static final Pattern GAME_END = Pattern.compile(
		"(1st killer|winner[s]?:|you won|victory!|game over|kazandın|kazanan|oyun bitti|zafer!|#1 victory|you win)",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern KILL_VERBS = Pattern.compile(
		"(was slain by|was killed by|was shot by|was knocked|tarafından öldürüldü|tarafından vuruldu|öldürdü)",
		Pattern.CASE_INSENSITIVE);
	private static final String[] MESSAGES = {"gg", "GG", "gg wp", "iyi oyundu"};
	private static final long COOLDOWN_MS = 10_000;

	private final Option.Choice message = add(new Option.Choice("message", "Mesaj", 0, MESSAGES));
	private final Option.Range delay = add(new Option.Range("delay", "Gecikme (sn)", 1f, 0f, 5f, 0.5f));
	private final Option.Bool onKill = add(new Option.Bool("on_kill", "Öldürünce", false));
	private final Option.Bool onDeath = add(new Option.Bool("on_death", "Ölünce", false));

	private long lastSent;
	private long sendAt = -1;
	private boolean wasDead;

	public AutoGgModule() {
		super("auto_gg", "Otomatik GG", "GG", Category.MISC, false);
	}

	/** Sunucudan gelen her sohbet/sistem mesajı için (CevherClientMod) çağrılır. */
	public static void onMessage(Component text) {
		AutoGgModule module = ModuleManager.get(AutoGgModule.class);
		Minecraft mc = Minecraft.getInstance();
		if (module == null || !module.isEnabled() || mc.player == null) {
			return;
		}
		String plain = text.getString();
		if (GAME_END.matcher(plain).find()) {
			module.schedule();
			return;
		}
		// "<kurban> was slain by <biz>": adımız mesajın başında değil, sonradan geçiyorsa öldüren biziz.
		String name = mc.player.getName().getString();
		int at = plain.toLowerCase(Locale.ROOT).indexOf(name.toLowerCase(Locale.ROOT));
		if (module.onKill.value && at > 0 && KILL_VERBS.matcher(plain).find()) {
			module.schedule();
		}
	}

	private void schedule() {
		long now = Util.getMillis();
		if (sendAt < 0 && now - lastSent >= COOLDOWN_MS) {
			sendAt = now + (long) (delay.value * 1000);
		}
	}

	@Override
	public void onTick(Minecraft mc) {
		boolean dead = mc.player.isDeadOrDying();
		if (dead && !wasDead && onDeath.value) {
			schedule();
		}
		wasDead = dead;
		if (sendAt >= 0 && Util.getMillis() >= sendAt) {
			sendAt = -1;
			lastSent = Util.getMillis();
			if (mc.getConnection() != null) {
				mc.getConnection().sendChat(message.name());
			}
		}
	}
}
