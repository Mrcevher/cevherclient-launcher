package net.cevherclient.client.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Ekrana çizilen modül. Konum, ekran boyutuna oranla (0..1) saklanır; böylece çözünürlük ya da
 * GUI ölçeği değişince modül aynı yerde kalır. İçerik kendi koordinat sisteminde (0,0 = sol üst) çizilir,
 * konum ve boyut dönüşümünü {@link net.cevherclient.client.ui.HudRenderer} uygular.
 */
public abstract class HudModule extends Module {
	public final Option.Range scale;
	private float x;
	private float y;

	protected HudModule(String id, String name, String badge, boolean enabledByDefault, float x, float y) {
		super(id, name, badge, Category.HUD, enabledByDefault);
		this.scale = add(new Option.Range("scale", "Boyut", 1f, 0.5f, 3f, 0.1f));
		this.x = x;
		this.y = y;
	}

	@Override
	public final boolean isHud() {
		return true;
	}

	/** Ölçeklenmemiş içerik genişliği (GUI pikseli). */
	public abstract int width(Minecraft mc, boolean preview);

	/** Ölçeklenmemiş içerik yüksekliği (GUI pikseli). */
	public abstract int height(Minecraft mc, boolean preview);

	/** (0,0) noktasını sol üst köşe sayarak çizer. {@code preview} düzenleyicide örnek veri göstermek içindir. */
	public abstract void draw(GuiGraphics graphics, Minecraft mc, boolean preview);

	public float xFraction() {
		return x;
	}

	public float yFraction() {
		return y;
	}

	public void setPosition(float x, float y) {
		this.x = Math.max(0f, Math.min(1f, x));
		this.y = Math.max(0f, Math.min(1f, y));
	}
}
