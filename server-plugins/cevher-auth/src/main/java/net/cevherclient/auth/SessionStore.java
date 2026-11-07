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

	/** {@code secret}: bilete özel sır; yalnızca launcher'a verilir, oyun istemcisi bununla kanıt üretir. */
	record Ticket(String username, String ip, long expiresAt, String secret) {
	}

	/** /api/join yanıtı: doğrulanan hesap adı ve bilete özel sır. */
	record IssuedTicket(String username, String secret) {
	}

	enum TicketCheck {
		OK, MISSING, WRONG_IP, WRONG_NAME
	}

	/**
	 * {@link #findTicket} sonucu. {@code ticket} yalnızca {@code check == OK} iken doludur;
	 * {@code grace} true ise yeniden bağlanma payından gelen kayıttır.
	 */
	record TicketLookup(TicketCheck check, Ticket ticket, boolean grace) {
		static TicketLookup of(TicketCheck check) {
			return new TicketLookup(check, null, false);
		}
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
	synchronized IssuedTicket issueTicket(String token, long sessionLifetimeMs, String ip, long ticketLifetimeMs) {
		Session session = touch(token, sessionLifetimeMs);
		if (session == null || accounts.get(session.username) == null) {
			return null;
		}
		String secret = Passwords.randomToken();
		tickets.put(AccountStore.key(session.username), new Ticket(session.username, ip, System.currentTimeMillis() + ticketLifetimeMs, secret));
		return new IssuedTicket(session.username, secret);
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
	 * Bağlanan oyuncunun biletini bulur ama tüketmez: önce istemcinin bilet sırrını bildiği
	 * kanıtlanır ({@link TicketProof}), sonra {@link #confirm} ile tüketilir. Böylece adı bilen
	 * ya da aynı IP'yi paylaşan biri gerçek oyuncunun biletini ne kullanabilir ne de yakabilir.
	 *
	 * Canlı bilet yoksa yeniden bağlanma payına bakılır: bilet tüketildikten sonra
	 * {@code graceMs} süresince aynı oyuncu (aynı sırla, gerekirse aynı IP'den) yeniden
	 * bağlanabilir; böylece bağlantı koptuğunda "Yeniden Bağlan" çalışır. Pay sabit süreli
	 * olup her bağlanışta uzamaz.
	 */
	/** Yalnızca teşhis/log için: adın bekleyen ya da yeniden bağlanma payındaki biletinin IP'si; yoksa null. */
	String pendingTicketIp(String name) {
		String key = AccountStore.key(name);
		Ticket t = tickets.get(key);
		if (t == null) {
			t = recent.get(key);
		}
		return t == null ? null : t.ip();
	}

	TicketLookup findTicket(String name, String ip, boolean checkIp) {
		String key = AccountStore.key(name);
		long now = System.currentTimeMillis();
		Ticket t = tickets.get(key);
		if (t != null && t.expiresAt() <= now) {
			tickets.remove(key, t);
			t = null;
		}
		boolean grace = false;
		if (t == null) {
			t = recent.get(key);
			if (t == null) {
				return TicketLookup.of(TicketCheck.MISSING);
			}
			if (t.expiresAt() <= now) {
				recent.remove(key, t);
				return TicketLookup.of(TicketCheck.MISSING);
			}
			grace = true;
		}
		if (checkIp && !t.ip().equals(ip)) {
			return TicketLookup.of(TicketCheck.WRONG_IP);
		}
		if (!t.username().equals(name)) {
			return TicketLookup.of(TicketCheck.WRONG_NAME);
		}
		return new TicketLookup(TicketCheck.OK, t, grace);
	}

	/**
	 * Kanıtı doğrulanmış bileti tüketir. Canlı bilet atomik olarak silinir (aynı bileti iki
	 * bağlantı kullanamaz) ve yerine aynı sırla yeniden bağlanma payı açılır. Bu arada bilet
	 * değiştiyse, iptal edildiyse veya süresi dolduysa false döner.
	 */
	boolean confirm(TicketLookup found, String ip, long graceMs) {
		Ticket t = found.ticket();
		if (found.check() != TicketCheck.OK || t == null) {
			return false;
		}
		String key = AccountStore.key(t.username());
		long now = System.currentTimeMillis();
		if (found.grace()) {
			return recent.get(key) == t && t.expiresAt() > now;
		}
		if (!tickets.remove(key, t)) {
			return false;
		}
		if (graceMs > 0) {
			recent.put(key, new Ticket(t.username(), ip, now + graceMs, t.secret()));
		}
		return true;
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
