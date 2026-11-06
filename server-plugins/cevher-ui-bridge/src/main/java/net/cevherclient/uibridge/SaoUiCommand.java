package net.cevherclient.uibridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** /saoui on|off [oyuncu], /saoui liste, /saoui reload */
final class SaoUiCommand implements CommandExecutor, TabCompleter {
	private static final List<String> SUBCOMMANDS = List.of("on", "off", "liste", "reload");

	private final CevherUiBridgePlugin plugin;

	SaoUiCommand(CevherUiBridgePlugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length == 0) {
			return false;
		}
		switch (args[0].toLowerCase(Locale.ROOT)) {
			case "on" -> toggle(sender, args, true);
			case "off" -> toggle(sender, args, false);
			case "liste" -> list(sender);
			case "reload" -> {
				plugin.loadSettings();
				reply(sender, "Ayarlar yeniden yüklendi.", NamedTextColor.GREEN);
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	private void toggle(CommandSender sender, String[] args, boolean enabled) {
		Player target;
		if (args.length >= 2) {
			target = Bukkit.getPlayerExact(args[1]);
			if (target == null) {
				reply(sender, args[1] + " çevrimiçi değil.", NamedTextColor.RED);
				return;
			}
		} else if (sender instanceof Player self) {
			target = self;
		} else {
			reply(sender, "Konsoldan oyuncu adı ver: /saoui " + args[0] + " <oyuncu>", NamedTextColor.RED);
			return;
		}

		boolean hasClient = plugin.setSao(target, enabled);
		if (hasClient) {
			reply(sender, target.getName() + " için SAO arayüzü " + (enabled ? "açıldı." : "kapatıldı."), NamedTextColor.GREEN);
		} else {
			reply(sender, target.getName() + " Cevherclient UI kullanmıyor (hello gelmedi). Durum kaydedildi; "
				+ "hello gelirse gönderilir.", NamedTextColor.YELLOW);
		}
	}

	private void list(CommandSender sender) {
		int count = 0;
		for (Player player : Bukkit.getOnlinePlayers()) {
			ClientInfo info = plugin.clientInfo(player);
			if (info == null) {
				continue;
			}
			count++;
			String state = plugin.isSaoEnabled(player) ? "SAO açık" : "SAO kapalı";
			reply(sender, "  " + player.getName() + " — mod " + info.mod() + ", protocol " + info.protocol() + ", " + state,
				NamedTextColor.AQUA);
		}
		reply(sender, count + " oyuncu Cevherclient UI kullanıyor.", NamedTextColor.GRAY);
	}

	private static void reply(CommandSender sender, String text, NamedTextColor color) {
		sender.sendMessage(Component.text("[SAO UI] ", NamedTextColor.DARK_AQUA).append(Component.text(text, color)));
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		List<String> result = new ArrayList<>();
		if (args.length == 1) {
			for (String sub : SUBCOMMANDS) {
				if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
					result.add(sub);
				}
			}
		} else if (args.length == 2 && (args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("off"))) {
			String prefix = args[1].toLowerCase(Locale.ROOT);
			for (Player player : Bukkit.getOnlinePlayers()) {
				if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
					result.add(player.getName());
				}
			}
		}
		return result;
	}
}
