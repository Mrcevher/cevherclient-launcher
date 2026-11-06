package net.cevherclient.client.module.impl;

import net.cevherclient.client.Keys;
import net.cevherclient.client.module.Module;
import net.cevherclient.client.module.ModuleManager;
import net.cevherclient.client.module.Option;
import net.minecraft.client.Minecraft;

/**
 * Yakınlaştırma tuşu (varsayılan C) basılıyken (ya da aç/kapa kipinde açıkken) görüş alanını yumuşakça daraltır.
 * Yakınlaşmışken fare tekerleği yakınlığı değiştirir ve fare hassasiyeti yakınlığa göre düşer (MouseHandlerMixin).
 */
public final class ZoomModule extends Module {
	private final Option.Range factor = add(new Option.Range("factor", "Yakınlaştırma", 4f, 1.5f, 10f, 0.5f));
	private final Option.Choice mode = add(new Option.Choice("mode", "Kip", 0, "Basılı tut", "Aç / kapa"));
	private final Option.Bool scroll = add(new Option.Bool("scroll", "Tekerlekle ayarla", true));
	private final Option.Bool sensitivity = add(new Option.Bool("sensitivity", "Hassasiyeti azalt", true));

	private float current = 1f;
	private long lastNanos = System.nanoTime();
	private boolean toggled;
	/** Tekerlekle verilen ek yakınlık çarpanı; yakınlaştırma bırakılınca sıfırlanır. */
	private float scrollFactor = 1f;

	public ZoomModule() {
		super("zoom", "Yakınlaştırma", "ZM", Category.VISUAL, true);
	}

	/** GameRendererMixin her kare görüş alanını buradan geçirir. */
	public static float applyFov(float fov) {
		ZoomModule zoom = ModuleManager.get(ZoomModule.class);
		return zoom == null ? fov : zoom.modify(fov);
	}

	/** Fare hareketinin bölüneceği değer (yakınlaşmışken > 1). */
	public static double sensitivityDivisor() {
		ZoomModule zoom = ModuleManager.get(ZoomModule.class);
		return zoom != null && zoom.isEnabled() && zoom.sensitivity.value ? Math.max(1f, zoom.current) : 1.0;
	}

	/** Fare tekerleği: yakınlaşmışken yakınlığı değiştirir ve true döner (hotbar kaymaz). */
	public static boolean onScroll(double amount) {
		ZoomModule zoom = ModuleManager.get(ZoomModule.class);
		if (zoom == null || !zoom.active() || !zoom.scroll.value || amount == 0) {
			return false;
		}
		zoom.scrollFactor = Math.max(0.25f, Math.min(5f, zoom.scrollFactor * (amount > 0 ? 1.25f : 0.8f)));
		return true;
	}

	@Override
	public void onTick(Minecraft mc) {
		while (Keys.ZOOM.consumeClick()) {
			if (mode.index == 1) {
				toggled = !toggled;
			}
		}
	}

	@Override
	protected void onDisable(Minecraft mc) {
		toggled = false;
	}

	private boolean active() {
		boolean wanted = mode.index == 0 ? Keys.ZOOM.isDown() : toggled;
		return isEnabled() && wanted && Minecraft.getInstance().screen == null;
	}

	private float modify(float fov) {
		long now = System.nanoTime();
		float dt = Math.min((now - lastNanos) / 1_000_000_000f, 0.1f);
		lastNanos = now;

		boolean active = active();
		if (!active) {
			scrollFactor = 1f;
		}
		float target = active ? Math.max(1f, factor.value * scrollFactor) : 1f;
		current += (target - current) * (1f - (float) Math.exp(-dt * 16f));
		if (!active && Math.abs(current - 1f) < 0.005f) {
			current = 1f;
		}
		return current == 1f ? fov : fov / current;
	}
}
