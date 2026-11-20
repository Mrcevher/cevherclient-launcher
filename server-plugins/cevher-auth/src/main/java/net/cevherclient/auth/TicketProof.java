package net.cevherclient.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Giriş biletinin, onu alan oyun istemcisi tarafından kullanıldığının kanıtı.
 *
 * Launcher /api/join ile bilete özel rastgele bir sır alır ve oyuna (cevher-session modu)
 * verir. Oyuncu bağlanırken sunucu login aşamasında {@code cevherauth:proof/<nonce>}
 * anahtarlı bir cookie ister; mod bu isteğe HMAC-SHA256(sır, "cevherauth-proof-v1|nonce|ad")
 * ile yanıt verir. Sır hiçbir zaman Minecraft bağlantısından geçmez; nonce her bağlanışta
 * yenidir, yani gözlenen bir kanıt tekrar kullanılamaz. Aynı IP'yi paylaşan ya da adı bilen
 * biri sırrı bilmediği için bileti kullanamaz.
 *
 * İstemci tarafındaki eşi: game-mods/cevher-session/.../JoinProof.java (biçim aynı kalmalı).
 */
final class TicketProof {
	/** Cookie anahtarının yolu: {@code proof/<nonce>}; ad alanı eklentinin adıdır (cevherauth). */
	static final String KEY_PREFIX = "proof/";
	private static final String CONTEXT = "cevherauth-proof-v1|";
	private static final SecureRandom RNG = new SecureRandom();

	private TicketProof() {
	}

	/** NamespacedKey yoluna uygun (küçük harf onaltılık) 128 bit nonce. */
	static String newNonce() {
		byte[] bytes = new byte[16];
		RNG.nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}

	static byte[] expected(String secret, String nonce, String name) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return mac.doFinal((CONTEXT + nonce + "|" + name.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("HmacSHA256 kullanılamıyor", e);
		}
	}

	private static final String CONTEXT_V2 = "cevherauth-proof-v2|";
	/** Kanıt v2: [0x02][32 bayt HMAC][UTF-8 mod listesi]. */
	private static final byte V2 = 2;

	/** Kanıt sonucu: geçerli mi ve (v2 ise) oyunun gönderdiği mod listesi. */
	record Result(boolean valid, String mods) {
	}

	/**
	 * v1 (yalnızca 32 bayt HMAC) ve v2 (HMAC + mod listesi) yanıtlarını doğrular. v2'de HMAC
	 * {@code "cevherauth-proof-v2|nonce|ad|modlar"} üzerinden hesaplanır.
	 */
	static Result check(String secret, String nonce, String name, byte[] proof) {
		if (secret == null || proof == null || proof.length == 0) {
			return new Result(false, null);
		}
		if (proof.length > 32 && proof[0] == V2) {
			byte[] mac = java.util.Arrays.copyOfRange(proof, 1, 33);
			String mods = new String(proof, 33, proof.length - 33, StandardCharsets.UTF_8);
			byte[] exp = hmac(secret, CONTEXT_V2 + nonce + "|" + name.toLowerCase(Locale.ROOT) + "|" + mods);
			return MessageDigest.isEqual(exp, mac) ? new Result(true, mods) : new Result(false, null);
		}
		return new Result(verify(secret, nonce, name, proof), null);
	}

	private static byte[] hmac(String secret, String message) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("HmacSHA256 kullanılamıyor", e);
		}
	}

	/** Sabit zamanlı karşılaştırma; boş/eksik yanıt reddedilir. */
	static boolean verify(String secret, String nonce, String name, byte[] proof) {
		return secret != null && proof != null && proof.length > 0 && MessageDigest.isEqual(expected(secret, nonce, name), proof);
	}
}
