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

	/** Sabit zamanlı karşılaştırma; boş/eksik yanıt reddedilir. */
	static boolean verify(String secret, String nonce, String name, byte[] proof) {
		return secret != null && proof != null && proof.length > 0 && MessageDigest.isEqual(expected(secret, nonce, name), proof);
	}
}
