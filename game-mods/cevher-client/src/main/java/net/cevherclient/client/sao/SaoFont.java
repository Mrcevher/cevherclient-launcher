package net.cevherclient.client.sao;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;

/**
 * SAO arayüzünün ince, köşeleri pahlı çizgi fontu. Her harf aşağıdaki tabloda çizgi koordinatlarıyla
 * tanımlıdır (dış font dosyası yok); ekran çözünürlüğünde kenar yumuşatmalı olarak dokuya çizilir.
 *
 * Birim sistemi: büyük harf üstü y=0, taban çizgisi y=10, alt uzantı y=13, büyük harf üstü işaretler y=-3'e kadar.
 * Boyut parametresi büyük harf yüksekliğidir (GUI pikseli). Tabloda olmayan karakterler vanilla fontla çizilir.
 */
public final class SaoFont {
	private static final float STROKE = 0.95f;
	private static final float SPACING = 1.3f;
	private static final float SPACE = 2.6f;
	private static final float PAD = 0.9f;
	private static final float CELL_TOP = -3.6f;
	private static final float CELL_HEIGHT = 17.6f;
	private static final float ATLAS_ROW = 170f;
	/** Vanilla fontun büyük harf yüksekliği (piksel). */
	private static final float VANILLA_CAP = 7f;

	private record Glyph(float width, float[][] strokes) {
	}

	private record Cell(float x, float y, float width) {
	}

	private static final Map<Integer, Glyph> GLYPHS = new HashMap<>();
	private static final Map<Integer, Cell> CELLS = new HashMap<>();
	private static float atlasWidth;
	private static float atlasHeight;

