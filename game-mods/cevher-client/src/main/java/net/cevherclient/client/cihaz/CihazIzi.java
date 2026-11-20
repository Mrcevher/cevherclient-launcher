package net.cevherclient.client.cihaz;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.cevherclient.client.CevherClientMod;
import net.fabricmc.fabric.api.client.networking.v1.C2SPlayChannelEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Sunucunun kalıcı yasak sistemi (CevherAuth) için bilgisayar kimliği. Ham kimlik asla gönderilmez: Windows
 * MachineGuid (Linux machine-id, macOS IOPlatformUUID) ve fiziksel ağ kartı adresleri tuzlu SHA-256 ile özetlenir.
 * Sunucu yasak sırasında bir "damga" gönderirse bilgisayarda üç ayrı yere saklanır ve her girişte geri bildirilir.
 */
public final class CihazIzi {
	private static final Pattern GUID = Pattern.compile("MachineGuid\\s+REG_SZ\\s+([0-9a-fA-F-]{16,64})");
	private static final Pattern MAC_UUID = Pattern.compile("\"IOPlatformUUID\"\\s*=\\s*\"([0-9A-Fa-f-]{16,64})\"");
	private static final Pattern DAMGA = Pattern.compile("[A-Za-z0-9_-]{16,64}");
	private static final String[] SANAL = {"virtual", "vmware", "vbox", "hyper-v", "docker", "tap", "tun", "vpn", "bluetooth", "wsl", "loopback",
		"teredo", "isatap", "zerotier", "hamachi", "wireguard", "npcap", "pseudo"};

	private static volatile List<String> izler = List.of();

	private CihazIzi() {
	}

	public static void init() {
		PayloadTypeRegistry.playS2C().register(CihazPayload.TYPE, CihazPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(CihazPayload.TYPE, CihazPayload.CODEC);
		ClientPlayNetworking.registerGlobalReceiver(CihazPayload.TYPE, (payload, context) -> al(payload.text()));
		C2SPlayChannelEvents.REGISTER.register((handler, sender, client, channels) -> {
			if (channels.contains(CihazPayload.TYPE.id())) {
				client.execute(CihazIzi::gonder);
			}
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(CihazIzi::gonder));
		Thread t = new Thread(CihazIzi::hesapla, "Cevherclient cihaz izi");
		t.setDaemon(true);
		t.start();
	}

	private static void gonder() {
		if (!ClientPlayNetworking.canSend(CihazPayload.TYPE)) {
			return;
		}
		JsonObject o = new JsonObject();
		o.addProperty("t", "iz");
		JsonArray a = new JsonArray();
		izler.forEach(a::add);
		o.add("izler", a);
		JsonArray d = new JsonArray();
		damgalar().forEach(d::add);
		o.add("damgalar", d);
		ClientPlayNetworking.send(new CihazPayload(o.toString()));
	}

	private static void al(String metin) {
		try {
			JsonElement e = JsonParser.parseString(metin);
			if (!e.isJsonObject()) {
				return;
			}
			JsonObject o = e.getAsJsonObject();
			if ("damga".equals(str(o, "t")) && DAMGA.matcher(str(o, "k")).matches()) {
				damgaYaz(str(o, "k"));
			}
		} catch (RuntimeException ex) {
			// geçersiz paket
		}
	}

	private static String str(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}

	// ── kimlik ─────────────────────────────────────────────────────────────────

	private static void hesapla() {
		List<String> l = new ArrayList<>();
		try {
			String m = makineKimligi();
			if (m != null && !m.isBlank()) {
				l.add("m:" + ozet("makine", m.trim().toLowerCase(Locale.ROOT)));
			}
		} catch (Exception e) {
			CevherClientMod.LOGGER.debug("Makine kimliği okunamadı: {}", e.toString());
		}
		try {
			List<String> macler = new ArrayList<>();
			for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
				if (ni.isLoopback() || ni.isVirtual()) {
					continue;
				}
				byte[] hw = ni.getHardwareAddress();
				if (hw == null || hw.length != 6 || (hw[0] & 0x02) != 0 || (hw[0] & 0x01) != 0) {
					continue; // yerel atanmış (rastgele) ve çoklu yayın adresleri değişkendir
				}
				String ad = (ni.getDisplayName() + " " + ni.getName()).toLowerCase(Locale.ROOT);
				boolean sanal = false;
				for (String s : SANAL) {
					sanal |= ad.contains(s);
				}
				if (!sanal) {
					macler.add(HexFormat.of().formatHex(hw));
				}
			}
			Collections.sort(macler);
			for (String mac : new LinkedHashSet<>(macler)) {
				if (l.size() >= 6) {
					break;
				}
				l.add("n:" + ozet("ag", mac));
			}
		} catch (Exception e) {
			CevherClientMod.LOGGER.debug("Ağ kartları okunamadı: {}", e.toString());
		}
		izler = List.copyOf(l);
	}

	private static String makineKimligi() throws Exception {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		if (os.contains("win")) {
			Matcher m = GUID.matcher(calistir("reg", "query", "HKLM\\SOFTWARE\\Microsoft\\Cryptography", "/v", "MachineGuid"));
			return m.find() ? m.group(1) : null;
		}
		if (os.contains("mac")) {
			Matcher m = MAC_UUID.matcher(calistir("ioreg", "-rd1", "-c", "IOPlatformExpertDevice"));
			return m.find() ? m.group(1) : null;
		}
		for (String p : new String[] {"/etc/machine-id", "/var/lib/dbus/machine-id"}) {
			Path f = Path.of(p);
			if (Files.isReadable(f)) {
				return Files.readString(f).trim();
			}
		}
		return null;
	}

	private static String calistir(String... komut) throws Exception {
		Process p = new ProcessBuilder(komut).redirectErrorStream(true).start();
		StringBuilder sb = new StringBuilder();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
			String s;
			while ((s = r.readLine()) != null && sb.length() < 64_000) {
				sb.append(s).append('\n');
			}
		}
		p.waitFor(5, TimeUnit.SECONDS);
		return sb.toString();
	}

