package net.cevherclient.client.ui;

import java.util.ArrayDeque;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Sol/sağ fare tuşu tıklarını sayar. Fare tuşu durumunu her karede yoklar ve basılma anlarını son
 * bir saniye için saklar; böylece saniyedeki tık sayısı (CPS) bir mixin olmadan hesaplanır.
 * Bir menü açıkken yapılan tıklar sayılmaz.
 */
public final class ClickTracker {
	private static final long WINDOW_MS = 1000;
	private static final ArrayDeque<Long>[] CLICKS = newQueues();
	private static final boolean[] DOWN = new boolean[2];

	private ClickTracker() {
	}

	@SuppressWarnings("unchecked")
	private static ArrayDeque<Long>[] newQueues() {
		return new ArrayDeque[] {new ArrayDeque<Long>(), new ArrayDeque<Long>()};
	}

	public static void poll(Minecraft mc) {
		long window = mc.getWindow().handle();
		long now = System.currentTimeMillis();
		for (int button = 0; button < 2; button++) {
			boolean down = GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS;
			if (down && !DOWN[button] && mc.screen == null) {
				CLICKS[button].addLast(now);
			}
			DOWN[button] = down;
			prune(CLICKS[button], now);
		}
	}

	public static int cps(int button) {
		prune(CLICKS[button], System.currentTimeMillis());
		return CLICKS[button].size();
	}

	private static void prune(ArrayDeque<Long> queue, long now) {
		while (!queue.isEmpty() && now - queue.peekFirst() > WINDOW_MS) {
			queue.removeFirst();
		}
	}
}