	static {
		// Büyük harfler (genişlik 5).
		g('A', 5, "0,10 0,1.5 1.5,0 3.5,0 5,1.5 5,10|0,5.5 5,5.5");
		g('B', 5, "0,0 4,0 5,1 5,4 4,5 0,5|4,5 5,6 5,9 4,10 0,10 0,0");
		g('C', 5, "5,0 1,0 0,1 0,9 1,10 5,10");
		g('D', 5, "0,0 3.5,0 5,1.5 5,8.5 3.5,10 0,10 0,0");
		g('E', 5, "5,0 0,0 0,10 5,10|0,5 4,5");
		g('F', 5, "5,0 0,0 0,10|0,5 4,5");
		g('G', 5, "5,1 4,0 1,0 0,1 0,9 1,10 4,10 5,9 5,5.5 2.5,5.5");
		g('H', 5, "0,0 0,10|5,0 5,10|0,5 5,5");
		g('I', 1, "0.5,0 0.5,10");
		g('J', 5, "5,0 5,9 4,10 1,10 0,9 0,7.5");
		g('K', 5, "0,0 0,10|5,0 1,5 0,5|1,5 5,10");
		g('L', 5, "0,0 0,10 5,10");
		g('M', 6, "0,10 0,0 3,4.5 6,0 6,10");
		g('N', 5, "0,10 0,0 5,10 5,0");
		g('O', 5, "1,0 4,0 5,1 5,9 4,10 1,10 0,9 0,1 1,0");
		g('P', 5, "0,10 0,0 4,0 5,1 5,4 4,5 0,5");
		g('Q', 5, "1,0 4,0 5,1 5,9 4,10 1,10 0,9 0,1 1,0|3,8 5,10.6");
		g('R', 5, "0,10 0,0 4,0 5,1 5,4 4,5 0,5|2,5 5,10");
		g('S', 5, "5,0 1,0 0,1 0,4 1,5 4,5 5,6 5,9 4,10 0,10");
		g('T', 5, "0,0 5,0|2.5,0 2.5,10");
		g('U', 5, "0,0 0,9 1,10 4,10 5,9 5,0");
		g('V', 5, "0,0 2.5,10 5,0");
		g('W', 6.5f, "0,0 1.5,10 3.25,3 5,10 6.5,0");
		g('X', 5, "0,0 5,10|5,0 0,10");
		g('Y', 5, "0,0 2.5,5 5,0|2.5,5 2.5,10");
		g('Z', 5, "0,0 5,0 0,10 5,10");

		// Küçük harfler (x yüksekliği y=3..10).
		g('a', 4.5f, "0.5,3 3.5,3 4.5,4 4.5,10 1,10 0,9 0,7.5 1,6.5 4.5,6.5");
		g('b', 4.5f, "0,0 0,10 3.5,10 4.5,9 4.5,4 3.5,3 0,3");
		g('c', 4.5f, "4.5,3 1,3 0,4 0,9 1,10 4.5,10");
		g('d', 4.5f, "4.5,0 4.5,10 1,10 0,9 0,4 1,3 4.5,3");
		g('e', 4.5f, "0,6.5 4.5,6.5 4.5,4 3.5,3 1,3 0,4 0,9 1,10 4.5,10");
		g('f', 3.5f, "3.5,0 2,0 1,1 1,10|0,3 3,3");
		g('g', 4.5f, "4.5,10 1,10 0,9 0,4 1,3 4.5,3 4.5,12 3.5,13 0.5,13");
		g('h', 4.5f, "0,0 0,10|0,3 3.5,3 4.5,4 4.5,10");
		g('i', 1, "0.5,3 0.5,10|0.5,0.9 0.5,1.5");
		g('j', 2.5f, "2,3 2,12 1,13 0,13|2,0.9 2,1.5");
		g('k', 4.5f, "0,0 0,10|4.5,3 1,6.5 0,6.5|1,6.5 4.5,10");
		g('l', 1.5f, "0.5,0 0.5,9 1.5,10");
		g('m', 6.5f, "0,10 0,3 5.5,3 6.5,4 6.5,10|3.25,3 3.25,10");
		g('n', 4.5f, "0,10 0,3 3.5,3 4.5,4 4.5,10");
		g('o', 4.5f, "1,3 3.5,3 4.5,4 4.5,9 3.5,10 1,10 0,9 0,4 1,3");
		g('p', 4.5f, "0,13 0,3 3.5,3 4.5,4 4.5,9 3.5,10 0,10");
		g('q', 4.5f, "4.5,13 4.5,3 1,3 0,4 0,9 1,10 4.5,10");
		g('r', 3.5f, "0,10 0,3|0,4 1,3 3.5,3");
		g('s', 4.5f, "4.5,3 1,3 0,4 0,5.5 1,6.5 3.5,6.5 4.5,7.5 4.5,9 3.5,10 0,10");
		g('t', 3.5f, "1,0.5 1,9 2,10 3.5,10|0,3 3.2,3");
		g('u', 4.5f, "0,3 0,9 1,10 4.5,10 4.5,3");
		g('v', 4.5f, "0,3 2.25,10 4.5,3");
		g('w', 6.5f, "0,3 1.5,10 3.25,5 5,10 6.5,3");
		g('x', 4.5f, "0,3 4.5,10|4.5,3 0,10");
		g('y', 4.5f, "0,3 0,9 1,10 4.5,10|4.5,3 4.5,12 3.5,13 0.5,13");
		g('z', 4.5f, "0,3 4.5,3 0,10 4.5,10");

		// Rakamlar.
		g('0', 5, "1,0 4,0 5,1 5,9 4,10 1,10 0,9 0,1 1,0");
		g('1', 3, "0.5,1.5 2,0 2,10");
		g('2', 5, "0,1 1,0 4,0 5,1 5,4 4,5 1,5 0,6 0,10 5,10");
		g('3', 5, "0,0 4,0 5,1 5,4 4,5 1.5,5|4,5 5,6 5,9 4,10 0,10");
		g('4', 5, "4,10 4,0 0,6.5 5,6.5");
		g('5', 5, "5,0 0,0 0,4.5 4,4.5 5,5.5 5,9 4,10 0,10");
		g('6', 5, "5,0 1,0 0,1 0,9 1,10 4,10 5,9 5,5.5 4,4.5 0,4.5");
		g('7', 5, "0,0 5,0 5,1.5 2,10");
		g('8', 5, "1,0 4,0 5,1 5,4 4,5 1,5 0,4 0,1 1,0|1,5 0,6 0,9 1,10 4,10 5,9 5,6 4,5");
		g('9', 5, "5,5.5 1,5.5 0,4.5 0,1 1,0 4,0 5,1 5,9 4,10 0,10");

		// Noktalama.
		g('.', 1, "0.5,9.4 0.5,10");
		g(',', 1.5f, "1,9.4 0.3,11.4");
		g(':', 1, "0.5,3.9 0.5,4.5|0.5,9.4 0.5,10");
		g(';', 1.5f, "1,3.9 1,4.5|1,9.4 0.3,11.4");
		g('!', 1, "0.5,0 0.5,7|0.5,9.4 0.5,10");
		g('?', 4.5f, "0,1 1,0 3.5,0 4.5,1 4.5,4 3.5,5 2.25,5 2.25,7|2.25,9.4 2.25,10");
		g('\'', 1, "0.5,0 0.5,2.5");
		g('"', 2.5f, "0.5,0 0.5,2.5|2,0 2,2.5");
		g('-', 3.5f, "0,6 3.5,6");
		g('+', 5, "0,5.5 5,5.5|2.5,3 2.5,8");
		g('=', 4, "0,4.5 4,4.5|0,7 4,7");
		g('_', 5, "0,11 5,11");
		g('/', 4, "0,10.5 4,-0.5");
		g('\\', 4, "0,-0.5 4,10.5");
		g('(', 2, "2,-0.5 1,0.5 1,9.5 2,10.5");
		g(')', 2, "0,-0.5 1,0.5 1,9.5 0,10.5");
		g('[', 2, "2,-0.5 0.5,-0.5 0.5,10.5 2,10.5");
		g(']', 2, "0,-0.5 1.5,-0.5 1.5,10.5 0,10.5");
		g('<', 4, "4,2.5 0,5.75 4,9");
		g('>', 4, "0,2.5 4,5.75 0,9");
		g('*', 4, "2,1 2,5|0,2 4,4|4,2 0,4");
		g('|', 1, "0.5,-0.5 0.5,10.5");
		g('%', 6, "0,10 6,0|0.5,0.5 2,0.5 2,2.5 0.5,2.5 0.5,0.5|4,7.5 5.5,7.5 5.5,9.5 4,9.5 4,7.5");
		g('#', 6, "1.8,0.5 1.2,10|4.8,0.5 4.2,10|0,3.5 6,3.5|0,7 6,7");

		// Türkçe harfler: temel harf + işaret.
		String cedillaUpper = "|2.5,10 2.5,11.6 1.5,12.6";
		String cedillaLower = "|2.25,10 2.25,11.6 1.25,12.6";
		composite('Ç', 'C', cedillaUpper);
		composite('Ş', 'S', cedillaUpper);
		composite('ç', 'c', cedillaLower);
		composite('ş', 's', cedillaLower);
		composite('Ğ', 'G', "|1.5,-2.6 2.5,-1.6 3.5,-2.6");
		composite('ğ', 'g', "|1,0.6 2.25,1.8 3.5,0.6");
		composite('Ö', 'O', "|1.5,-2.2 1.5,-1.6|3.5,-2.2 3.5,-1.6");
		composite('Ü', 'U', "|1.5,-2.2 1.5,-1.6|3.5,-2.2 3.5,-1.6");
		composite('ö', 'o', "|1.25,0.9 1.25,1.5|3.25,0.9 3.25,1.5");
		composite('ü', 'u', "|1.25,0.9 1.25,1.5|3.25,0.9 3.25,1.5");
		composite('İ', 'I', "|0.5,-2.2 0.5,-1.6");
		g('ı', 1, "0.5,3 0.5,10");

		layout();
	}

