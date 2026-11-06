package net.cevherclient.auth;

import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hesaplar: plugins/CevherAuth/accounts.json. Ad karşılaştırması büyük/küçük harf
 * duyarsızdır ("Arda" ile "arda" aynı hesap); oyuna girişte ise kayıtlı yazım zorunludur.
 */
final class AccountStore {
	/** Gson tarafından okunup yazılır; alan adları dosya biçimidir. */
	static final class Account {
		String username;
		String passwordHash;
		long createdAt;
		String registerIp;
		/**
		 * Sitedeki (LeaderOS) hesap bu launcher hesabıyla aynı şifreyle açıldı/doğrulandı.
		 * Yalnızca true iken siteden şifre devralınır; eski kayıtlarda alan yoktur (false).
		 */
		boolean siteSynced;
		/** Şifre değişince/hesap silinince artar; oturum üretimi bunu yeniden doğrular (dosyaya yazılmaz). */
		transient volatile long credVersion;

		Account(String username, String passwordHash, long createdAt, String registerIp) {
			this.username = username;
			this.passwordHash = passwordHash;
			this.createdAt = createdAt;
			this.registerIp = registerIp;
		}
	}

	private final Path file;
	private final Map<String, Account> byKey = new ConcurrentHashMap<>();

	AccountStore(Path file) {
		this.file = file;
	}

	static String key(String username) {
		return username.toLowerCase(Locale.ROOT);
	}

	void load() throws IOException {
		List<Account> list = JsonFile.read(file, new TypeToken<List<Account>>() {}.getType(), new ArrayList<>());
		byKey.clear();
		for (Account a : list) {
			if (a != null && a.username != null && a.passwordHash != null) {
				byKey.put(key(a.username), a);
			}
		}
	}

	Account get(String username) {
		return byKey.get(key(username));
	}

	int size() {
		return byKey.size();
	}

	long countByIp(String ip) {
		return byKey.values().stream().filter(a -> ip.equals(a.registerIp)).count();
	}

	List<Account> all() {
		List<Account> list = new ArrayList<>(byKey.values());
		list.sort(Comparator.comparingLong(a -> a.createdAt));
		return list;
	}

	/** Ad boştaysa hesabı oluşturur; doluysa null döner. */
	synchronized Account create(String username, String passwordHash, String ip) throws IOException {
		return create(username, passwordHash, ip, false);
	}

	synchronized Account create(String username, String passwordHash, String ip, boolean siteSynced) throws IOException {
		if (byKey.containsKey(key(username))) {
			return null;
		}
		Account account = new Account(username, passwordHash, System.currentTimeMillis(), ip);
		account.siteSynced = siteSynced;
		byKey.put(key(username), account);
		try {
			save();
		} catch (IOException | RuntimeException e) {
			byKey.remove(key(username));
			throw e;
		}
		return account;
	}

	/** Site senkron işaretini değiştirir; değer aynıysa dosyaya yazılmaz. */
	synchronized void setSiteSynced(String username, boolean synced) throws IOException {
		Account account = byKey.get(key(username));
		if (account == null || account.siteSynced == synced) {
			return;
		}
		account.siteSynced = synced;
		try {
			save();
		} catch (IOException | RuntimeException e) {
			account.siteSynced = !synced;
			throw e;
		}
	}

	/** Hesap hâlâ kayıtlı ve kimlik bilgisi sürümü değişmediyse true. */
	boolean isCurrent(Account account, long version) {
		return byKey.get(key(account.username)) == account && account.credVersion == version;
	}

	/** Yazım başarısız olursa bellekteki şifre eski haline döner (sürüm yine de artar; oturumlar kapanır). */
	synchronized boolean setPassword(String username, String passwordHash) throws IOException {
		// Launcher/yönetici şifre değişikliği siteye yazılamaz: site şifresi eski kalır, bu yüzden
		// siteden şifre devralma kapatılır (eski şifreyle hesabın geri alınmasını önler).
		return setPassword(username, passwordHash, false);
	}

	synchronized boolean setPassword(String username, String passwordHash, boolean siteSynced) throws IOException {
		Account account = byKey.get(key(username));
		if (account == null) {
			return false;
		}
		String previous = account.passwordHash;
		boolean previousSynced = account.siteSynced;
		account.passwordHash = passwordHash;
		account.siteSynced = siteSynced;
		account.credVersion++;
		try {
			save();
		} catch (IOException | RuntimeException e) {
			account.passwordHash = previous;
			account.siteSynced = previousSynced;
			throw e;
		}
		return true;
	}

	synchronized boolean remove(String username) throws IOException {
		Account account = byKey.remove(key(username));
		if (account == null) {
			return false;
		}
		account.credVersion++;
		try {
			save();
		} catch (IOException | RuntimeException e) {
			byKey.put(key(username), account);
			throw e;
		}
		return true;
	}

	private void save() throws IOException {
		JsonFile.write(file, all());
	}
}
