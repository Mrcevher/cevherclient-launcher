package net.cevherclient.uibridge;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * CevherUIBridge: Cevherclient UI modu (cevher_ui) için sunucu tarafı.
 *
 * Mod oyuncu girince cevherclient:hello gönderir; bu eklenti o oyunculara cevherclient:ui kanalından
 * "sao_on" / "sao_off" göndererek SAO arayüzünü açıp kapatır. Arayüz yalnızca çizimdir: giriş, eşya,
 * para ve yetki kararları her zaman sunucuda kalır ve hello'ya dayanmaz. cevherclient:action ile gelen
 * istekler (ör. depoyu aç) burada yetki ve durum kontrolünden geçer.
 *
 * Paper bir kanala ancak istemci onu kaydettikten (minecraft:register) sonra mesaj gönderebilir. hello
 * ile kanal kaydı hangi sırayla gelirse gelsin, ikisi de tamamlanınca istenen durum gönderilir.
 */
public final class CevherUiBridgePlugin extends JavaPlugin implements Listener, PluginMessageListener {
	static final String HELLO_CHANNEL = "cevherclient:hello";
	static final String UI_CHANNEL = "cevherclient:ui";
	static final String ACTION_CHANNEL = "cevherclient:action";
	private static final String ACTION_OPEN_STORAGE = "open_storage";
	private static final int MAX_ACTION_BYTES = 256;

	/** hello gönderen oyuncular. Yalnızca arayüz mesajlarının kime gideceğini belirler. */
	private final Map<UUID, ClientInfo> clients = new ConcurrentHashMap<>();
	/** Oyuncu başına istenen SAO durumu; kayıt yoksa kapalı. */
	private final Map<UUID, Boolean> saoEnabled = new ConcurrentHashMap<>();
	/** Son depo açma zamanı (bekleme süresi için). */
	private final Map<UUID, Long> storageOpened = new ConcurrentHashMap<>();
	/** sao-worlds (küçük harf). */
	private volatile Set<String> saoWorlds = Set.of();
	private BetterHudSync betterHud;

	@Override
	public void onEnable() {
		saveDefaultConfig();
		betterHud = new BetterHudSync(this);
		betterHud.load();
		loadSettings();

		getServer().getMessenger().registerIncomingPluginChannel(this, HELLO_CHANNEL, this);
		getServer().getMessenger().registerIncomingPluginChannel(this, ACTION_CHANNEL, this);
		getServer().getMessenger().registerOutgoingPluginChannel(this, UI_CHANNEL);
		getServer().getPluginManager().registerEvents(this, this);
		new CosmeticSync(this).enable();

		PluginCommand command = getCommand("saoui");
		if (command != null) {
			SaoUiCommand handler = new SaoUiCommand(this);
			command.setExecutor(handler);
			command.setTabCompleter(handler);
		}
		// /reload sonrası: önceki çalışmada gizlenmiş HUD'ları geri ver (hello gelince yeniden gizlenir).
		for (Player player : getServer().getOnlinePlayers()) {
			betterHud.schedule(player);
		}
		getLogger().info("Hazır — SAO dünyaları: " + (saoWorlds.isEmpty() ? "yok (yalnızca /saoui)" : saoWorlds));
	}

	@Override
	public void onDisable() {
		// Açık arayüzleri kapat ve BetterHud'u geri ver; istemciler bağlantı kopunca zaten kendiliğinden kapatır.
		for (Player player : getServer().getOnlinePlayers()) {
			if (isSaoEnabled(player) && canReceive(player)) {
				send(player, false);
			}
			if (betterHud != null) {
				betterHud.restore(player);
			}
		}
		getServer().getMessenger().unregisterIncomingPluginChannel(this);
		getServer().getMessenger().unregisterOutgoingPluginChannel(this);
	}

	/** config.yml'yi yeniden okur ve sao-worlds açıksa çevrimiçi oyuncuların durumunu dünyalarına göre günceller. */
	void loadSettings() {
		reloadConfig();
		saoWorlds = getConfig().getStringList("sao-worlds").stream()
			.map(name -> name.toLowerCase(Locale.ROOT))
			.collect(Collectors.toUnmodifiableSet());
		if (!saoWorlds.isEmpty()) {
			for (Player player : getServer().getOnlinePlayers()) {
				setSao(player, isSaoWorld(player.getWorld()));
			}
		}
	}

	@Override
	public void onPluginMessageReceived(String channel, Player player, byte[] message) {
		if (HELLO_CHANNEL.equals(channel)) {
			ClientInfo info = ClientInfo.parse(message);
			if (info == null) {
				getLogger().warning(player.getName() + " geçersiz hello gönderdi, yok sayıldı");
				return;
			}
			runOnMainThread(() -> onHello(player, info));
		} else if (ACTION_CHANNEL.equals(channel) && message.length <= MAX_ACTION_BYTES) {
			String action = new String(message, StandardCharsets.UTF_8).trim();
			runOnMainThread(() -> onAction(player, action));
		}
	}

