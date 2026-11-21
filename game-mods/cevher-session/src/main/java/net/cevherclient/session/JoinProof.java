package net.cevherclient.session;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Giriş bileti kanıtı (istemci tarafı).
 *
 * Launcher, CevherAuth'tan bilete özel bir sır alır ve oyunu açmadan önce
 * {@code <oyun klasörü>/.cevher/join-ticket.json} dosyasına yazar. Mod açılışta dosyayı okur
 * ve hemen siler; sır yalnızca bu oyun sürecinin belleğinde kalır.
 *
 * Sunucu login aşamasında {@code cevherauth:proof/<nonce>} anahtarlı cookie ister; mod
 * HMAC-SHA256(sır, "cevherauth-proof-v1|nonce|ad") ile yanıt verir. Sır bağlantıdan hiç
 * geçmez ve her bağlanışta nonce yenidir.
 *
 * Kanıt v2 (1.1.0+): yanıt {@code [0x02][HMAC][mod listesi]} biçimindedir; HMAC
 * {@code "cevherauth-proof-v2|nonce|ad|modlar"} üzerinden hesaplanır. Mod listesi, yüklü üst
 * düzey modların (iç içe paketlenmiş alt modlar ve yerleşikler hariç) sıralı, virgülle ayrılmış
 * kimlikleridir. CevherAuth 1.4.0+ listeyi yasaklı/izinli modlarla karşılaştırır; eski sunucular
 * v2 yanıtı tanımaz, bu yüzden sunucu önce güncellenmelidir.
 *
 * Sunucu tarafındaki eşi: server-plugins/cevher-auth/.../TicketProof.java (biçim aynı kalmalı).
 */
public final class JoinProof {
	private static final Logger LOG = LoggerFactory.getLogger("cevher_session");
	private static final String KEY_PREFIX = "cevherauth:proof/";
	private static final String SORGU_PREFIX = "cevherauth:sorgu/";
	private static final String SORGU_BAGLAM = "cevherauth-sorgu-v1|";
	private static final String CONTEXT_V2 = "cevherauth-proof-v2|";
	/** Cookie yanıtı en fazla 5120 bayt; HMAC ve önek için pay bırakılır. */
	private static final int MAX_MODS_BYTES = 4800;
	private static volatile String modList;
	private static final Pattern NONCE = Pattern.compile("[0-9a-f]{32}");
	private static final Pattern SECRET = Pattern.compile("[A-Za-z0-9_-]{32,128}");
	private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

	private static volatile String username;
	private static volatile String secret;

	private JoinProof() {
	}

	/** Launcher'ın bıraktığı bilet sırrını okur ve dosyayı siler. Dosya yoksa hiçbir şey yapmaz. */
	static void load(Path gameDir) {
		Path file = gameDir.resolve(".cevher").resolve("join-ticket.json");
		if (!Files.isRegularFile(file)) {
			return;
		}
		try {
			JsonObject o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			String name = o.has("username") ? o.get("username").getAsString() : null;
			String s = o.has("secret") ? o.get("secret").getAsString() : null;
			if (name != null && s != null && USERNAME.matcher(name).matches() && SECRET.matcher(s).matches()) {
				username = name;
				secret = s;
			} else {
				LOG.warn("join-ticket.json geçersiz; bilet kanıtı gönderilmeyecek");
			}
		} catch (Exception e) {
			LOG.warn("join-ticket.json okunamadı: {}", e.toString());
		} finally {
			try {
				Files.deleteIfExists(file);
			} catch (Exception e) {
				LOG.warn("join-ticket.json silinemedi: {}", e.toString());
			}
		}
	}

	/**
	 * Cookie isteğine (login ya da Velocity arkasında configuration aşaması) verilecek kanıt; istek CevherAuth kanıt isteği değilse
	 * veya sır yoksa null (oyun normal davranır).
	 */
	public static byte[] respond(String cookieKey) {
		String s = secret;
		String name = username;
		if (s != null && name != null && cookieKey != null && cookieKey.startsWith(SORGU_PREFIX)) {
			return sorguYanit(s, name, cookieKey.substring(SORGU_PREFIX.length()));
		}
		if (s == null || name == null || cookieKey == null || !cookieKey.startsWith(KEY_PREFIX)) {
			return null;
		}
		String nonce = cookieKey.substring(KEY_PREFIX.length());
		if (!NONCE.matcher(nonce).matches()) {
			return null;
		}
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(s.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			String mods = modList();
			byte[] sig = mac.doFinal((CONTEXT_V2 + nonce + "|" + name.toLowerCase(Locale.ROOT) + "|" + mods).getBytes(StandardCharsets.UTF_8));
			byte[] modBytes = mods.getBytes(StandardCharsets.UTF_8);
			byte[] out = new byte[1 + sig.length + modBytes.length];
			out[0] = 2;
			System.arraycopy(sig, 0, out, 1, sig.length);
			System.arraycopy(modBytes, 0, out, 1 + sig.length, modBytes.length);
			return out;
		} catch (GeneralSecurityException e) {
			LOG.error("HmacSHA256 kullanılamıyor", e);
			return null;
		}
	}

