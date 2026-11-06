package net.cevherclient.uibridge;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * Cevherclient kozmetiklerinin eşitlenmesi (istemci modu cevher_client).
 *
 * <p>Kanal {@code cevherclient:cosmetic}, gövde düz UTF-8 metin. Üç yuva var: baş, sırt (kanatlar) ve pelerin.
 * <ul>
 * <li>İstemci → sunucu: yuva başına bir mesaj, {@code head=<id>}, {@code back=<id>} ya da {@code cape=<id>}
 *     (id boşsa o yuvada kozmetik yok). Oyuncu girince ve seçimi değişince gelir.</li>
 * <li>Sunucu → istemci: satır başına {@code <uuid> <id>} (baş yuvası; eski istemciler yalnızca bunu tanır) ya da
 *     {@code <uuid> <yuva>=<id>}; id yerine {@code -} kozmetiğin kalktığını söyler. Kanalı kaydeden istemciye
 *     mevcut herkesin durumu bir kerede, sonra her değişiklik tek satır gönderilir.</li>
 * </ul>
 *
 * <p>Seçimin kime ait olduğunu istemci söylemez: sunucu paketi gönderen oyuncuya yazar, böylece başkası adına
 * seçim yapılamaz. Seçilebilecek kozmetikler {@code cosmetics.available} listesiyle sınırlıdır (liste {@code "*"}
 * içeriyorsa her kimlik kabul edilir); isteğe bağlı olarak her kozmetik için {@code cevherclient.cosmetic.<id>}
 * yetkisi aranır. Reddedilen istekte oyuncuya o yuvadaki gerçek durumu geri gönderilir, istemci kendi seçimini ona
 * göre düzeltir. Seçim sunucuda saklanmaz; istemci her girişte yeniden gönderir.
 */
final class CosmeticSync implements Listener, PluginMessageListener {
	static final String CHANNEL = "cevherclient:cosmetic";
	private static final int MAX_MESSAGE_BYTES = 64;
	private static final Pattern SELECTION = Pattern.compile("(head|back|cape)=([a-z0-9_]{0,32})");

	/** Kozmetik yuvası; {@code key} mesajlardaki addır. */
	private enum Slot {
		HEAD("head"),
		BACK("back"),
		CAPE("cape");

		final String key;

		Slot(String key) {
			this.key = key;
		}

		static Slot of(String key) {
			for (Slot slot : values()) {
				if (slot.key.equals(key)) {
					return slot;
				}
			}
			throw new IllegalArgumentException(key);
		}
	}

	private final CevherUiBridgePlugin plugin;
	/** Çevrimiçi oyuncuların yuva başına seçili kozmetiği; kayıt yoksa o yuvada kozmetik yok. */
	private final Map<Slot, Map<UUID, String>> selections = new EnumMap<>(Slot.class);
	/** Hız sınırı yuva başına tutulur; istemci girişte üç yuvayı art arda gönderir. */
	private final Map<Slot, Map<UUID, Long>> lastChange = new EnumMap<>(Slot.class);

	CosmeticSync(CevherUiBridgePlugin plugin) {
		this.plugin = plugin;
		for (Slot slot : Slot.values()) {
			selections.put(slot, new ConcurrentHashMap<>());
			lastChange.put(slot, new ConcurrentHashMap<>());
		}
	}

	void enable() {
		plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
		plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
		plugin.getServer().getPluginManager().registerEvents(this, plugin);
	}

	private boolean enabled() {
		return plugin.getConfig().getBoolean("cosmetics.enabled", true);
	}

	@Override
	public void onPluginMessageReceived(String channel, Player player, byte[] message) {
		if (!CHANNEL.equals(channel) || message.length > MAX_MESSAGE_BYTES) {
			return;
		}
		Matcher matcher = SELECTION.matcher(new String(message, StandardCharsets.UTF_8).trim());
		if (!matcher.matches()) {
			plugin.getLogger().warning(player.getName() + " geçersiz kozmetik isteği gönderdi, yok sayıldı");
			return;
		}
		Slot slot = Slot.of(matcher.group(1));
		String id = matcher.group(2);
		runOnMainThread(() -> select(player, slot, id));
	}

