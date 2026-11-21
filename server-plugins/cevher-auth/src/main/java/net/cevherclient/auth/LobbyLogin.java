package net.cevherclient.auth;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.scheduler.BukkitTask;

/**
 * Lobi rolündeki oyun içi giriş.
 *
 * Cevherclient ile gelen (bileti ve kanıtı doğrulanan) oyuncu doğrudan "giriş yapmış" sayılır.
 * Launcher'sız gelen oyuncu lobide bekletilir: hareket edemez, sohbet edemez, /login veya
 * /register dışında komut kullanamaz; süre dolarsa ya da çok kez yanlış şifre girerse atılır.
 * Şifreler launcher hesabıyla aynıdır (accounts.json): /register launcher hesabı açar.
 *
 * Giriş yapmış oyuncular {@link #isAuthorized} ile arka sunuculara (kapı rolü) bildirilir.
 */
final class LobbyLogin implements Listener {
	static final Set<String> LOGIN_LABELS = Set.of("login", "l", "giris", "gir");
	static final Set<String> REGISTER_LABELS = Set.of("register", "reg", "kayit", "kaydol");
	/** Konsol günlüğünden şifreyi gizlemek için (bkz. {@link PasswordLogFilter}). */
	static final Pattern SECRET_COMMAND = Pattern.compile(
		"(?i)issued server command: /(?:cevherauth:)?(?:login|l|giris|gir|register|reg|kayit|kaydol)(?:\\s|$)");

	private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
	private static final long WINDOW_MS = 10 * 60_000L;
	/** Ön-girişte bileti doğrulanan oyuncunun katılma olayına kadar geçebilecek en uzun süre. */
	private static final long PRE_AUTH_MS = 60_000L;

	/** Launcher biletiyle ön-girişte doğrulanan oyuncu (ad + IP), katılınca giriş yapmış sayılır. */
	private record PreAuth(String name, String ip, long at) {
	}

	/** Giriş yapmış ve lobide çevrim içi oyuncu. */
	record Authorized(String name, String ip, boolean viaLauncher) {
	}

	/** Henüz giriş yapmamış oyuncu. */
	private static final class Pending {
		final long deadline;
		int attempts;
		/** Şifre doğrulaması sürüyor (PBKDF2 / site isteği arka planda). */
		volatile boolean busy;

		Pending(long deadline) {
			this.deadline = deadline;
		}
	}

	private final CevherAuthPlugin plugin;
	private final Map<String, PreAuth> preAuth = new ConcurrentHashMap<>();
	private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
	/** Anahtar: küçük harfli ad. HTTP iş parçacığından okunur. */
	private final Map<String, Authorized> authorized = new ConcurrentHashMap<>();
	/**
	 * Durum izinleri (TAB / diğer eklentilerin koşulları için, plugin.yml'de varsayılanı false):
	 *   cevherauth.bekliyor        — henüz giriş yapmadı
	 *   cevherauth.bekliyor.giris  — hesabı var, /login bekleniyor
	 *   cevherauth.bekliyor.kayit  — hesabı yok, /register bekleniyor
	 *   cevherauth.kayitli         — hesabı var (giriş yapmış ya da /login bekliyor)
	 */
	private final Map<UUID, PermissionAttachment> states = new ConcurrentHashMap<>();
	private final Throttle failIp = new Throttle(WINDOW_MS);
	private final Throttle failAccount = new Throttle(WINDOW_MS);
	private final Throttle registerIp = new Throttle(WINDOW_MS);
	private BukkitTask ticker;
	private long tick;

	LobbyLogin(CevherAuthPlugin plugin) {
		this.plugin = plugin;
	}

	void start() {
		ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
		Command login = new SecretCommand("login", List.of("l", "giris", "gir"), "/login <şifre>", this::onLogin);
		Command register = new SecretCommand("register", List.of("reg", "kayit", "kaydol"), "/register <şifre> <şifre tekrar>", this::onRegister);
		plugin.getServer().getCommandMap().register("cevherauth", login);
		plugin.getServer().getCommandMap().register("cevherauth", register);
	}

