package net.cevherclient.session;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Oyun çalışırken istemci bütünlüğünü izler; bulgular giriş kanıtına ve oyun içi sorgu yanıtına
 * ({@link JoinProof#sorguYanit}) bayrak olarak eklenir. Karar sunucuda verilir.
 *
 * <ul>
 *   <li>{@code +noattach}: oyun {@code -XX:+DisableAttachMechanism} ile açıldı (launcher 5.3.0+); çalışan JVM'e
 *   dışarıdan agent bağlanamaz.</li>
 *   <li>{@code !attach}: "Attach Listener" iş parçacığı var; biri çalışan oyuna bağlandı (hile enjektörü).</li>
 *   <li>{@code !sinif:<ad>}: bilinen bir hile istemcisinin ana sınıfı sınıf yolunda ya da mod kimliği yüklü.</li>
 *   <li>{@code !jvm:<ad>}: oyun bir hile launcher'ının JVM argümanlarıyla açıldı (ör. NUTclient: nutagent,
 *   -Dnutclient.launcher, -XX:+AllowEnhancedClassRedefinition).</li>
 * </ul>
 */
final class Bekci {
	/** Bilinen hile istemcilerinin ana sınıfları (kaynak yolu; sınıf yüklenmez). */
	private static final String[][] SINIFLAR = {
		{"meteor", "meteordevelopment/meteorclient/MeteorClient.class"},
		{"wurst", "net/wurstclient/WurstClient.class"},
		{"liquidbounce", "net/ccbluex/liquidbounce/LiquidBounce.class"},
		{"bleachhack", "org/bleachhack/BleachHack.class"},
		{"thunderhack", "thunder/hack/ThunderHack.class"},
		// NUTclient (NUTCRACKER-com): istemci ve oyun içinde hile jar'ı yükleyen Java agent'ı
		{"nutclient", "gg/nutcracker/nutclient/NutClient.class"},
		{"nutagent", "gg/nutcracker/nutagent/NutAgent.class"},
	};

	/** Bilinen hile istemcilerinin mod kimlikleri (Fabric). Tam eşleşme; "nut" gibi parça eşleşmesi yapılmaz. */
	private static final String[] MOD_KIMLIKLERI = {"nutclient"};

	private static final Set<String> BULGULAR = ConcurrentHashMap.newKeySet();
	private static volatile boolean noAttach;
	private static volatile boolean basladi;

	private Bekci() {
	}

	static synchronized void baslat() {
		if (basladi) {
			return;
		}
		basladi = true;
		for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
			String a = arg.trim();
			String k = a.toLowerCase(Locale.ROOT);
			if (a.equals("-XX:+DisableAttachMechanism")) {
				noAttach = true;
			}
			// NUTclient launcher'ının JVM imzaları: oyun içinde sınıf yeniden tanımlama (canlı hile yükleme) ve kendi agent'ı
			if (k.startsWith("-javaagent") && k.contains("nutagent")) {
				BULGULAR.add("!jvm:nutagent");
			}
			if (k.startsWith("-dnutclient.")) {
				BULGULAR.add("!jvm:nutclient");
			}
			if (a.equals("-XX:+AllowEnhancedClassRedefinition")) {
				BULGULAR.add("!jvm:redefine");
			}
		}
		if (System.getProperty("nutclient.launcher") != null) {
			BULGULAR.add("!jvm:nutclient");
		}
		tara();
		Thread t = new Thread(() -> {
			while (true) {
				try {
					Thread.sleep(5_000);
				} catch (InterruptedException e) {
					return;
				}
				tara();
			}
		}, "cevher-session-bekci");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		t.start();
	}

	private static void tara() {
		try {
			for (Thread th : Thread.getAllStackTraces().keySet()) {
				if ("Attach Listener".equals(th.getName())) {
					BULGULAR.add("!attach");
				}
			}
		} catch (RuntimeException ignored) {
			// Güvenlik yöneticisi vb. engellerse sessiz geç.
		}
		ClassLoader cl = Bekci.class.getClassLoader();
		ClassLoader sistem = ClassLoader.getSystemClassLoader();
		for (String[] s : SINIFLAR) {
			try {
				// Agent jar'ları sistem sınıf yoluna eklenir; ikisine de bak (sınıf yüklenmez, yalnızca kaynak aranır).
				if (cl.getResource(s[1]) != null || sistem.getResource(s[1]) != null) {
					BULGULAR.add("!sinif:" + s[0]);
				}
			} catch (RuntimeException ignored) {
				// yok say
			}
		}
		try {
			net.fabricmc.loader.api.FabricLoader fl = net.fabricmc.loader.api.FabricLoader.getInstance();
			for (String id : MOD_KIMLIKLERI) {
				if (fl.isModLoaded(id)) {
					BULGULAR.add("!sinif:" + id);
				}
			}
		} catch (RuntimeException | LinkageError ignored) {
			// yok say
		}
	}

	/** Girişte gönderilecek bayraklar (sıralı): yetenekler ve o ana kadarki bulgular. */
	static List<String> bayraklar() {
		baslat();
		List<String> out = new ArrayList<>();
		out.add("+sorgu");
		if (noAttach) {
			out.add("+noattach");
		}
		List<String> b = new ArrayList<>(BULGULAR);
		b.sort(null);
		out.addAll(b);
		return out;
	}

	/** Sorgu anındaki durum satırı (mod özeti hariç). */
	static String durum() {
		tara();
		StringBuilder sb = new StringBuilder();
		for (String f : bayraklar()) {
			if (f.equals("+sorgu")) {
				continue;
			}
			sb.append(',').append(f.toLowerCase(Locale.ROOT));
		}
		return sb.toString();
	}
}
