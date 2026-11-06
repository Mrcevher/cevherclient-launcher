package net.cevherclient.auth;

import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Launcher oturumları ve giriş biletleri.
 *
 * - Oturum: launcher'da şifreyle giriş yapınca verilen uzun ömürlü belirteç. Diskte
 *   (sessions.json) yalnızca SHA-256 özeti tutulur; sunucu yeniden başlasa da geçerlidir.
 *   Hesap başına oturum sayısı sınırlıdır; süre uzatmaları bellekte tutulur ve toplu yazılır.
 * - Bilet: Oyna'ya basınca oturumla alınan, tek kullanımlık, kısa ömürlü izin.
 *   Yalnızca bellekte tutulur.
 *
 * Oturum/bilet üretimi ile hesap iptali (şifre değişimi, silme) aynı kilit altında
 * sıralanır; iptalden sonra eski kimlik bilgisiyle yeni oturum veya bilet çıkmaz.
 */
final class SessionStore {
	static final class Session {
		String username;
		long expiresAt;

		Session(String username, long expiresAt) {
			this.username = username;
			this.expiresAt = expiresAt;
		}
	}

	record Ticket(String username, String ip, long expiresAt) {
	}

	enum TicketCheck {
		OK, MISSING, WRONG_IP, WRONG_NAME
	}

	private final Path file;
	private final AccountStore accounts;
	private final Map<String, Session> sessions = new ConcurrentHashMap<>();
	private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
	/** Bileti yeni tüketilmiş oyuncular: kısa süre yeniden bağlanabilirler. */
	private final Map<String, Ticket> recent = new ConcurrentHashMap<>();
	private boolean dirty;

	SessionStore(Path file, AccountStore accounts) {
		this.file = file;
		this.accounts = accounts;
	}

	void load() throws IOException {
		Map<String, Session> loaded = JsonFile.read(file, new TypeToken<HashMap<String, Session>>() {}.getType(), new HashMap<>());
		sessions.clear();
		long now = System.currentTimeMillis();
		loaded.forEach((hash, s) -> {
			if (s != null && s.username != null && s.expiresAt > now) {
				sessions.put(hash, s);
			}
		});
	}

	/**
	 * Yeni oturum açar ve belirtecin kendisini (yalnızca launcher'a gönderilir) döndürür.
	 * {@code version}, kimlik bilgisi doğrulanmadan önce okunan sürümdür; hesap bu arada
	 * silindiyse veya şifresi değiştiyse null döner.
	 */
	synchronized String create(AccountStore.Account account, long version, long lifetimeMs, int maxPerAccount) throws IOException {
		if (!accounts.isCurrent(account, version)) {
			return null;
		}
		String key = AccountStore.key(account.username);
		if (maxPerAccount > 0) {
			while (true) {
				String oldest = null;
				long oldestExp = Long.MAX_VALUE;
				int count = 0;
				for (Map.Entry<String, Session> e : sessions.entrySet()) {
					if (AccountStore.key(e.getValue().username).equals(key)) {
						count++;
						if (e.getValue().expiresAt < oldestExp) {
							oldestExp = e.getValue().expiresAt;
							oldest = e.getKey();
						}
					}
				}
				if (count < maxPerAccount || oldest == null) {
					break;
				}
				sessions.remove(oldest);
			}
		}
		String token = Passwords.randomToken();
		sessions.put(Passwords.sha256(token), new Session(account.username, System.currentTimeMillis() + lifetimeMs));
		save();
		return token;
	}

	/**
	 * Belirteç geçerliyse süresini uzatır ve oturumu döndürür; değilse null. Uzatma yalnızca
	 * bellekte yapılır (her istekte tüm dosya yeniden yazılmaz); {@link #flush()} diske yazar.
	 */
	synchronized Session touch(String token, long lifetimeMs) {
		String hash = Passwords.sha256(token);
		Session s = sessions.get(hash);
		if (s == null) {
			return null;
		}
		long now = System.currentTimeMillis();
		if (s.expiresAt <= now) {
			sessions.remove(hash);
			dirty = true;
			return null;
		}
		s.expiresAt = now + lifetimeMs;
		dirty = true;
		return s;
	}

