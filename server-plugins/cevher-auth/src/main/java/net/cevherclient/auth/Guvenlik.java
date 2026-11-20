package net.cevherclient.auth;

import com.google.gson.JsonObject;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;

/**
 * 2026-11-19 saldırısından çıkan korumalar (tüm rollerde çalışır):
 *
 * <ul>
 *   <li><b>IP başına online sınırı:</b> saldırgan aynı IP'den 6 hesapla aynı anda geldi.</li>
 *   <li><b>Hile istemcisi tespiti:</b> kullanılan istemciler kendini client brand, kayıtlı
 *   kanallar ve custom click kimlikleriyle (autismclient, xynisclient) belli etti.</li>
 *   <li><b>Geçici IP engeli:</b> paket kalkanı tarafından atılan saldırgan saniyeler içinde
 *   yeniden bağlanıp devam ediyordu. Güvenlik nedenli atılmalar sayılır; eşik aşılınca IP bir
 *   süre giremez. Kapı rolünde engel lobiye de bildirilir (giriş kapısı orası).</li>
 * </ul>
 */
final class Guvenlik implements Listener {
	/** Login'den geçip henüz oyuna girmemiş bağlantılar bu süre boyunca sayılır. */
	private static final long REZERV_MS = 30_000;
	private static final long IHLAL_PENCERE_MS = 10 * 60_000L;

	private final CevherAuthPlugin plugin;
	/** ip → (küçük harf ad → bitiş zamanı; oyundakiler için Long.MAX_VALUE). */
	private final Map<String, Map<String, Long>> ipOyuncular = new ConcurrentHashMap<>();
	/** ip → engelin bitiş zamanı. */
	private final Map<String, Engel> engeller = new ConcurrentHashMap<>();
	private final Throttle ihlaller = new Throttle(IHLAL_PENCERE_MS);
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
	private volatile Ayar ayar;

	record Engel(long bitis, String neden) {
	}

	private record Ayar(
		int ipBasinaOnline,
		Set<String> ipMuaf,
		boolean engelAcik,
		long engelMs,
		int ihlalEsigi,
		Set<String> kickSebepleri,
		List<String> kickMesajlari,
		boolean hileAcik,
		boolean hileEngelle,
		List<String> brandlar,
		Set<String> kanallar,
		Set<String> customClick) {
	}

	Guvenlik(CevherAuthPlugin plugin) {
		this.plugin = plugin;
		yenile();
	}

	void yenile() {
		ConfigurationSection c = plugin.getConfig().getConfigurationSection("guvenlik");
		if (c == null) {
			c = plugin.getConfig().createSection("guvenlik");
		}
		ayar = new Ayar(
			Math.max(0, c.getInt("ip-basina-online", 3)),
			kucuk(c.getStringList("ip-muaf")),
			c.getBoolean("ip-engeli.enabled", true),
			Math.max(1, c.getLong("ip-engeli.sure-dakika", 10)) * 60_000L,
			Math.max(1, c.getInt("ip-engeli.ihlal-esigi", 2)),
			buyuk(c.getStringList("ip-engeli.kick-sebepleri")),
			List.copyOf(kucuk(c.getStringList("ip-engeli.kick-mesajlari"))),
			c.getBoolean("hile-istemcisi.enabled", true),
			c.getBoolean("hile-istemcisi.ip-engelle", true),
			List.copyOf(kucuk(c.getStringList("hile-istemcisi.brand"))),
			kucuk(c.getStringList("hile-istemcisi.kanallar")),
			kucuk(c.getStringList("hile-istemcisi.custom-click")));
	}

	private static Set<String> kucuk(List<String> l) {
		Set<String> s = new HashSet<>();
		for (String x : l) {
			if (x != null && !x.isBlank()) {
				s.add(x.trim().toLowerCase(Locale.ROOT));
			}
		}
		return Set.copyOf(s);
	}

	private static Set<String> buyuk(List<String> l) {
		Set<String> s = new HashSet<>();
		for (String x : l) {
			if (x != null && !x.isBlank()) {
				s.add(x.trim().toUpperCase(Locale.ROOT));
			}
		}
		return Set.copyOf(s);
	}

	private static String ip(Player p) {
		InetSocketAddress a = p.getAddress();
		return CevherAuthPlugin.ipKey(a == null ? null : a.getAddress());
	}

	// ── IP engeli ──────────────────────────────────────────────────────────────

	/** IP'yi engeller (ör. lobiye kapıdan gelen bildirim). */
	void engelle(String ip, long ms, String neden, boolean yay) {
		if (ip == null || ip.equals("loopback") || ip.equals("unknown") || ayar.ipMuaf().contains(ip)) {
			return;
		}
		long bitis = System.currentTimeMillis() + ms;
		engeller.merge(ip, new Engel(bitis, neden), (eski, yeni) -> eski.bitis() >= yeni.bitis() ? eski : yeni);
		plugin.getLogger().warning("IP engellendi: " + ip + " " + (ms / 60_000) + " dk (" + neden + ")");
		if (yay && plugin.isGate()) {
			lobiyeBildir(ip, ms, neden);
		}
	}