	private SaoFont() {
	}

	/** Metnin genişliği (GUI pikseli). */
	public static float width(String text, float size) {
		float total = 0;
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			total += advance(cp, size);
			i += Character.charCount(cp);
		}
		return total;
	}

	public static float width(FormattedCharSequence text, float size) {
		float[] total = {0};
		text.accept((index, style, cp) -> {
			total[0] += advance(cp, size);
			return true;
		});
		return total[0];
	}

	/** (x, y) büyük harf üstü olacak şekilde çizer; bitiş x'ini döndürür. */
	public static float draw(GuiGraphics graphics, String text, float x, float y, float size, int color) {
		float cursor = x;
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			cursor += drawChar(graphics, cp, cursor, y, size, color);
			i += Character.charCount(cp);
		}
		return cursor;
	}

	/** Biçimli metni (ör. eşya açıklaması) parça renkleriyle çizer; renksiz parçalar {@code color} olur. */
	public static float draw(GuiGraphics graphics, FormattedCharSequence text, float x, float y, float size, int color) {
		float[] cursor = {x};
		int alpha = color & 0xFF000000;
		text.accept((index, style, cp) -> {
			cursor[0] += drawChar(graphics, cp, cursor[0], y, size, colorOf(style, color, alpha));
			return true;
		});
		return cursor[0];
	}

	private static int colorOf(Style style, int fallback, int alpha) {
		TextColor textColor = style.getColor();
		return textColor == null ? fallback : alpha | (textColor.getValue() & 0xFFFFFF);
	}

	private static float advance(int cp, float size) {
		float unit = size / 10f;
		if (cp == ' ') {
			return SPACE * unit;
		}
		Glyph glyph = GLYPHS.get(cp);
		if (glyph != null) {
			return (glyph.width + SPACING) * unit;
		}
		Font font = Minecraft.getInstance().font;
		return font.width(new String(Character.toChars(cp))) * size / VANILLA_CAP;
	}

	private static float drawChar(GuiGraphics graphics, int cp, float x, float y, float size, int color) {
		float unit = size / 10f;
		if (cp == ' ') {
			return SPACE * unit;
		}
		Glyph glyph = GLYPHS.get(cp);
		if (glyph == null) {
			String text = new String(Character.toChars(cp));
			Font font = Minecraft.getInstance().font;
			float scale = size / VANILLA_CAP;
			graphics.pose().pushMatrix();
			graphics.pose().translate(x, y);
			graphics.pose().scale(scale, scale);
			graphics.drawString(font, text, 0, 0, color, false);
			graphics.pose().popMatrix();
			return font.width(text) * scale;
		}
		Cell cell = CELLS.get(cp);
		float key = Math.round(size * 10) / 10f;
		SaoRaster.drawRegion(graphics, "font_" + key, x - PAD * unit, y + CELL_TOP * unit,
			atlasWidth * unit, atlasHeight * unit, cell.x * unit, cell.y * unit,
			cell.width * unit, CELL_HEIGHT * unit, color, g -> paintAtlas(g, unit));
		return (glyph.width + SPACING) * unit;
	}

	private static void paintAtlas(Graphics2D g, float unit) {
		g.scale(unit, unit);
		g.setStroke(new BasicStroke(STROKE, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
		for (Map.Entry<Integer, Glyph> entry : GLYPHS.entrySet()) {
			Cell cell = CELLS.get(entry.getKey());
			float ox = cell.x + PAD;
			float oy = cell.y - CELL_TOP;
			for (float[] stroke : entry.getValue().strokes) {
				Path2D path = new Path2D.Float();
				path.moveTo(ox + stroke[0], oy + stroke[1]);
				for (int i = 2; i < stroke.length; i += 2) {
					path.lineTo(ox + stroke[i], oy + stroke[i + 1]);
				}
				g.draw(path);
			}
		}
	}

	private static void layout() {
		float x = 0;
		float y = 0;
		for (Map.Entry<Integer, Glyph> entry : GLYPHS.entrySet()) {
			float cellWidth = entry.getValue().width + PAD * 2;
			if (x + cellWidth > ATLAS_ROW) {
				x = 0;
				y += CELL_HEIGHT;
			}
			CELLS.put(entry.getKey(), new Cell(x, y, cellWidth));
			x += cellWidth;
		}
		atlasWidth = ATLAS_ROW;
		atlasHeight = y + CELL_HEIGHT;
	}

	private static void g(char c, float width, String strokes) {
		GLYPHS.put((int) c, new Glyph(width, parse(strokes)));
	}

	private static void composite(char c, char base, String extra) {
		Glyph glyph = GLYPHS.get((int) base);
		List<float[]> strokes = new ArrayList<>(List.of(glyph.strokes));
		strokes.addAll(List.of(parse(extra.substring(1))));
		GLYPHS.put((int) c, new Glyph(glyph.width, strokes.toArray(new float[0][])));
	}

	private static float[][] parse(String strokes) {
		String[] parts = strokes.split("\\|");
		float[][] result = new float[parts.length][];
		for (int s = 0; s < parts.length; s++) {
			String[] points = parts[s].trim().split(" ");
			float[] coords = new float[points.length * 2];
			for (int p = 0; p < points.length; p++) {
				String[] xy = points[p].split(",");
				coords[p * 2] = Float.parseFloat(xy[0]);
				coords[p * 2 + 1] = Float.parseFloat(xy[1]);
			}
			result[s] = coords;
		}
		return result;
	}
}