	private void select(Player player, Slot slot, String id) {
		if (!enabled() || !player.isOnline()) {
			return;
		}
		UUID uuid = player.getUniqueId();
		long now = System.currentTimeMillis();
		long minInterval = Math.max(0, plugin.getConfig().getLong("cosmetics.min-interval-ms", 500));
		Long last = lastChange.get(slot).get(uuid);
		if (last != null && now - last < minInterval) {
			return;
		}
		lastChange.get(slot).put(uuid, now);

		if (!id.isEmpty() && !allowed(player, id)) {
			plugin.getLogger().info(player.getName() + " için '" + id + "' kozmetiği reddedildi");
			sendTo(player, line(uuid, slot));
			return;
		}
		Map<UUID, String> map = selections.get(slot);
		String previous = id.isEmpty() ? map.remove(uuid) : map.put(uuid, id);
		if (Objects.equals(previous, id.isEmpty() ? null : id)) {
			return;
		}
		broadcast(uuid, line(uuid, slot));
	}

	private boolean allowed(Player player, String id) {
		List<String> available = plugin.getConfig().getStringList("cosmetics.available");
		if (!available.contains("*") && !available.contains(id)) {
			return false;
		}
		return !plugin.getConfig().getBoolean("cosmetics.require-permission", false)
			|| player.hasPermission("cevherclient.cosmetic." + id);
	}

	/** Oyuncunun bir yuvadaki durumu, istemciye gidecek satır olarak. Baş yuvası eski biçimde yazılır. */
	private String line(UUID uuid, Slot slot) {
		String id = selections.get(slot).get(uuid);
		String value = id == null ? "-" : id;
		return slot == Slot.HEAD ? uuid + " " + value : uuid + " " + slot.key + "=" + value;
	}

	/** Gönderen hariç, kanalı kaydetmiş herkese tek satır gönderir. */
	private void broadcast(UUID sender, String line) {
		for (Player other : plugin.getServer().getOnlinePlayers()) {
			if (!other.getUniqueId().equals(sender)) {
				sendTo(other, line);
			}
		}
	}

	private void sendTo(Player player, String text) {
		if (player.isOnline() && player.getListeningPluginChannels().contains(CHANNEL)) {
			player.sendPluginMessage(plugin, CHANNEL, text.getBytes(StandardCharsets.UTF_8));
		}
	}

	/** İstemci kanalı kaydedince, o an çevrimiçi diğer oyuncuların kozmetiklerini bir mesajda gönderir. */
	@EventHandler
	public void onRegisterChannel(PlayerRegisterChannelEvent event) {
		if (!CHANNEL.equals(event.getChannel())) {
			return;
		}
		Player player = event.getPlayer();
		runOnMainThread(() -> {
			if (!enabled()) {
				return;
			}
			StringBuilder lines = new StringBuilder();
			for (Slot slot : Slot.values()) {
				for (UUID uuid : selections.get(slot).keySet()) {
					if (!uuid.equals(player.getUniqueId())) {
						lines.append(line(uuid, slot)).append('\n');
					}
				}
			}
			if (lines.length() > 0) {
				sendTo(player, lines.toString().trim());
			}
		});
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		UUID uuid = event.getPlayer().getUniqueId();
		for (Slot slot : Slot.values()) {
			lastChange.get(slot).remove(uuid);
			if (selections.get(slot).remove(uuid) != null) {
				broadcast(uuid, slot == Slot.HEAD ? uuid + " -" : uuid + " " + slot.key + "=-");
			}
		}
	}

	private void runOnMainThread(Runnable task) {
		if (plugin.getServer().isPrimaryThread()) {
			task.run();
		} else {
			plugin.getServer().getScheduler().runTask(plugin, task);
		}
	}
}
