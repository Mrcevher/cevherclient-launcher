package net.cevherclient.uibridge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;

/**
 * cevherclient:hello içeriği: {"client":"cevherclient","mod":"1.0.0","mc":"1.21.11","protocol":1}
 * Oyuncu tarafından değiştirilebilir; yalnızca bilgi amaçlıdır.
 */
record ClientInfo(String mod, String mc, int protocol) {
	private static final int MAX_BYTES = 4096;

	/** Geçersiz ya da Cevherclient'a ait olmayan içerikte null döner. */
	static ClientInfo parse(byte[] message) {
		if (message.length == 0 || message.length > MAX_BYTES) {
			return null;
		}
		try {
			JsonElement root = JsonParser.parseString(new String(message, StandardCharsets.UTF_8));
			if (!root.isJsonObject()) {
				return null;
			}
			JsonObject body = root.getAsJsonObject();
			if (!"cevherclient".equals(text(body, "client"))) {
				return null;
			}
			int protocol = body.has("protocol") ? body.get("protocol").getAsInt() : 0;
			return new ClientInfo(clean(text(body, "mod")), clean(text(body, "mc")), protocol);
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static String text(JsonObject body, String key) {
		JsonElement value = body.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
	}

	/** Log'a ve sohbete yazılacağı için yalnızca sürüm karakterleri, en çok 32. */
	private static String clean(String value) {
		String safe = value.replaceAll("[^A-Za-z0-9._+\\-]", "");
		return safe.length() > 32 ? safe.substring(0, 32) : safe;
	}
}
