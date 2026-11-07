package net.cevherclient.auth;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Sunucunun kaynak paketi (ör. Nexo'nun ürettiği pack.zip) hakkında launcher'a bilgi verir:
 * {@code GET /api/pack → { ok, enabled, sha1, size, url }}.
 *
 * Launcher paketi oyun açılmadan indirip normal bir kaynak paketi olarak etkinleştirir; sunucu
 * aynı paketi (aynı SHA-1) gönderdiğinde Cevherclient oturum modu indirmeden ve yeniden
 * yüklemeden "yüklendi" yanıtı verir. Böylece girişte ne onay ekranı çıkar ne de oyun kasar.
 *
 * Dosya bu API'den sunulmaz (HTTP sunucusunun yanıt süresi sınırı 26 MB'lık paketi kesebilir);
 * launcher paketi {@code resource-pack.url} adresinden indirir ve SHA-1'i doğrular.
 */
final class ServerPack {
	/** Bilgi; dosya değişmedikçe (boyut + değişme zamanı) SHA-1 yeniden hesaplanmaz. */
	private record Info(String sha1, long size, long modified) {
	}

	private final CevherAuthPlugin plugin;
	private volatile Info cached;

	ServerPack(CevherAuthPlugin plugin) {
		this.plugin = plugin;
	}

	JsonObject describe() throws IOException {
		JsonObject o = new JsonObject();
		o.addProperty("ok", true);
		String file = plugin.getConfig().getString("resource-pack.file", "");
		String urlTemplate = plugin.getConfig().getString("resource-pack.url", "");
		if (!plugin.getConfig().getBoolean("resource-pack.enabled", false) || file.isBlank() || urlTemplate.isBlank()) {
			o.addProperty("enabled", false);
			return o;
		}
		// Yol sunucu klasörüne (plugins/ klasörünün üstü) göredir.
		Path serverRoot = plugin.getDataFolder().toPath().toAbsolutePath().getParent().getParent();
		Path path = serverRoot.resolve(file).normalize();
		if (!Files.isRegularFile(path)) {
			o.addProperty("enabled", false);
			return o;
		}
		Info info = info(path);
		o.addProperty("enabled", true);
		o.addProperty("sha1", info.sha1());
		o.addProperty("size", info.size());
		o.addProperty("url", urlTemplate.replace("{sha1}", info.sha1()));
		return o;
	}

	private Info info(Path path) throws IOException {
		BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
		long modified = attrs.lastModifiedTime().toMillis();
		Info c = cached;
		if (c != null && c.size() == attrs.size() && c.modified() == modified) {
			return c;
		}
		synchronized (this) {
			c = cached;
			if (c != null && c.size() == attrs.size() && c.modified() == modified) {
				return c;
			}
			MessageDigest sha1;
			try {
				sha1 = MessageDigest.getInstance("SHA-1");
			} catch (NoSuchAlgorithmException e) {
				throw new IllegalStateException("SHA-1 kullanılamıyor", e);
			}
			long size = 0;
			try (InputStream in = Files.newInputStream(path)) {
				byte[] buf = new byte[64 * 1024];
				int n;
				while ((n = in.read(buf)) > 0) {
					sha1.update(buf, 0, n);
					size += n;
				}
			}
			c = new Info(HexFormat.of().formatHex(sha1.digest()), size, modified);
			cached = c;
			return c;
		}
	}
}
