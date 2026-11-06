package net.cevherclient.client.screen;

import net.cevherclient.client.sao.SaoGlyph;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/**
 * SAO menüsünde sütunun yanında açılan pencerenin içeriği. Koordinatlar menünün "tasarım biriminde"dir
 * (ekranın ortası 0,0); pencerenin yeri {@link SaoMenuScreen} içindeki {@code WX/WY/WW/WH} sabitleridir.
 */
abstract class SaoPage {
	protected final SaoMenuScreen host;

	protected SaoPage(SaoMenuScreen host) {
		this.host = host;
	}

	abstract String title();

	abstract SaoGlyph glyph();

	/** @param t sayfanın açılışından bu yana geçen süre (ms); giriş animasyonları buna göre oynar */
	abstract void render(GuiGraphics g, float mx, float my, long t, float dt);

	boolean click(float mx, float my, int button) {
		return false;
	}

	void release(float mx, float my, int button) {
	}

	void drag(float mx, float my, int button) {
	}

	boolean scroll(float mx, float my, double amount) {
		return false;
	}

	boolean charTyped(CharacterEvent event) {
		return false;
	}

	boolean keyPressed(KeyEvent event) {
		return false;
	}
}
