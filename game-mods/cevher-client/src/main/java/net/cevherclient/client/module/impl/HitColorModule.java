package net.cevherclient.client.module.impl;

import net.cevherclient.client.mixin.OverlayTextureAccessor;
import net.cevherclient.client.module.Module;
import net.cevherclient.client.module.Option;
import net.cevherclient.client.module.Palette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * Hasar alan varlıkların üstüne binen kırmızı rengi değiştirir. Oyun bu rengi 16×16'lık bir "kaplama dokusu"nun
 * üst yarısından okur; modül o yarıyı seçilen renk ve yoğunlukla yeniden boyar, kapanınca oyunun rengine döner.
 */
public final class HitColorModule extends Module {
	/** Oyunun kendi rengi: %70 opak kırmızı. */
	private static final int VANILLA = 0xB3FF0000;

	private final Option.Choice color = add(Palette.option("color", "Renk", 6));
	private final Option.Range intensity = add(new Option.Range("intensity", "Yoğunluk (%)", 70f, 10f, 100f, 5f));
	private int applied = VANILLA;

	public HitColorModule() {
		super("hit_color", "İsabet Rengi", "İR", Category.VISUAL, false);
	}

	@Override
	public void onTick(Minecraft mc) {
		int wanted = Palette.withAlpha(Palette.color(color), intensity.value / 100f);
		if (wanted != applied) {
			paint(mc, wanted);
		}
	}

	@Override
	protected void onDisable(Minecraft mc) {
		paint(mc, VANILLA);
	}

	private void paint(Minecraft mc, int argb) {
		DynamicTexture texture = ((OverlayTextureAccessor) mc.gameRenderer.overlayTexture()).cevher$texture();
		if (texture.getPixels() == null) {
			return;
		}
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x < 16; x++) {
				texture.getPixels().setPixel(x, y, argb);
			}
		}
		texture.upload();
		applied = argb;
	}
}
