package net.cevherclient.client.vaka;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.cevherclient.client.CevherClientMod;
import net.fabricmc.loader.api.FabricLoader;

/** Vaka menüsünün kişisel görünüm ayarları: config/cevherclient/vaka-gorunum.json. */
final class Gorunum {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Arka plan dosyası (config/cevherclient/vaka-arkaplan/ içine kopyalanır); boşsa oyunun bulanık görüntüsü. */
	String arkaplan = "";
	/** Arka planın üstüne binen karartma, 0..0.9 */
	float karartma = 0.45f;
	/** Panellerin opaklığı, 0.25..0.97 */
	float panel = 0.78f;
	/** Yazı ölçeği, 0.75..2.0 */
	float yazi = 1.0f;

	private static Gorunum ornek;

	static Path klasor() {
		return FabricLoader.getInstance().getConfigDir().resolve("cevherclient");
	}

	static Path medyaKlasoru() {
		return klasor().resolve("vaka-arkaplan");
	}

	static Gorunum al() {
		if (ornek == null) {
			ornek = new Gorunum();
			Path f = klasor().resolve("vaka-gorunum.json");
			try {
				if (Files.isReadable(f)) {
					Gorunum g = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Gorunum.class);
					if (g != null) {
						ornek = g;
					}
				}
			} catch (Exception e) {
				CevherClientMod.LOGGER.warn("vaka-gorunum.json okunamadı: {}", e.toString());
			}
			ornek.sinirla();
		}
		return ornek;
	}

	void sinirla() {
		if (arkaplan == null) {
			arkaplan = "";
		}
		karartma = Math.max(0f, Math.min(0.9f, karartma));
		panel = Math.max(0.25f, Math.min(0.97f, panel));
		yazi = Math.max(0.75f, Math.min(2.0f, yazi));
	}

	void kaydet() {
		sinirla();
		try {
			Files.createDirectories(klasor());
			Files.writeString(klasor().resolve("vaka-gorunum.json"), GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (Exception e) {
			CevherClientMod.LOGGER.warn("vaka-gorunum.json yazılamadı: {}", e.toString());
		}
	}
}