	void stop() {
		if (ticker != null) {
			ticker.cancel();
		}
	}

	void cleanup() {
		failIp.cleanup();
		failAccount.cleanup();
		registerIp.cleanup();
		long now = System.currentTimeMillis();
		preAuth.values().removeIf(p -> now - p.at() > PRE_AUTH_MS);
	}

	/** Ön-giriş (asenkron): launcher bileti ve kanıtı doğrulandı. */
	void markLauncherVerified(String name, String ip) {
		preAuth.put(AccountStore.key(name), new PreAuth(name, ip, System.currentTimeMillis()));
	}

	/** Kapı rolündeki sunucunun sorusu: bu ad bu IP'den lobide giriş yapmış ve hâlâ bağlı mı? */
	boolean isAuthorized(String name, String ip) {
		Authorized a = authorized.get(AccountStore.key(name));
		return a != null && a.name().equals(name) && a.ip().equals(ip);
	}

	/** Lobide giriş yapmış oyuncu launcher biletiyle mi geldi (oyunda şifreyle değil)? */
	boolean viaLauncher(String name) {
		Authorized a = authorized.get(AccountStore.key(name));
		return a != null && a.viaLauncher();
	}

	boolean isPending(Player player) {
		return pending.containsKey(player.getUniqueId());
	}

	// ── giriş / çıkış ───────────────────────────────────────────────────────────