	/**
	 * Oturumu doğrular ve aynı kilit altında bilet üretir; böylece iptalden sonra bilet çıkmaz.
	 * Oturum geçersizse veya hesap yoksa null döner.
	 */
	synchronized String issueTicket(String token, long sessionLifetimeMs, String ip, long ticketLifetimeMs) {
		Session session = touch(token, sessionLifetimeMs);
		if (session == null || accounts.get(session.username) == null) {
			return null;
		}
		tickets.put(AccountStore.key(session.username), new Ticket(session.username, ip, System.currentTimeMillis() + ticketLifetimeMs));
		return session.username;
	}

	synchronized void revoke(String token) throws IOException {
		if (sessions.remove(Passwords.sha256(token)) != null) {
			save();
		}
	}

	/** Şifre değişince / hesap silinince o hesabın tüm oturumlarını kapatır. */
	synchronized int revokeAll(String username) throws IOException {
		String key = AccountStore.key(username);
		int before = sessions.size();
		sessions.values().removeIf(s -> AccountStore.key(s.username).equals(key));
		tickets.remove(key);
		recent.remove(key);
		int removed = before - sessions.size();
		if (removed > 0) {
			save();
		}
		return removed;
	}

	/**
	 * Bağlanan oyuncunun biletini doğrular. Bilet yalnızca başarılı eşleşmede tüketilir;
	 * yanlış IP'den gelen biri gerçek oyuncunun biletini yakamaz.
	 *
	 * Bilet tüketildikten sonra {@code graceMs} süresince aynı oyuncu (ve IP) yeniden
	 * bağlanabilir; böylece bağlantı koptuğunda "Yeniden Bağlan" çalışır. Pay sabit süreli
	 * olup her bağlanışta uzamaz.
	 */
	TicketCheck consumeTicket(String name, String ip, boolean checkIp, long graceMs) {
		String key = AccountStore.key(name);
		long now = System.currentTimeMillis();
		Ticket t = tickets.get(key);
		if (t != null && t.expiresAt() <= now) {
			tickets.remove(key, t);
			t = null;
		}
		if (t == null) {
			Ticket r = recent.get(key);
			if (r == null) {
				return TicketCheck.MISSING;
			}
			if (r.expiresAt() <= now) {
				recent.remove(key, r);
				return TicketCheck.MISSING;
			}
			if (checkIp && !r.ip().equals(ip)) {
				return TicketCheck.WRONG_IP;
			}
			return r.username().equals(name) ? TicketCheck.OK : TicketCheck.WRONG_NAME;
		}
		if (checkIp && !t.ip().equals(ip)) {
			return TicketCheck.WRONG_IP;
		}
		if (!t.username().equals(name)) {
			return TicketCheck.WRONG_NAME;
		}
		if (!tickets.remove(key, t)) {
			return TicketCheck.MISSING;
		}
		if (graceMs > 0) {
			recent.put(key, new Ticket(t.username(), ip, now + graceMs));
		}
		return TicketCheck.OK;
	}

	/** Süresi dolmuş oturum ve biletleri temizler, bekleyen süre uzatmalarını diske yazar. */
	synchronized void cleanup() throws IOException {
		long now = System.currentTimeMillis();
		tickets.values().removeIf(t -> t.expiresAt() <= now);
		recent.values().removeIf(t -> t.expiresAt() <= now);
		if (sessions.values().removeIf(s -> s.expiresAt <= now)) {
			dirty = true;
		}
		flush();
	}

	synchronized void flush() throws IOException {
		if (dirty) {
			save();
		}
	}

	private void save() throws IOException {
		JsonFile.write(file, new HashMap<>(sessions));
		dirty = false;
	}
}
