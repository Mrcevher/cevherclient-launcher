package net.cevherclient.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.cevherclient.client.CevherClientMod;
import net.cevherclient.client.cosmetic.Cosmetic;
import net.cevherclient.client.cosmetic.CosmeticSelection;
import net.cevherclient.client.module.HudModule;
import net.cevherclient.client.module.Module;
import net.cevherclient.client.module.ModuleManager;
import net.cevherclient.client.module.Option;
import net.fabricmc.loader.api.FabricLoader;

/** Modül durumlarını ve HUD konumlarını {@code config/cevherclient/client.json} dosyasında saklar. */
public final class ClientConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static boolean dirty;

	private ClientConfig() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("cevherclient").resolve("client.json");
	}

	public static void markDirty() {
		dirty = true;
	}

	/** Kaydedilmemiş değişiklik varsa dosyaya yazar. */
	public static void saveIfDirty() {
		if (dirty) {
			save();
		}
	}

	public static void load() {
		Path path = file();
		if (!Files.isRegularFile(path)) {
			return;
		}
		try {
			JsonElement parsed = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
			if (!parsed.isJsonObject()) {
				return;
			}
			JsonObject cosmetics = parsed.getAsJsonObject().getAsJsonObject("cosmetics");
			for (Cosmetic.Slot slot : Cosmetic.Slot.values()) {
				if (cosmetics != null && cosmetics.has(slot.key) && cosmetics.get(slot.key).isJsonPrimitive()) {
					CosmeticSelection.restore(slot, cosmetics.get(slot.key).getAsString());
				}
			}
			if (cosmetics != null && cosmetics.has("showOthers") && cosmetics.get("showOthers").isJsonPrimitive()) {
				CosmeticSelection.restoreShowOthers(cosmetics.get("showOthers").getAsBoolean());
			}
			JsonObject modules = parsed.getAsJsonObject().getAsJsonObject("modules");
			if (modules == null) {
				return;
			}
			for (Module module : ModuleManager.all()) {
				JsonObject entry = modules.has(module.id) && modules.get(module.id).isJsonObject()
					? modules.getAsJsonObject(module.id) : null;
				if (entry != null) {
					read(module, entry);
				}
			}
		} catch (IOException | RuntimeException e) {
			// Bozuk dosya oyunu durdurmasın; varsayılanlarla devam edilir ve bir sonraki kayıtta düzelir.
			CevherClientMod.LOGGER.warn("client.json okunamadı, varsayılanlar kullanılıyor", e);
		}
	}

	private static void read(Module module, JsonObject entry) {
		if (entry.has("enabled") && entry.get("enabled").isJsonPrimitive()) {
			module.restoreEnabledFromConfig(entry.get("enabled").getAsBoolean());
		}
		if (module instanceof HudModule hud) {
			if (entry.has("x") && entry.has("y")) {
				hud.setPosition(entry.get("x").getAsFloat(), entry.get("y").getAsFloat());
			}
		}
		JsonObject saved = entry.has("options") && entry.get("options").isJsonObject() ? entry.getAsJsonObject("options") : null;
		if (saved != null) {
			for (Option option : module.options()) {
				if (saved.has(option.id)) {
					option.load(saved.get(option.id));
				}
			}
		}
	}

	public static void save() {
		JsonObject modules = new JsonObject();
		for (Module module : ModuleManager.all()) {
			JsonObject entry = new JsonObject();
			entry.addProperty("enabled", module.isEnabled());
			if (module instanceof HudModule hud) {
				entry.addProperty("x", hud.xFraction());
				entry.addProperty("y", hud.yFraction());
			}
			JsonObject options = new JsonObject();
			for (Option option : module.options()) {
				options.add(option.id, option.save());
			}
			entry.add("options", options);
			modules.add(module.id, entry);
		}
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		root.add("modules", modules);
		JsonObject cosmetics = new JsonObject();
		for (Cosmetic.Slot slot : Cosmetic.Slot.values()) {
			if (CosmeticSelection.id(slot) != null) {
				cosmetics.addProperty(slot.key, CosmeticSelection.id(slot));
			}
		}
		cosmetics.addProperty("showOthers", CosmeticSelection.showOthers());
		root.add("cosmetics", cosmetics);
		Path path = file();
		try {
			Files.createDirectories(path.getParent());
			Path tmp = path.resolveSibling("client.json.tmp");
			Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
			Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			dirty = false;
		} catch (IOException e) {
			CevherClientMod.LOGGER.warn("client.json yazılamadı", e);
		}
	}
}
