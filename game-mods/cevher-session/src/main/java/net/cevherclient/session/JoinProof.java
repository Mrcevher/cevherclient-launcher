package net.cevherclient.session;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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
 * Sunucu tarafındaki eşi: server-plugins/cevher-auth/.../TicketProof.java (biçim aynı kalmalı).
 */
public final class JoinProof {
	private static final Logger LOG = LoggerFactory.getLogger("cevher_session");
	private static final String KEY_PREFIX = "cevherauth:proof/";
	private static final String CONTEXT = "cevherauth-proof-v1|";
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
	 * Login aşamasındaki cookie isteğine verilecek kanıt; istek CevherAuth kanıt isteği değilse
	 * veya sır yoksa null (oyun normal davranır).
	 */
	public static byte[] respond(String cookieKey) {
		String s = secret;
		String name = username;
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
			return mac.doFinal((CONTEXT + nonce + "|" + name.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
		} catch (GeneralSecurityException e) {
			LOG.error("HmacSHA256 kullanılamıyor", e);
			return null;
		}
	}
}
