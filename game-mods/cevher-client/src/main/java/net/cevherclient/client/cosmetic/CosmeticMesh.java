package net.cevherclient.client.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.util.Optional;
import net.cevherclient.client.CevherClientMod;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

/**
 * OdinMC biçimindeki kozmetik ağı: {@code q} dörtgen köşeleri (dörtgen başına 4 köşe × x, y, z; blok birimi,
 * y yukarı, orijin boyun), {@code c} dörtgen başına bir ARGB renk. Dosyalar büyük olabildiği için (kanatlar
 * ~370 KB) ilk çizimde yüklenir; yüklenemezse kozmetik sessizce çizilmez.
 */
public final class CosmeticMesh {
	private final Identifier path;
	private float[] quads;
	private int[] colors;
	private boolean failed;

	CosmeticMesh(Identifier path) {
		this.path = path;
	}

	/** Yüklüyse true; ilk çağrıda dosyayı okur. */
	public boolean ready() {
		if (quads == null && !failed) {
			load();
		}
		return quads != null;
	}

	public float[] quads() {
		return quads;
	}

	public int[] colors() {
		return colors;
	}

	private void load() {
		try {
			Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(path);
			if (resource.isEmpty()) {
				throw new IOException("Kaynak bulunamadı: " + path);
			}
			JsonObject json;
			try (Reader reader = resource.get().openAsReader()) {
				json = JsonParser.parseReader(reader).getAsJsonObject();
			}
			JsonArray q = json.getAsJsonArray("q");
			JsonArray c = json.getAsJsonArray("c");
			if (q.size() != c.size() * 12) {
				throw new IOException("Köşe ve renk sayısı uyuşmuyor: " + q.size() + " / " + c.size());
			}
			float[] loadedQuads = new float[q.size()];
			for (int i = 0; i < loadedQuads.length; i++) {
				loadedQuads[i] = q.get(i).getAsFloat();
			}
			int[] loadedColors = new int[c.size()];
			for (int i = 0; i < loadedColors.length; i++) {
				// Ağlar opaktır; dosyadaki saydamlık yok sayılır.
				loadedColors[i] = c.get(i).getAsInt() | 0xFF000000;
			}
			colors = loadedColors;
			quads = loadedQuads;
		} catch (IOException | RuntimeException e) {
			failed = true;
			CevherClientMod.LOGGER.warn("Kozmetik ağı yüklenemedi: {}", path, e);
		}
	}
}