	/** Süren engel; yoksa null. */
	Engel engel(String ip) {
		Engel e = engeller.get(ip);
		if (e == null) {
			return null;
		}
		if (e.bitis() <= System.currentTimeMillis()) {
			engeller.remove(ip, e);
			return null;
		}
		return e;
	}

	boolean kaldir(String ip) {
		ihlaller.clear(ip);
		return engeller.remove(ip) != null;
	}

	Map<String, Engel> engeller() {
		return engeller;
	}

	private void ihlal(String ip, String ad, String neden) {
		Ayar a = ayar;
		if (!a.engelAcik() || ip == null) {
			return;
		}
		ihlaller.record(ip);
		int n = ihlaller.count(ip);
		plugin.getLogger().info("Güvenlik ihlali: " + ad + " (" + ip + ") " + neden + " [" + n + "/" + a.ihlalEsigi() + "]");
		if (n >= a.ihlalEsigi()) {
			ihlaller.clear(ip);
			engelle(ip, a.engelMs(), ad + ": " + neden, true);
			plugin.riskTakip().ekle(ad, ip, RiskTakip.Seviye.YUKSEK, "ip-engeli", a.engelMs() / 60_000 + " dk: " + neden);
		}
	}

	private void lobiyeBildir(String ip, long ms, String neden) {
		String url = plugin.getConfig().getString("network.lobby-api", "http://127.0.0.1:25580").replaceAll("/+$", "")
			+ "/api/internal/block";
		JsonObject body = new JsonObject();
		body.addProperty("secret", plugin.getConfig().getString("network.secret", ""));
		body.addProperty("ip", ip);
		body.addProperty("minutes", Math.max(1, ms / 60_000));
		body.addProperty("reason", neden.length() > 120 ? neden.substring(0, 120) : neden);
		HttpRequest req = HttpRequest.newBuilder(URI.create(url))
			.timeout(Duration.ofSeconds(4))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(JsonFile.GSON.toJson(body), StandardCharsets.UTF_8))
			.build();
		http.sendAsync(req, HttpResponse.BodyHandlers.discarding()).whenComplete((r, t) -> {
			if (t != null) {
				plugin.getLogger().warning("IP engeli lobiye bildirilemedi: " + t.getMessage());
			} else if (r.statusCode() != 200) {
				plugin.getLogger().warning("IP engeli lobiye bildirilemedi: HTTP " + r.statusCode());
			}
		});
	}

	// ── giriş ──────────────────────────────────────────────────────────────────

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPreLogin(AsyncPlayerPreLoginEvent event) {
		Ayar a = ayar;
		String ip = CevherAuthPlugin.ipKey(event.getAddress());
		Engel e = engel(ip);
		if (e != null) {
			long dk = Math.max(1, (e.bitis() - System.currentTimeMillis() + 59_999) / 60_000);
			event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, plugin.message("ip-engelli")
				.replaceText(b -> b.matchLiteral("{dk}").replacement(String.valueOf(dk))));
			return;
		}
		if (a.ipBasinaOnline() <= 0 || ip.equals("loopback") || ip.equals("unknown") || a.ipMuaf().contains(ip)) {
			return;
		}
		String ad = event.getName().toLowerCase(Locale.ROOT);
		long simdi = System.currentTimeMillis();
		Map<String, Long> m = ipOyuncular.computeIfAbsent(ip, k -> new ConcurrentHashMap<>());
		synchronized (m) {
			m.values().removeIf(bitis -> bitis <= simdi);
			int digerleri = 0;
			for (String k : m.keySet()) {
				if (!k.equals(ad)) {
					digerleri++;
				}
			}
			if (digerleri >= a.ipBasinaOnline()) {
				plugin.getLogger().info(event.getName() + " reddedildi: " + ip + " adresinden zaten " + digerleri + " hesap bağlı");
				event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, plugin.message("too-many-online")
					.replaceText(b -> b.matchLiteral("{max}").replacement(String.valueOf(a.ipBasinaOnline()))));
				return;
			}
			// Aynı anda gelen çok sayıda login de sayılsın diye girişe kadar yer ayrılır.
			m.merge(ad, simdi + REZERV_MS, Math::max);
		}
	}

	/** Diğer dinleyiciler reddettiyse ayrılan yeri bırak. */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onPreLoginSonuc(AsyncPlayerPreLoginEvent event) {
		if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
			return;
		}
		Map<String, Long> m = ipOyuncular.get(CevherAuthPlugin.ipKey(event.getAddress()));
		if (m != null) {
			String ad = event.getName().toLowerCase(Locale.ROOT);
			synchronized (m) {
				Long b = m.get(ad);
				if (b != null && b != Long.MAX_VALUE) {
					m.remove(ad);
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(PlayerJoinEvent event) {
		Player p = event.getPlayer();
		String ip = ip(p);
		ipOyuncular.computeIfAbsent(ip, k -> new ConcurrentHashMap<>()).put(p.getName().toLowerCase(Locale.ROOT), Long.MAX_VALUE);
		Ayar a = ayar;
		if (!a.hileAcik()) {
			return;
		}
		String brand = p.getClientBrandName();
		if (brand != null) {
			String b = brand.toLowerCase(Locale.ROOT);
			for (String yasak : a.brandlar()) {
				if (b.contains(yasak)) {
					hileBulundu(p, "brand=" + brand);
					return;
				}
			}
		}
		for (String kanal : p.getListeningPluginChannels()) {
			if (yasakliKanal(kanal)) {
				hileBulundu(p, "kanal=" + kanal);
				return;
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		Player p = event.getPlayer();
		String ip = ip(p);
		Map<String, Long> m = ipOyuncular.get(ip);
		if (m != null) {
			synchronized (m) {
				m.remove(p.getName().toLowerCase(Locale.ROOT));
				if (m.isEmpty()) {
					ipOyuncular.remove(ip, m);
				}
			}
		}
	}

	// ── hile istemcisi ─────────────────────────────────────────────────────────

	private boolean yasakliKanal(String kanal) {
		if (kanal == null) {
			return false;
		}
		int i = kanal.indexOf(':');
		String ns = (i < 0 ? kanal : kanal.substring(0, i)).toLowerCase(Locale.ROOT);
		return ayar.kanallar().contains(ns);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onRegister(PlayerRegisterChannelEvent event) {
		if (ayar.hileAcik() && yasakliKanal(event.getChannel())) {
			hileBulundu(event.getPlayer(), "kanal=" + event.getChannel());
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onCustomClick(PlayerCustomClickEvent event) {
		Ayar a = ayar;
		if (!a.hileAcik() || !a.customClick().contains(event.getIdentifier().namespace().toLowerCase(Locale.ROOT))) {
			return;
		}
		if (event.getCommonConnection() instanceof io.papermc.paper.connection.PlayerGameConnection g) {
			hileBulundu(g.getPlayer(), "custom-click=" + event.getIdentifier().asString());
		} else {
			event.getCommonConnection().disconnect(plugin.message("cheat-client"));
		}
	}

	/** Kesin hile kanıtı (ör. hile kelimeli tanınmayan mod): ayar açıksa IP'yi engeller. */
	void hileEngeli(String ip, String neden) {
		Ayar a = ayar;
		if (a.hileEngelle() && a.engelAcik()) {
			engelle(ip, a.engelMs(), neden, true);
		}
	}

	private void hileBulundu(Player p, String detay) {
		String ip = ip(p);
		plugin.riskTakip().ekle(p.getName(), ip, RiskTakip.Seviye.KRITIK, "hile-istemcisi", detay);
		plugin.getLogger().warning("Hile istemcisi: " + p.getName() + " (" + ip + ") " + detay);
		if (ayar.hileEngelle() && ayar.engelAcik()) {
			engelle(ip, ayar.engelMs(), p.getName() + ": hile istemcisi " + detay, true);
		}
		plugin.getServer().getScheduler().runTask(plugin, () -> {
			if (p.isOnline()) {
				p.kick(plugin.message("cheat-client"), PlayerKickEvent.Cause.PLUGIN);
			}
		});
	}

	// ── güvenlik nedenli atılmalar ─────────────────────────────────────────────

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onKick(PlayerKickEvent event) {
		Ayar a = ayar;
		String neden = null;
		if (a.kickSebepleri().contains(event.getCause().name())) {
			neden = "atılma " + event.getCause().name();
		} else {
			String mesaj = PlainTextComponentSerializer.plainText().serialize(event.reason()).toLowerCase(Locale.ROOT);
			for (String m : a.kickMesajlari()) {
				if (mesaj.contains(m)) {
					neden = "atılma \"" + m + "\"";
					break;
				}
			}
		}
		if (neden != null) {
			plugin.riskTakip().ekle(event.getPlayer().getName(), ip(event.getPlayer()), RiskTakip.Seviye.ORTA, "guvenlik", neden);
			ihlal(ip(event.getPlayer()), event.getPlayer().getName(), neden);
		}
	}

	void cleanup() {
		long simdi = System.currentTimeMillis();
		engeller.values().removeIf(e -> e.bitis() <= simdi);
		ihlaller.cleanup();
		for (Map.Entry<String, Map<String, Long>> en : ipOyuncular.entrySet()) {
			Map<String, Long> m = en.getValue();
			synchronized (m) {
				m.values().removeIf(b -> b <= simdi);
				if (m.isEmpty()) {
					ipOyuncular.remove(en.getKey(), m);
				}
			}
		}
	}
}
