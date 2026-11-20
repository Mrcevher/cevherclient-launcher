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
 *   /cevherauth engeller              — süren geçici IP engelleri
 *   /cevherauth engelkaldir <ip>      — IP engelini kaldırır
 *   /cevherauth risk [ad]             — risk listesi ya da bir oyuncunun bulguları
 *   /cevherauth risktemizle <ad>      — oyuncunun risk kaydını siler
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
				case "engeller", "engelkaldir" -> {
					return engelKomutu(plugin, sender, args);
				}
				case "yasaklar", "yasakla", "yasakkaldir", "yasak" -> {
					return yasakKomutu(plugin, sender, args);
				}
				case "risk", "risktemizle" -> {
					return riskKomutu(plugin, sender, args);
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

	/** Risk takibi komutları (her rolde); komut bu değilse false. */
	static boolean riskKomutu(CevherAuthPlugin plugin, CommandSender sender, String[] args) {
		if (args.length == 0) {
			return false;
		}
		if (args[0].equalsIgnoreCase("risk")) {
			for (String satir : plugin.riskTakip().rapor(args.length >= 2 ? args[1] : null)) {
				sender.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(satir));
			}
			return true;
		}
		if (args[0].equalsIgnoreCase("risktemizle") && args.length >= 2) {
			sender.sendMessage(plugin.riskTakip().temizle(args[1]) ? "§a" + args[1] + " risk kaydı silindi." : "§cBu oyuncu için kayıt yok.");
			return true;
		}
		return false;
	}

	/** Kapı rolünde de kullanılan IP engeli komutları; komut bu değilse false. */
	static boolean engelKomutu(CevherAuthPlugin plugin, CommandSender sender, String[] args) {
		if (args.length == 0) {
			return false;
		}
		if (args[0].equalsIgnoreCase("engeller")) {
			long simdi = System.currentTimeMillis();
			var list = plugin.guvenlik().engeller().entrySet().stream().filter(e -> e.getValue().bitis() > simdi).toList();
			sender.sendMessage("§bCevherAuth §7— " + list.size() + " geçici IP engeli");
			for (var e : list) {
				long dk = Math.max(1, (e.getValue().bitis() - simdi + 59_999) / 60_000);
				sender.sendMessage(" §f" + e.getKey() + " §8| §e" + dk + " dk §8| §7" + e.getValue().neden());
			}
			return true;
		}
		if (args[0].equalsIgnoreCase("engelkaldir") && args.length >= 2) {
			boolean ok = plugin.guvenlik().kaldir(args[1]);
			sender.sendMessage(ok ? "§a" + args[1] + " engeli kaldırıldı." : "§cBu IP için süren engel yok.");
			return true;
		}
		return false;
	}

	/** yasaklar | yasak &lt;ad|ip|id&gt; | yasakla &lt;ad&gt; [sebep] | yasakkaldir &lt;ad|ip|id&gt; */
	static boolean yasakKomutu(CevherAuthPlugin plugin, CommandSender sender, String[] args) {
		KaliciYasak y = plugin.kaliciYasak();
		if (args.length == 0 || y == null) {
			return false;
		}
		switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
			case "yasaklar" -> {
				sender.sendMessage("§bCevherAuth §7— " + y.kayitlar().size() + " kalıcı yasak");
				for (KaliciYasak.Kayit k : y.kayitlar()) {
					sender.sendMessage(" §c" + k.ana + " §8(" + k.id + ") §7" + k.adlar.size() + " hesap, " + k.ipler.size() + " IP, "
						+ k.cihazlar.size() + " cihaz izi §8| §f" + k.sebep + " §8| " + k.yetkili);
				}
			}
			case "yasak" -> {
				KaliciYasak.Kayit k = args.length >= 2 ? y.bulAdVeyaIp(args[1]) : null;
				if (k == null) {
					sender.sendMessage("§cBöyle bir yasak yok.");
					return true;
				}
				sender.sendMessage("§c" + k.ana + " §8(" + k.id + ") §f" + k.sebep + " §7— " + k.yetkili + (k.vaka.isEmpty() ? "" : ", vaka " + k.vaka));
				sender.sendMessage(" §7Hesaplar: §f" + String.join(", ", k.adlar));
				sender.sendMessage(" §7IP'ler: §f" + String.join(", ", k.ipler));
				sender.sendMessage(" §7Cihaz izi: §f" + k.cihazlar.size() + " §7· damga: §f" + k.damgalar.size());
				for (String g : k.gecmis) {
					sender.sendMessage(" §8• §7" + g);
				}
			}
			case "yasakla" -> {
				if (args.length < 2) {
					sender.sendMessage("§cKullanım: /cevherauth yasakla <ad> [sebep]");
					return true;
				}
				org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayerExact(args[1]);
				String ip = p != null ? KaliciYasak.ipOf(p) : null;
				String sebep = args.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : "Yetkili kararı";
				KaliciYasak.Kayit k = y.yasakla(args[1], ip, null, null, sebep, sender.getName(), "", true);
				sender.sendMessage("§a" + args[1] + " kalıcı yasaklandı (" + k.id + "): " + k.adlar.size() + " hesap, " + k.ipler.size() + " IP, " + k.cihazlar.size() + " cihaz izi.");
			}
			case "yasakkaldir" -> {
				KaliciYasak.Kayit k = args.length >= 2 ? y.kaldir(args[1], true) : null;
				sender.sendMessage(k != null ? "§a" + k.ana + " yasağı kaldırıldı (" + k.adlar.size() + " hesap, " + k.ipler.size() + " IP)." : "§cBöyle bir yasak yok.");
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (args.length == 1) {
			return Stream.of("liste", "sil", "sifre", "kayit", "reload", "engeller", "engelkaldir", "risk", "risktemizle", "yasaklar", "yasak", "yasakla", "yasakkaldir").filter(s -> s.startsWith(args[0].toLowerCase())).toList();
		}
		if (args.length == 2 && (args[0].equalsIgnoreCase("sil") || args[0].equalsIgnoreCase("sifre"))) {
			return plugin.accounts().all().stream().map(a -> a.username)
				.filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).toList();
		}
		return List.of();
	}
}