	@EventHandler(priority = EventPriority.LOWEST)
	public void onJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		String ip = ipOf(player);
		PreAuth pre = preAuth.remove(AccountStore.key(player.getName()));
		if (pre != null && pre.name().equals(player.getName()) && pre.ip().equals(ip)
			&& System.currentTimeMillis() - pre.at() <= PRE_AUTH_MS) {
			authorize(player, true);
			return;
		}
		long timeout = Math.max(15, plugin.getConfig().getLong("in-game-login.timeout-seconds", 90)) * 1000L;
		pending.put(player.getUniqueId(), new Pending(System.currentTimeMillis() + timeout));
		setState(player, hasAccount(player) ? "giris" : "kayit");
		// Lobi betiklerinin hoş geldin başlığı bitince giriş başlığı gösterilir.
		plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
			if (player.isOnline() && isPending(player)) {
				remind(player, true);
			}
		}, 70L);
		remind(player, false);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		pending.remove(player.getUniqueId());
		PermissionAttachment old = states.remove(player.getUniqueId());
		if (old != null) {
			player.removeAttachment(old);
		}
		Authorized a = authorized.get(AccountStore.key(player.getName()));
		if (a != null && a.name().equals(player.getName())) {
			authorized.remove(AccountStore.key(player.getName()), a);
		}
	}

	private void tick() {
		tick++;
		long now = System.currentTimeMillis();
		for (Player player : plugin.getServer().getOnlinePlayers()) {
			Pending p = pending.get(player.getUniqueId());
			if (p == null) {
				continue;
			}
			if (now >= p.deadline && !p.busy) {
				pending.remove(player.getUniqueId());
				player.kick(plugin.message("login-timeout"));
				continue;
			}
			long left = Math.max(0, (p.deadline - now) / 1000);
			player.sendActionBar(plugin.message(hasAccount(player) ? "login-actionbar" : "register-actionbar")
				.replaceText(b -> b.matchLiteral("{sure}").replacement(String.valueOf(left))));
			if (tick % 10 == 0) {
				remind(player, false);
			}
		}
	}

	private void remind(Player player, boolean title) {
		boolean registered = hasAccount(player);
		player.sendMessage(plugin.message(registered ? "login-prompt" : "register-prompt"));
		if (title) {
			player.showTitle(Title.title(
				plugin.message(registered ? "login-title" : "register-title"),
				plugin.message(registered ? "login-subtitle" : "register-subtitle"),
				Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(4), Duration.ofMillis(500))));
		}
	}

	private boolean hasAccount(Player player) {
		return plugin.accounts().get(player.getName()) != null;
	}

	/** Durum izinlerini ayarlar: "giris", "kayit" (bekliyor) ya da "tamam" (giriş yaptı). */
	private void setState(Player player, String state) {
		PermissionAttachment old = states.remove(player.getUniqueId());
		if (old != null) {
			player.removeAttachment(old);
		}
		PermissionAttachment attachment = player.addAttachment(plugin);
		boolean waiting = !state.equals("tamam");
		attachment.setPermission("cevherauth.bekliyor", waiting);
		attachment.setPermission("cevherauth.bekliyor.giris", state.equals("giris"));
		attachment.setPermission("cevherauth.bekliyor.kayit", state.equals("kayit"));
		attachment.setPermission("cevherauth.kayitli", !state.equals("kayit"));
		states.put(player.getUniqueId(), attachment);
	}

	private void authorize(Player player, boolean viaLauncher) {
		pending.remove(player.getUniqueId());
		setState(player, "tamam");
		authorized.put(AccountStore.key(player.getName()), new Authorized(player.getName(), ipOf(player), viaLauncher));
		player.resetTitle();
		if (viaLauncher) {
			Component welcome = plugin.message("launcher-welcome");
			if (!plugin.getConfig().getString("messages.launcher-welcome", "").isBlank()) {
				player.sendMessage(welcome);
			}
		} else {
			player.sendMessage(plugin.message("login-success"));
		}
		String server = plugin.getConfig().getString("in-game-login.send-to-server", "");
		if (server != null && !server.isBlank()) {
			long delay = Math.max(0, plugin.getConfig().getLong("in-game-login.send-delay-ticks", 40));
			plugin.getServer().getScheduler().runTaskLater(plugin, () -> sendToServer(player, server.trim()), delay);
		}
	}

	private void sendToServer(Player player, String server) {
		if (!player.isOnline() || isPending(player)) {
			return;
		}
		// Velocity/BungeeCord "Connect" mesajı (velocity.toml: bungee-plugin-message-channel = true).
		java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
		try (java.io.DataOutputStream out = new java.io.DataOutputStream(bytes)) {
			out.writeUTF("Connect");
			out.writeUTF(server);
		} catch (IOException e) {
			return;
		}
		player.sendPluginMessage(plugin, "BungeeCord", bytes.toByteArray());
	}

	static String ipOf(Player player) {
		var address = player.getAddress();
		return CevherAuthPlugin.ipKey(address == null ? null : address.getAddress());
	}

	// ── komutlar ────────────────────────────────────────────────────────────────

	private interface Handler {
		void run(Player player, String[] args);
	}

	/** /login ve /register: komut önerisi vermez, yalnızca oyuncular kullanabilir. */
	private final class SecretCommand extends Command {
		private final Handler handler;

		SecretCommand(String name, List<String> aliases, String usage, Handler handler) {
			super(name, "CevherAuth oyun içi giriş", usage, aliases);
			this.handler = handler;
		}

		@Override
		public boolean execute(CommandSender sender, String label, String[] args) {
			if (!(sender instanceof Player player)) {
				sender.sendMessage("Bu komut yalnızca oyunda kullanılır.");
				return true;
			}
			handler.run(player, args);
			return true;
		}

		@Override
		public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
			return List.of();
		}
	}

	private void onLogin(Player player, String[] args) {
		Pending p = pending.get(player.getUniqueId());
		if (p == null) {
			player.sendMessage(plugin.message("already-logged-in"));
			return;
		}
		if (p.busy) {
			return;
		}
		if (args.length != 1) {
			player.sendMessage(plugin.message("login-usage"));
			return;
		}
		String name = player.getName();
		String ip = ipOf(player);
		String ukey = AccountStore.key(name);
		String password = args[0];
		int max = Math.max(1, plugin.getConfig().getInt("max-failed-logins", 10));
		if (failIp.count(ip) >= max * 3 || failAccount.count(ukey) >= max * 3) {
			player.kick(plugin.message("too-many-attempts"));
			return;
		}
		AccountStore.Account account = plugin.accounts().get(name);
		LeaderOsSync site = plugin.site();
		boolean siteOn = site != null && site.enabled();
		if (account == null && !siteOn) {
			player.sendMessage(plugin.message("not-registered"));
			return;
		}
		p.busy = true;
		plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
			boolean valid = false;
			try {
				if (account != null) {
					valid = Passwords.verify(password, account.passwordHash);
				}
				if (!valid && siteOn && plugin.http() != null) {
					// Sitede açılmış (veya şifresi sitede değişmiş) hesap: launcher girişiyle aynı kurallar.
					valid = plugin.http().loginViaSite(name, password, account, ip) != null;
				}
			} catch (IOException | RuntimeException e) {
				plugin.getLogger().log(Level.WARNING, name + " oyun içi giriş doğrulanamadı", e);
			}
			boolean ok = valid;
			plugin.getServer().getScheduler().runTask(plugin, () -> finishLogin(player, p, ok, ip, ukey, account == null));
		});
	}

	private void finishLogin(Player player, Pending p, boolean valid, String ip, String ukey, boolean noAccount) {
		p.busy = false;
		if (!player.isOnline() || pending.get(player.getUniqueId()) != p) {
			return;
		}
		if (valid) {
			plugin.getLogger().info(player.getName() + " oyunda şifreyle giriş yaptı (" + ip + ")");
			authorize(player, false);
			return;
		}
		failIp.record(ip);
		failAccount.record(ukey);
		p.attempts++;
		int maxAttempts = Math.max(1, plugin.getConfig().getInt("in-game-login.max-attempts", 5));
		if (p.attempts >= maxAttempts) {
			pending.remove(player.getUniqueId());
			player.kick(plugin.message("too-many-attempts"));
			return;
		}
		player.sendMessage(plugin.message(noAccount ? "not-registered" : "wrong-password")
			.replaceText(b -> b.matchLiteral("{kalan}").replacement(String.valueOf(maxAttempts - p.attempts))));
	}

	private void onRegister(Player player, String[] args) {
		Pending p = pending.get(player.getUniqueId());
		if (p == null) {
			player.sendMessage(plugin.message("already-logged-in"));
			return;
		}
		if (p.busy) {
			return;
		}
		if (!plugin.getConfig().getBoolean("in-game-login.allow-register", true)) {
			player.sendMessage(plugin.message("register-disabled"));
			return;
		}
		if (args.length != 2) {
			player.sendMessage(plugin.message("register-usage"));
			return;
		}
		if (!args[0].equals(args[1])) {
			player.sendMessage(plugin.message("password-mismatch"));
			return;
		}
		String name = player.getName();
		String password = args[0];
		String ip = ipOf(player);
		if (!USERNAME.matcher(name).matches()) {
			player.sendMessage(plugin.message("invalid-username"));
			return;
		}
		int min = plugin.getConfig().getInt("password.min-length", 6);
		int max = plugin.getConfig().getInt("password.max-length", 64);
		if (password.length() < min || password.length() > max || password.isBlank()) {
			player.sendMessage(plugin.message("weak-password")
				.replaceText(b -> b.matchLiteral("{min}").replacement(String.valueOf(min))));
			return;
		}
		if (plugin.accounts().get(name) != null) {
			player.sendMessage(plugin.message("already-registered"));
			return;
		}
		if (plugin.isReservedForInGame(name)) {
			player.sendMessage(plugin.message(plugin.isOperatorName(name) ? "protected-op-name" : "protected-name"));
			return;
		}
		if (registerIp.count(ip) >= plugin.getConfig().getInt("max-registrations-per-10min", 5)) {
			player.kick(plugin.message("too-many-attempts"));
			return;
		}
		registerIp.record(ip);
		int maxPerIp = plugin.getConfig().getInt("max-accounts-per-ip", 3);
		if (maxPerIp > 0 && !"loopback".equals(ip) && plugin.accounts().countByIp(ip) >= maxPerIp) {
			player.sendMessage(plugin.message("too-many-accounts"));
			return;
		}
		p.busy = true;
		plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
			String error = null;
			try {
				boolean siteSynced = false;
				LeaderOsSync site = plugin.site();
				if (site != null && site.enabled()) {
					LeaderOsSync.Result r = site.register(name, password, ip);
					switch (r) {
						case OK -> siteSynced = true;
						case USERNAME_TAKEN -> error = "site-name-taken";
						case INVALID_USERNAME -> error = "invalid-username";
						case INVALID_PASSWORD -> error = "weak-password";
						case LIMIT -> error = "too-many-accounts";
						default -> plugin.getLogger().warning(name + " sitede açılamadı (" + r + "); yalnızca launcher hesabı açılıyor");
					}
				}
				if (error == null) {
					AccountStore.Account created = plugin.accounts().create(name, Passwords.hash(password), ip, siteSynced);
					if (created == null) {
						error = "already-registered";
					} else {
						plugin.getLogger().info(name + " oyunda kayıt oldu (" + ip + ")" + (siteSynced ? " — sitede de hesap açıldı" : ""));
					}
				}
			} catch (IOException | RuntimeException e) {
				plugin.getLogger().log(Level.WARNING, name + " oyun içi kaydı yazılamadı", e);
				error = "internal-error";
			}
			String result = error;
			plugin.getServer().getScheduler().runTask(plugin, () -> {
				p.busy = false;
				if (!player.isOnline() || pending.get(player.getUniqueId()) != p) {
					return;
				}
				if (result == null) {
					player.sendMessage(plugin.message("register-success"));
					authorize(player, false);
				} else {
					player.sendMessage(plugin.message(result)
						.replaceText(b -> b.matchLiteral("{min}").replacement(String.valueOf(min))));
				}
			});
		});
	}

	// ── giriş yapmamış oyuncu kısıtlamaları ────────────────────────────────────

	private void block(Player player, Cancellable event) {
		if (player != null && isPending(player)) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onMove(PlayerMoveEvent event) {
		if (!isPending(event.getPlayer())) {
			return;
		}
		Location from = event.getFrom();
		Location to = event.getTo();
		if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
			Location back = from.clone();
			back.setYaw(to.getYaw());
			back.setPitch(to.getPitch());
			event.setTo(back);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onCommand(PlayerCommandPreprocessEvent event) {
		if (!isPending(event.getPlayer())) {
			return;
		}
		String msg = event.getMessage();
		String label = msg.length() > 1 ? msg.substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT) : "";
		if (label.startsWith("cevherauth:")) {
			label = label.substring("cevherauth:".length());
		}
		if (LOGIN_LABELS.contains(label) || REGISTER_LABELS.contains(label)) {
			return;
		}
		event.setCancelled(true);
		remind(event.getPlayer(), false);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onChat(AsyncChatEvent event) {
		if (isPending(event.getPlayer())) {
			event.setCancelled(true);
			remind(event.getPlayer(), false);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onInteract(PlayerInteractEvent event) {
		block(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onInteractEntity(PlayerInteractEntityEvent event) {
		block(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onDrop(PlayerDropItemEvent event) {
		block(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPickup(EntityPickupItemEvent event) {
		if (event.getEntity() instanceof Player player) {
			block(player, event);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onSwap(PlayerSwapHandItemsEvent event) {
		block(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onInventoryClick(InventoryClickEvent event) {
		if (event.getWhoClicked() instanceof Player player) {
			block(player, event);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onInventoryOpen(InventoryOpenEvent event) {
		if (event.getPlayer() instanceof Player player) {
			block(player, event);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onBreak(BlockBreakEvent event) {
		block(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlace(BlockPlaceEvent event) {
		block(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onDamage(EntityDamageEvent event) {
		if (event.getEntity() instanceof Player player) {
			block(player, event);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onAttack(EntityDamageByEntityEvent event) {
		Entity damager = event.getDamager();
		if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
			block(shooter, event);
		} else if (damager instanceof Player player) {
			block(player, event);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onFood(FoodLevelChangeEvent event) {
		if (event.getEntity() instanceof Player player) {
			block(player, event);
		}
	}
}
