package net.cevherclient.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

/**
 * Kapı rolü (survival gibi arka sunucu): oyuncu yalnızca lobide giriş yapmışsa (launcher
 * biletiyle ya da oyunda şifreyle) ve hâlâ lobide bağlıysa içeri alınır. Velocity önce yeni
 * sunucuya bağlanıp sonra lobiden çıkardığı için bu sorgu oyuncu lobideyken yapılır.
 *
 * Lobiye ulaşılamazsa giriş reddedilir (kapalı başarısızlık).
 */
final class GateListener implements Listener {
	private final CevherAuthPlugin plugin;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

	GateListener(CevherAuthPlugin plugin) {
		this.plugin = plugin;
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPreLogin(AsyncPlayerPreLoginEvent event) {
		if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
			return; // Güvenlik katmanı (IP engeli / online sınırı) zaten reddetti.
		}
		String name = event.getName();
		String ip = CevherAuthPlugin.ipKey(event.getAddress());
		String url = plugin.getConfig().getString("network.lobby-api", "http://127.0.0.1:25580").replaceAll("/+$", "")
			+ "/api/internal/check";
		JsonObject body = new JsonObject();
		body.addProperty("secret", plugin.getConfig().getString("network.secret", ""));
		body.addProperty("name", name);
		body.addProperty("ip", ip);
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(4))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(JsonFile.GSON.toJson(body), StandardCharsets.UTF_8))
				.build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
			if (response.statusCode() == 200 && json.has("authorized") && json.get("authorized").getAsBoolean()) {
				if (json.has("risk") && json.get("risk").isJsonArray()) {
					plugin.riskTakip().iceAktar(name, ip, json.getAsJsonArray("risk"));
				}
				return;
			}
			if (response.statusCode() == 403) {
				plugin.getLogger().severe("Lobi API'si kapı sorgusunu reddetti: network.secret iki sunucuda aynı mı?");
			}
			plugin.getLogger().info(name + " lobide giriş yapmadan bağlanmaya çalıştı (" + ip + "), reddedildi");
			event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, plugin.message("gate-denied"));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, plugin.message("gate-unavailable"));
		} catch (Exception e) {
			plugin.getLogger().warning("Lobi API'sine ulaşılamadı (" + url + "): " + e.getMessage() + " — " + name + " reddedildi");
			event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, plugin.message("gate-unavailable"));
		}
	}
}