	/**
	 * Oyun içi sorgu (CevherAuth 1.9+ OyunSorgu): {@code [0x01][HMAC(sır, "cevherauth-sorgu-v1|nonce|ad|durum")][durum]}.
	 * Durum: {@code m=<giriş mod listesinin sha1'i>} ve {@link Bekci#durum()} bayrakları.
	 */
	private static byte[] sorguYanit(String s, String name, String nonce) {
		if (!NONCE.matcher(nonce).matches()) {
			return null;
		}
		try {
			String durum = "m=" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(modList().getBytes(StandardCharsets.UTF_8)))
				+ Bekci.durum();
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(s.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] sig = mac.doFinal((SORGU_BAGLAM + nonce + "|" + name.toLowerCase(Locale.ROOT) + "|" + durum).getBytes(StandardCharsets.UTF_8));
			byte[] d = durum.getBytes(StandardCharsets.UTF_8);
			byte[] out = new byte[1 + sig.length + d.length];
			out[0] = 1;
			System.arraycopy(sig, 0, out, 1, sig.length);
			System.arraycopy(d, 0, out, 1 + sig.length, d.length);
			return out;
		} catch (GeneralSecurityException e) {
			return null;
		}
	}

	/**
	 * Yüklü üst düzey modların bildirimi: sıralı {@code kimlik@sha1} girdileri, virgülle ayrılmış.
	 * Karar sunucuda hash'e göre verilir (kimlik yalnızca günlük içindir): bir modun adını ya da
	 * kimliğini değiştiren araçlar jar'ın hash'ini de değiştirir. Jar dışından yüklenen mod
	 * {@code kimlik@dir}, JVM'e eklenmiş bir Java agent'ı {@code !agent} olarak bildirilir.
	 * Sığmazsa önce kimlikler atılır ({@code @sha1}), yine sığmazsa liste {@code …+N} ile kesilir.
	 * Oyun boyunca değişmediği için bir kez hesaplanır.
	 */
	static synchronized String modList() {
		String cached = modList;
		if (cached != null) {
			return cached;
		}
		List<String[]> mods = new ArrayList<>();
		for (ModContainer m : FabricLoader.getInstance().getAllMods()) {
			String id = m.getMetadata().getId();
			if (m.getContainingMod().isPresent() || "builtin".equals(m.getMetadata().getType())
				|| id.equals("fabricloader") || id.equals("minecraft") || id.equals("java")) {
				continue;
			}
			mods.add(new String[] {id.replaceAll("[,@!]", ""), hashOf(m)});
		}
		mods.sort((a, b) -> a[1].compareTo(b[1]));
		List<String> flags = new ArrayList<>(Bekci.bayraklar());
		for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
			String a = arg.toLowerCase(Locale.ROOT);
			if (a.startsWith("-javaagent") || a.startsWith("-agentlib") || a.startsWith("-agentpath")) {
				flags.add("!agent");
				break;
			}
		}
		String full = join(mods, flags, true);
		if (full.getBytes(StandardCharsets.UTF_8).length > MAX_MODS_BYTES) {
			full = join(mods, flags, false);
		}
		if (full.getBytes(StandardCharsets.UTF_8).length > MAX_MODS_BYTES) {
			StringBuilder sb = new StringBuilder(String.join(",", flags));
			int written = 0;
			for (String[] m : mods) {
				String e = "@" + m[1];
				if (sb.length() + e.length() + 12 > MAX_MODS_BYTES) {
					break;
				}
				if (sb.length() > 0) {
					sb.append(',');
				}
				sb.append(e);
				written++;
			}
			full = sb.append(",…+").append(mods.size() - written).toString();
		}
		modList = full;
		return full;
	}

	private static String join(List<String[]> mods, List<String> flags, boolean withIds) {
		List<String> parts = new ArrayList<>(flags);
		for (String[] m : mods) {
			parts.add((withIds ? m[0] : "") + "@" + m[1]);
		}
		return String.join(",", parts);
	}

	/** Modun jar dosyasının SHA-1'i (küçük harf onaltılık); jar değilse "dir". */
	private static String hashOf(ModContainer m) {
		try {
			if (m.getOrigin().getKind() != ModOrigin.Kind.PATH || m.getOrigin().getPaths().size() != 1) {
				return "dir";
			}
			Path p = m.getOrigin().getPaths().get(0);
			if (!Files.isRegularFile(p)) {
				return "dir";
			}
			MessageDigest d = MessageDigest.getInstance("SHA-1");
			try (InputStream in = Files.newInputStream(p)) {
				byte[] buf = new byte[65536];
				for (int n; (n = in.read(buf)) > 0; ) {
					d.update(buf, 0, n);
				}
			}
			return HexFormat.of().formatHex(d.digest());
		} catch (Exception e) {
			LOG.warn("{} hash'i alınamadı: {}", m.getMetadata().getId(), e.toString());
			return "dir";
		}
	}
}
