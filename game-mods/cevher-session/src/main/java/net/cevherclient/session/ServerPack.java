package net.cevherclient.session;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Launcher'ın önceden kurduğu sunucu kaynak paketi.
 *
 * Launcher, sunucunun paketini Oyna'da indirip {@code resourcepacks/cevherclient-server.zip}
 * olarak koyar, options.txt'de etkinleştirir ve SHA-1'ini {@code .cevher/server-pack.json}
 * dosyasına yazar. Sunucu aynı SHA-1'le paket gönderdiğinde paket zaten yüklü olduğundan mod
 * indirmeden ve kaynakları yeniden yüklemeden "yüklendi" yanıtı verir.
 *
 * Launcher tarafındaki eşi: src/main/services/launch/server-pack.ts (biçim aynı kalmalı).
 */
public final class ServerPack {
	private static final Logger LOG = LoggerFactory.getLogger("cevher_session");
	private static final String PACK_ID = "file/cevherclient-server.zip";
	private static final Pattern SHA1 = Pattern.compile("[0-9a-f]{40}");

	private static volatile String sha1;

	private ServerPack() {
	}

	static void load(Path gameDir) {
		Path file = gameDir.resolve(".cevher").resolve("server-pack.json");
		if (!Files.isRegularFile(file)) {
			return;
		}
		try {
			JsonObject o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			String s = o.has("sha1") ? o.get("sha1").getAsString().toLowerCase(Locale.ROOT) : null;
			if (s != null && SHA1.matcher(s).matches()) {
				sha1 = s;
			}
		} catch (Exception e) {
			LOG.warn("server-pack.json okunamadı: {}", e.toString());
		}
	}

	/** Sunucunun gönderdiği paket, önceden kurulan ve şu an etkin olan paketle aynı mı? */
	public static boolean isPreinstalled(String hash) {
		String s = sha1;
		if (s == null || hash == null || !s.equals(hash.trim().toLowerCase(Locale.ROOT))) {
			return false;
		}
		// Oyuncu paketi kaynak paketi menüsünden kapattıysa sunucunun paketi normal yoldan yüklensin.
		return Minecraft.getInstance().getResourcePackRepository().getSelectedIds().contains(PACK_ID);
	}
}