	private static String ozet(String tur, String deger) throws Exception {
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		byte[] h = md.digest(("cevherclient-cihaz|" + tur + "|" + deger).getBytes(StandardCharsets.UTF_8));
		return HexFormat.of().formatHex(h, 0, 16);
	}

	// ── damga ──────────────────────────────────────────────────────────────────

	private static List<Path> damgaDosyalari() {
		String ev = System.getProperty("user.home", ".");
		return List.of(
			Path.of(ev, ".cache", "cevher", "k.dat"),
			FabricLoader.getInstance().getConfigDir().resolve("cevherclient").resolve("oturum.dat"));
	}

	private static Set<String> damgalar() {
		Set<String> s = new LinkedHashSet<>();
		try {
			String p = Preferences.userRoot().node("cevherclient").get("o", "");
			for (String x : p.split(",")) {
				if (DAMGA.matcher(x).matches()) {
					s.add(x);
				}
			}
		} catch (Exception ignored) {
			// tercih deposu yok
		}
		for (Path f : damgaDosyalari()) {
			try {
				if (Files.isReadable(f)) {
					for (String x : Files.readString(f).split("[,\\s]+")) {
						if (DAMGA.matcher(x).matches()) {
							s.add(x);
						}
					}
				}
			} catch (Exception ignored) {
				// okunamadı
			}
		}
		while (s.size() > 8) {
			s.remove(s.iterator().next());
		}
		return s;
	}

	private static void damgaYaz(String k) {
		Set<String> s = damgalar();
		s.add(k);
		String hepsi = String.join(",", s);
		try {
			Preferences p = Preferences.userRoot().node("cevherclient");
			p.put("o", hepsi);
			p.flush();
		} catch (Exception ignored) {
			// yazılamadı
		}
		for (Path f : damgaDosyalari()) {
			try {
				Files.createDirectories(f.getParent());
				Files.writeString(f, hepsi);
				try {
					Files.setAttribute(f, "dos:hidden", true);
				} catch (Exception ignored) {
					// Windows dışı
				}
			} catch (Exception ignored) {
				// yazılamadı
			}
		}
	}
}
