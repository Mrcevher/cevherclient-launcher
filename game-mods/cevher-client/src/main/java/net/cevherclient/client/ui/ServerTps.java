package net.cevherclient.client.ui;

import java.util.ArrayDeque;

/**
 * Sunucunun saniyedeki tick sayısını (TPS) tahmin eder. Sunucu her 20 tickte bir saat paketi gönderir;
 * iki paket arasında geçen gerçek süre ile oyun zamanındaki artış karşılaştırılır. Son birkaç ölçümün
 * ortalaması alınır, böylece tek bir gecikmeli paket değeri zıplatmaz.
 */
public final class ServerTps {
	private static final int SAMPLES = 5;
	private static final ArrayDeque<Float> SAMPLE_QUEUE = new ArrayDeque<>();
	private static long lastNanos = -1;
	private static long lastGameTime = -1;

	private ServerTps() {
	}

	/** ClientPacketListenerMixin her saat paketinde çağırır. */
	public static void onTimePacket(long gameTime) {
		long now = System.nanoTime();
		if (lastNanos > 0 && gameTime > lastGameTime) {
			float seconds = (now - lastNanos) / 1_000_000_000f;
			if (seconds > 0.05f) {
				SAMPLE_QUEUE.addLast(Math.min(20f, (gameTime - lastGameTime) / seconds));
				while (SAMPLE_QUEUE.size() > SAMPLES) {
					SAMPLE_QUEUE.removeFirst();
				}
			}
		}
		lastNanos = now;
		lastGameTime = gameTime;
	}

	public static float tps() {
		if (SAMPLE_QUEUE.isEmpty()) {
			return 20f;
		}
		float sum = 0f;
		for (float sample : SAMPLE_QUEUE) {
			sum += sample;
		}
		return sum / SAMPLE_QUEUE.size();
	}

	/** Yeni sunucuya bağlanınca eski ölçümler atılır. */
	public static void reset() {
		SAMPLE_QUEUE.clear();
		lastNanos = -1;
		lastGameTime = -1;
	}
}
