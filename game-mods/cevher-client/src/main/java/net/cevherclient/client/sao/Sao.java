package net.cevherclient.client.sao;

import java.awt.geom.Ellipse2D;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Sword Art Online temasının ortak renkleri, geçiş eğrileri ve çizim yardımcıları. Renkler SAO arayüz modunun
 * videodan ölçülmüş paletidir: açık gri yarı saydam paneller, koyu gri yazı, turuncu seçim, yeşil "açık".
 * Her çizim {@link #alpha} ile çarpılır; ekranlar açılış/kapanış solmasını bu değer üzerinden yapar.
 */
public final class Sao {
	public static final int PANEL_BG = 0xBDC8C8C8;
	public static final int HEADER_BG = 0xDFD4D4D4;
	public static final int FIELD_BG = 0xE6EAEAEA;
	public static final int CELL_BG = 0xD4D8D8D8;
	public static final int CELL_HOVER = 0xF0EEEEEE;
	public static final int CELL_LOCKED = 0x90BDBDBD;
	public static final int LABEL_BG = 0xEBE2E2E2;
	public static final int DISC = 0xEBE2E2E2;
	public static final int ICON = 0xFF3A3C3B;
	public static final int ICON_DIM = 0xFF9A9C9B;
	public static final int TITLE = 0xFF6E6E6E;
	public static final int TEXT = 0xFF5E5E5E;
	public static final int MUTED = 0xFF727272;
	public static final int ACCENT = 0xFFF2A233;
	public static final int ACCENT_LIGHT = 0xFFFFC66B;
	public static final int GREEN = 0xFF5CB52A;
	public static final int WHITE = 0xFFFFFFFF;

	/** Şu an çizilen ekranın saydamlığı (0..1). */
	public static float alpha = 1f;

	private Sao() {
	}

	// --- Zamanlama ---

	public static float progress(long t, long start, long duration) {
		return Math.max(0f, Math.min(1f, (t - start) / (float) duration));
	}

	public static float easeOut(float p) {
		return 1f - (1f - p) * (1f - p) * (1f - p);
	}

	public static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	// --- Renk ---

	public static int fade(int argb) {
		return withAlpha(argb, alpha);
	}

	public static int withAlpha(int argb, float factor) {
		int a = Math.round((argb >>> 24) * Math.max(0f, Math.min(1f, factor)));
		return (a << 24) | (argb & 0xFFFFFF);
	}

	public static int mix(int from, int to, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int a = Math.round(lerp(from >>> 24, to >>> 24, t));
		int r = Math.round(lerp((from >> 16) & 0xFF, (to >> 16) & 0xFF, t));
		int g = Math.round(lerp((from >> 8) & 0xFF, (to >> 8) & 0xFF, t));
		int b = Math.round(lerp(from & 0xFF, to & 0xFF, t));
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	public static boolean inside(double mx, double my, float x, float y, float w, float h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	// --- Çizim ---

	public static void rect(GuiGraphics g, float x, float y, float w, float h, int color) {
		g.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), fade(color));
	}

	/** 1 piksellik dikdörtgen çerçeve. */
	public static void outline(GuiGraphics g, float x, float y, float w, float h, int color) {
		int x0 = Math.round(x);
		int y0 = Math.round(y);
		int x1 = Math.round(x + w);
		int y1 = Math.round(y + h);
		int c = fade(color);
		g.fill(x0, y0, x1, y0 + 1, c);
		g.fill(x0, y1 - 1, x1, y1, c);
		g.fill(x0, y0 + 1, x0 + 1, y1 - 1, c);
		g.fill(x1 - 1, y0 + 1, x1, y1 - 1, c);
	}

	public static float textWidth(String text, float size) {
		return SaoFont.width(text, size);
	}

	/** (x, y) büyük harf üstü olacak şekilde SAO fontuyla yazar. */
	public static void text(GuiGraphics g, String text, float x, float y, float size, int color) {
		SaoFont.draw(g, text, x, y, size, fade(color));
	}

	public static void centered(GuiGraphics g, String text, float cx, float y, float size, int color) {
		text(g, text, cx - textWidth(text, size) / 2f, y, size, color);
	}

	/** Metni {@code maxWidth}'e sığacak şekilde sonuna ".." ekleyerek kısaltır. */
	public static String fit(String text, float maxWidth, float size) {
		if (textWidth(text, size) <= maxWidth) {
			return text;
		}
		String cut = text;
		while (cut.length() > 1 && textWidth(cut + "..", size) > maxWidth) {
			cut = cut.substring(0, cut.length() - 1);
		}
		return cut + "..";
	}

	/** Merkezi (cx, cy), çapı d olan dolu daire; kenarları yumuşatılmıştır. */
	public static void disk(GuiGraphics g, float cx, float cy, float d, int color) {
		SaoRaster.draw(g, "disk_" + d, cx - d / 2f, cy - d / 2f, d, d, fade(color),
			p -> p.fill(new Ellipse2D.Float(0, 0, d, d)));
	}

	/** Merkezi (cx, cy), dış çapı d olan, kalınlığı {@code stroke} olan halka. */
	public static void ring(GuiGraphics g, float cx, float cy, float d, float stroke, int color) {
		SaoRaster.draw(g, "ring_" + d + "_" + stroke, cx - d / 2f, cy - d / 2f, d, d, fade(color), p -> {
			p.setStroke(new java.awt.BasicStroke(stroke));
			p.draw(new Ellipse2D.Float(stroke / 2f, stroke / 2f, d - stroke, d - stroke));
		});
	}

	/** SAO etiketi: imlecin yanında beliren küçük açık gri kutu. */
	public static void label(GuiGraphics g, String text, float x, float y) {
		float size = 5.5f;
		float w = textWidth(text, size) + 10;
		rect(g, x, y, w, 12, LABEL_BG);
		text(g, text, x + 5, y + 3, size, MUTED);
	}

	/** Aç/kapa düğmesi: kapalıyken gri gövde ve solda beyaz topuz, açıkken yeşil gövde ve sağda topuz. */
	public static void toggle(GuiGraphics g, float x, float y, float anim, boolean hover) {
		float w = 26;
		float h = 12;
		rect(g, x, y, w, h, mix(0xFF9E9E9E, GREEN, anim));
		if (hover) {
			outline(g, x - 1, y - 1, w + 2, h + 2, WHITE);
		}
		float knob = lerp(x + 2, x + w - 10, anim);
		rect(g, knob, y + 2, 8, h - 4, WHITE);
	}

	/** Pencere gövdesi ve başlık çubuğu; başlık yatayda, gövde dikeyde açılır (SAO pencere animasyonu). */
	public static void window(GuiGraphics g, float x, float y, float w, float h, float header, long t) {
		float headerP = easeOut(progress(t, 0, 150));
		float bodyP = easeOut(progress(t, 100, 120));
		if (bodyP > 0) {
			rect(g, x, y + header, w, (h - header) * bodyP, PANEL_BG);
		}
		if (headerP > 0) {
			rect(g, x, y, w * headerP, header, HEADER_BG);
		}
	}
}
