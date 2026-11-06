package net.cevherclient.auth;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Anahtar başına kayan pencereli sayaç (hatalı giriş, kayıt denemesi gibi). Anahtar sayısı sınırlıdır. */
final class Throttle {
	private static final int MAX_KEYS = 20_000;

	private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
	private final long windowMs;

	Throttle(long windowMs) {
		this.windowMs = windowMs;
	}

	private void prune(Deque<Long> q, long now) {
		long cutoff = now - windowMs;
		while (!q.isEmpty() && q.peekFirst() < cutoff) {
			q.pollFirst();
		}
	}

	int count(String key) {
		long now = System.currentTimeMillis();
		int[] size = new int[1];
		hits.computeIfPresent(key, (k, q) -> {
			prune(q, now);
			size[0] = q.size();
			return q.isEmpty() ? null : q;
		});
		return size[0];
	}

	void record(String key) {
		long now = System.currentTimeMillis();
		if (hits.size() >= MAX_KEYS && !hits.containsKey(key)) {
			cleanup();
			if (hits.size() >= MAX_KEYS) {
				return; // Bellek koruması: yeni anahtar izlenmez.
			}
		}
		hits.compute(key, (k, q) -> {
			Deque<Long> d = q != null ? q : new ArrayDeque<>();
			prune(d, now);
			d.addLast(now);
			return d;
		});
	}

	void clear(String key) {
		hits.remove(key);
	}

	void cleanup() {
		long now = System.currentTimeMillis();
		for (String key : hits.keySet()) {
			hits.computeIfPresent(key, (k, q) -> {
				prune(q, now);
				return q.isEmpty() ? null : q;
			});
		}
	}
}
