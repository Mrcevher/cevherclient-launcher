package net.cevherclient.uibridge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.entity.Player;

/**
 * BetterHud uyumu: SAO arayüzü açık olan modlu oyuncularda sunucunun BetterHud HUD'u gizlenir, HP
 * panelini ve hotbar'ı mod çizer. Modsuz oyuncular etkilenmez.
 *
 * Komutlar config'ten gelir ve konsoldan çalışır. Gizlenen oyuncular dosyaya yazılır; oyuncu çıkınca
 * HUD hemen geri verilir, sunucu çökse bile bir sonraki girişte geri verilir.
 */
final class BetterHudSync {
	/** BetterHud oyuncuyu girişte yükleyene kadar beklenecek süre (tick). */
	private static final long COMMAND_DELAY_TICKS = 20;

	private final CevherUiBridgePlugin plugin;
	private final Path file;
	private final Set<UUID> hidden = ConcurrentHashMap.newKeySet();

	BetterHudSync(CevherUiBridgePlugin plugin) {
		this.plugin = plugin;
		this.file = plugin.getDataFolder().toPath().resolve("betterhud-hidden.txt");
	}

	void load() {
		hidden.clear();
		if (!Files.exists(file)) {
			return;
		}
		try {
			for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
				try {
					hidden.add(UUID.fromString(line.trim()));
				} catch (IllegalArgumentException ignored) {
					// Bozuk satır: yok say.
				}
			}
		} catch (IOException e) {
			plugin.getLogger().log(Level.WARNING, "betterhud-hidden.txt okunamadı", e);
		}
	}

	/** Oyuncunun HUD'unu istenen duruma bir süre sonra getirir (BetterHud'un oyuncuyu yüklemesi beklenir). */
	void schedule(Player player) {
		plugin.getServer().getScheduler().runTaskLater(plugin, () -> reconcile(player), COMMAND_DELAY_TICKS);
	}

	/** Çıkışta ve eklenti kapanırken: HUD'u hemen geri ver. */
	void restore(Player player) {
		if (hidden.remove(player.getUniqueId())) {
			if (active()) {
				run("betterhud.show-commands", player);
			}
			save();
		}
	}

	private void reconcile(Player player) {
		if (!active() || !player.isOnline()) {
			return;
		}
		boolean shouldHide = plugin.drawsOwnHud(player);
		boolean isHidden = hidden.contains(player.getUniqueId());
		if (shouldHide && !isHidden) {
			run("betterhud.hide-commands", player);
			hidden.add(player.getUniqueId());
			save();
		} else if (!shouldHide && isHidden) {
			run("betterhud.show-commands", player);
			hidden.remove(player.getUniqueId());
			save();
		}
	}

	private boolean active() {
		return plugin.getConfig().getBoolean("betterhud.enabled", true)
			&& plugin.getServer().getPluginManager().isPluginEnabled("BetterHud");
	}

	private void run(String key, Player player) {
		List<String> commands = plugin.getConfig().getStringList(key);
		for (String command : commands) {
			String line = command.replace("{player}", player.getName()).trim();
			if (line.startsWith("/")) {
				line = line.substring(1);
			}
			if (!line.isEmpty()) {
				plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), line);
			}
		}
	}

	private void save() {
		try {
			Files.createDirectories(file.getParent());
			List<String> lines = hidden.stream().map(UUID::toString).toList();
			Files.write(file, lines, StandardCharsets.UTF_8);
		} catch (IOException e) {
			plugin.getLogger().log(Level.WARNING, "betterhud-hidden.txt yazılamadı", e);
		}
	}
}
