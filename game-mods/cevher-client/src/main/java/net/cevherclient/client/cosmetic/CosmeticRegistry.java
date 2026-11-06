package net.cevherclient.client.cosmetic;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.cevherclient.client.CevherClientMod;
import net.minecraft.client.Minecraft;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Modun {@code assets/cevher_client/cosmetics/cosmetics.json} listesindeki kozmetikleri, ilk istendiğinde
 * yükler. Bir kozmetiğin yüklenememesi (bozuk model gibi) diğerlerini ve oyunu etkilemez.
 *
 * <p>Her kayıt bir tür alanı taşır: {@code model} (Blockbench + {@code texture}), {@code mesh} (OdinMC ağı) ya da
 * {@code cape} (tek doku) / {@code frames} + {@code frameCount} + {@code frameMs} (hareketli pelerin, yolda
 * {@code %02d} kare numarasıdır).
 */
public final class CosmeticRegistry {
	private static List<Cosmetic> loaded;

	private CosmeticRegistry() {
	}

	public static List<Cosmetic> all() {
		if (loaded == null) {
			loaded = load(Minecraft.getInstance().getResourceManager());
		}
		return loaded;
	}

	public static List<Cosmetic> forSlot(Cosmetic.Slot slot) {
		List<Cosmetic> result = new ArrayList<>();
		for (Cosmetic cosmetic : all()) {
			if (cosmetic.slot() == slot) {
				result.add(cosmetic);
			}
		}
		return result;
	}

	public static Cosmetic find(String id) {
		if (id == null) {
			return null;
		}
		for (Cosmetic cosmetic : all()) {
			if (cosmetic.id().equals(id)) {
				return cosmetic;
			}
		}
		return null;
	}

	private static List<Cosmetic> load(ResourceManager manager) {
		List<Cosmetic> result = new ArrayList<>();
		try {
			JsonObject index = readJson(manager, "cosmetics/cosmetics.json");
			for (JsonElement entry : index.getAsJsonArray("cosmetics")) {
				JsonObject object = entry.getAsJsonObject();
				String id = object.get("id").getAsString();
				try {
					result.add(parse(manager, id, object));
				} catch (IOException | RuntimeException e) {
					CevherClientMod.LOGGER.warn("Kozmetik yüklenemedi: {}", id, e);
				}
			}
		} catch (IOException | RuntimeException e) {
			CevherClientMod.LOGGER.warn("cosmetics.json okunamadı", e);
		}
		return Collections.unmodifiableList(result);
	}

	private static Cosmetic parse(ResourceManager manager, String id, JsonObject object) throws IOException {
		String name = object.get("name").getAsString();
		Cosmetic.Slot slot = Cosmetic.Slot.parse(object.get("slot").getAsString());
		Identifier icon = object.has("icon") ? asset(object.get("icon").getAsString()) : null;
		if (object.has("model")) {
			JsonObject model = readJson(manager, object.get("model").getAsString());
			return new Cosmetic.Model(id, name, slot, icon, BbModelLoader.load(model, id), asset(object.get("texture").getAsString()));
		}
		if (object.has("mesh")) {
			boolean flaps = object.has("flaps") && object.get("flaps").getAsBoolean();
			return new Cosmetic.Mesh(id, name, slot, icon, new CosmeticMesh(asset(object.get("mesh").getAsString())), flaps);
		}
		if (object.has("cape")) {
			return new Cosmetic.Cape(id, name, icon, new ClientAsset.Texture[] {capeTexture(id, 0, object.get("cape").getAsString())}, 1);
		}
		String pattern = object.get("frames").getAsString();
		int count = object.get("frameCount").getAsInt();
		ClientAsset.Texture[] frames = new ClientAsset.Texture[count];
		for (int i = 0; i < count; i++) {
			frames[i] = capeTexture(id, i, String.format(Locale.ROOT, pattern, i));
		}
		return new Cosmetic.Cape(id, name, icon, frames, Math.max(20, object.get("frameMs").getAsInt()));
	}

	private static ClientAsset.Texture capeTexture(String id, int frame, String path) {
		return new ClientAsset.ResourceTexture(Identifier.fromNamespaceAndPath(CevherClientMod.MOD_ID, "cape/" + id + "/" + frame), asset(path));
	}

	private static Identifier asset(String path) {
		return Identifier.fromNamespaceAndPath(CevherClientMod.MOD_ID, path);
	}

	private static JsonObject readJson(ResourceManager manager, String path) throws IOException {
		Optional<Resource> resource = manager.getResource(asset(path));
		if (resource.isEmpty()) {
			throw new IOException("Kaynak bulunamadı: " + path);
		}
		try (Reader reader = resource.get().openAsReader()) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		}
	}
}
