package net.cevherclient.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Şifre ve belirteç yardımcıları. Şifreler PBKDF2-HMAC-SHA256 ile (rastgele tuz, yüksek
 * tekrar sayısı) saklanır; düz metin hiçbir yere yazılmaz. Biçim:
 *   pbkdf2_sha256$<tekrar>$<tuz-base64>$<anahtar-base64>
 */
final class Passwords {
	private static final SecureRandom RNG = new SecureRandom();
	private static final String PREFIX = "pbkdf2_sha256";
	private static final int ITERATIONS = 210_000;
	private static final int KEY_BITS = 256;

	private Passwords() {
	}

	static String hash(String password) {
		byte[] salt = new byte[16];
		RNG.nextBytes(salt);
		byte[] key = pbkdf2(password, salt, ITERATIONS);
		Base64.Encoder b64 = Base64.getEncoder();
		return PREFIX + "$" + ITERATIONS + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(key);
	}

	static boolean verify(String password, String stored) {
		String[] parts = stored.split("\\$");
		if (parts.length != 4 || !parts[0].equals(PREFIX)) {
			return false;
		}
		try {
			int iterations = Integer.parseInt(parts[1]);
			byte[] salt = Base64.getDecoder().decode(parts[2]);
			byte[] expected = Base64.getDecoder().decode(parts[3]);
			// Sabit zamanlı karşılaştırma: zamanlama saldırısına kapalı.
			return MessageDigest.isEqual(expected, pbkdf2(password, salt, iterations));
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	/** Tahmin edilemez oturum belirteci (256 bit). */
	static String randomToken() {
		byte[] bytes = new byte[32];
		RNG.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** Belirteçler diske SHA-256 özeti olarak yazılır; dosya çalınsa bile kullanılamaz. */
	static String sha256(String value) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
		try {
			PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
			try {
				return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
			} finally {
				spec.clearPassword();
			}
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}
}