	private void onHello(Player player, ClientInfo info) {
		if (!player.isOnline()) {
			return;
		}
		// Aynı oyuncunun tekrarlanan hello'ları günlüğü doldurmasın: yalnızca ilkini yaz.
		boolean first = clients.put(player.getUniqueId(), info) == null;
		if (first && getConfig().getBoolean("log-hello", true)) {
			getLogger().info(player.getName() + " Cevherclient UI ile bağlandı (mod " + info.mod()
				+ ", mc " + info.mc() + ", protocol " + info.protocol() + ")");
		}
		if (!saoWorlds.isEmpty()) {
			saoEnabled.put(player.getUniqueId(), isSaoWorld(player.getWorld()));
		}
		push(player);
	}

	/** İstemciden gelen işlem isteği. Yalnızca istektir: her biri burada kontrol edilir. */
	private void onAction(Player player, String action) {
		if (!player.isOnline() || !clients.containsKey(player.getUniqueId())) {
			return;
		}
		if (ACTION_OPEN_STORAGE.equals(action)) {
			openStorage(player);
		}
	}

	private void openStorage(Player player) {
		if (!isSaoEnabled(player) || player.isDead() || !player.hasPermission("cevherui.storage")) {
			return;
		}
		long now = System.currentTimeMillis();
		long cooldown = Math.max(0, getConfig().getLong("storage.cooldown-ms", 1000));
		Long last = storageOpened.get(player.getUniqueId());
		if (last != null && now - last < cooldown) {
			return;
		}
		storageOpened.put(player.getUniqueId(), now);

		if ("command".equalsIgnoreCase(getConfig().getString("storage.mode", "enderchest"))) {
			String command = getConfig().getString("storage.command", "").trim();
			if (command.startsWith("/")) {
				command = command.substring(1);
			}
			if (!command.isEmpty()) {
				player.performCommand(command);
			}
		} else {
			player.openInventory(player.getEnderChest());
		}
	}

	@EventHandler
	public void onRegisterChannel(PlayerRegisterChannelEvent event) {
		if (UI_CHANNEL.equals(event.getChannel())) {
			Player player = event.getPlayer();
			runOnMainThread(() -> push(player));
		}
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		// Önceki oturumda (ör. çökme) gizli kalmış BetterHud HUD'unu düzelt.
		betterHud.schedule(event.getPlayer());
	}

	@EventHandler
	public void onWorldChange(PlayerChangedWorldEvent event) {
		if (!saoWorlds.isEmpty()) {
			setSao(event.getPlayer(), isSaoWorld(event.getPlayer().getWorld()));
		}
	}

	@EventHandler
	public void onGameModeChange(PlayerGameModeChangeEvent event) {
		// Creative/Spectator'da mod vanilla HUD'a döner; BetterHud de geri gelmeli.
		betterHud.schedule(event.getPlayer());
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		UUID id = event.getPlayer().getUniqueId();
		clients.remove(id);
		saoEnabled.remove(id);
		storageOpened.remove(id);
		betterHud.restore(event.getPlayer());
	}

	/**
	 * İstenen durumu kaydeder ve istemci hazırsa gönderir.
	 *
	 * @return oyuncu Cevherclient UI kullanıyorsa (hello geldiyse) true
	 */
	boolean setSao(Player player, boolean enabled) {
		Boolean previous = saoEnabled.put(player.getUniqueId(), enabled);
		if (previous == null || previous != enabled) {
			push(player);
		}
		return clients.containsKey(player.getUniqueId());
	}

	boolean isSaoEnabled(Player player) {
		return Boolean.TRUE.equals(saoEnabled.get(player.getUniqueId()));
	}

	ClientInfo clientInfo(Player player) {
		return clients.get(player.getUniqueId());
	}

	/** Mod bu oyuncuya kendi HUD'unu çiziyor mu? (Modlu, SAO açık, Survival/Adventure.) */
	boolean drawsOwnHud(Player player) {
		GameMode mode = player.getGameMode();
		return canReceive(player) && isSaoEnabled(player) && (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE);
	}

	private boolean isSaoWorld(World world) {
		return saoWorlds.contains(world.getName().toLowerCase(Locale.ROOT));
	}

	/** hello geldi ve istemci cevherclient:ui kanalını kaydetti mi? */
	private boolean canReceive(Player player) {
		return clients.containsKey(player.getUniqueId()) && player.getListeningPluginChannels().contains(UI_CHANNEL);
	}

	/** İstenen durumu gönderir; istemci henüz hazır değilse hazır olunca (hello / kanal kaydı) gönderilir. */
	private void push(Player player) {
		if (player.isOnline() && canReceive(player)) {
			send(player, isSaoEnabled(player));
		}
	}

	private void send(Player player, boolean enabled) {
		String command = enabled ? "sao_on" : "sao_off";
		player.sendPluginMessage(this, UI_CHANNEL, command.getBytes(StandardCharsets.UTF_8));
		if (betterHud != null && isEnabled()) {
			betterHud.schedule(player);
		}
	}

	private void runOnMainThread(Runnable task) {
		if (getServer().isPrimaryThread()) {
			task.run();
		} else {
			getServer().getScheduler().runTask(this, task);
		}
	}
}
