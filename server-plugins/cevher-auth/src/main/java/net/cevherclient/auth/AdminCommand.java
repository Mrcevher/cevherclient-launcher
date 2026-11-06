package net.cevherclient.auth;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

/**
 * Yönetici komutları (konsol veya op):
 *   /cevherauth liste                 — kayıtlı hesaplar
 *   /cevherauth sil <ad>              — hesabı ve oturumlarını siler
 *   /cevherauth sifre <ad> <yeni>     — şifreyi değiştirir, açık oturumları kapatır
 *   /cevherauth kayit <ad> <sifre>    — hesap açar (korunan/op adları için; yalnızca konsol veya op)
 *   /cevherauth reload                — config.yml'i yeniden okur
 */
final class AdminCommand implements TabExecutor {
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

	private final CevherAuthPlugin plugin;

	AdminCommand(CevherAuthPlugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length == 0) {
			return false;
		}
		try {
			switch (args[0].toLowerCase()) {
				case "liste" -> {
					List<AccountStore.Account> all = plugin.accounts().all();
					sender.sendMessage("§bCevherAuth §7— " + all.size() + " hesap");
					for (AccountStore.Account a : all) {
						sender.sendMessage(" §f" + a.username + " §8| §7" + DATE.format(Instant.ofEpochMilli(a.createdAt)));
					}
				}
				case "sil" -> {
					if (args.length < 2) {
						return false;
					}
					boolean removed = plugin.accounts().remove(args[1]);
					plugin.sessions().revokeAll(args[1]);
					sender.sendMessage(removed ? "§a" + args[1] + " hesabı silindi." : "§cBöyle bir hesap yok.");
				}
				case "sifre" -> {
					if (args.length < 3) {
						return false;
					}
					int min = plugin.getConfig().getInt("password.min-length", 6);
					if (args[2].length() < min) {
						sender.sendMessage("§cŞifre en az " + min + " karakter olmalı.");
						return true;
					}
					boolean changed = plugin.accounts().setPassword(args[1], Passwords.hash(args[2]));
					if (changed) {
						plugin.sessions().revokeAll(args[1]);
					}
					sender.sendMessage(changed ? "§a" + args[1] + " şifresi değiştirildi; launcher'da tekrar giriş yapmalı." : "§cBöyle bir hesap yok.");
				}
				case "kayit" -> {
					if (args.length < 3) {
						return false;
					}
					if (!args[1].matches("^[A-Za-z0-9_]{3,16}$")) {
						sender.sendMessage("§cGeçersiz kullanıcı adı (3-16 harf, rakam veya _).");
						return true;
					}
					int minLen = plugin.getConfig().getInt("password.min-length", 6);
					if (args[2].length() < minLen) {
						sender.sendMessage("§cŞifre en az " + minLen + " karakter olmalı.");
						return true;
					}
					AccountStore.Account created = plugin.accounts().create(args[1], Passwords.hash(args[2]), "admin");
					sender.sendMessage(created != null ? "§a" + args[1] + " hesabı açıldı." : "§cBu ad zaten kayıtlı.");
					if (created != null && plugin.site() != null) {
						// Sitede de aynı şifreyle hesap açılır (arka planda; sonuç konsola yazılır).
						plugin.site().backfillAsync(created.username, args[2], "loopback");
					}
				}
				case "reload" -> {
					plugin.reloadSettings();
					sender.sendMessage("§aCevherAuth ayarları yeniden yüklendi (HTTP portu değiştiyse sunucuyu yeniden başlatın).");
				}
				default -> {
					return false;
				}
			}
		} catch (IOException e) {
			sender.sendMessage("§cDosyaya yazılamadı: " + e.getMessage());
		}
		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (args.length == 1) {
			return Stream.of("liste", "sil", "sifre", "kayit", "reload").filter(s -> s.startsWith(args[0].toLowerCase())).toList();
		}
		if (args.length == 2 && (args[0].equalsIgnoreCase("sil") || args[0].equalsIgnoreCase("sifre"))) {
			return plugin.accounts().all().stream().map(a -> a.username)
				.filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).toList();
		}
		return List.of();
	}
}
