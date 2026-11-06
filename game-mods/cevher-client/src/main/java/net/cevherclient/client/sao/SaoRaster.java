package net.cevherclient.client.sao;

import com.mojang.blaze3d.platform.NativeImage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.cevherclient.client.CevherClientMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

/**
 * Kodla tanımlanan vektör şekilleri (ikonlar, kristal, font) ekranın gerçek piksel boyutunda Java2D ile
 * kenar yumuşatmalı çizip dinamik dokuya çevirir. Böylece GUI ölçeği ne olursa olsun şekiller keskin kalır.
 * Dış görsel yoktur; her şey buradaki çizim kodundan üretilir.
 *
 * Dokular anahtar + piksel boyutuna göre önbelleğe alınır; boyut değişince (GUI ölçeği, pencere) eskisi bırakılır.
 */
public final class SaoRaster {
	private static final Map<String, Entry> CACHE = new HashMap<>();
	private static int counter = 0;

	private record Entry(Identifier id, int width, int height) {
	}

	private SaoRaster() {
	}

	/** Mevcut poz dönüşümü dahil, 1 GUI pikselinin kaç ekran pikseline denk geldiği. */
	public static float pixelScale(GuiGraphics graphics) {
		Matrix3x2f pose = graphics.pose();
		float poseScale = (float) Math.sqrt(pose.m00 * pose.m00 + pose.m01 * pose.m01);
		return Minecraft.getInstance().getWindow().getGuiScale() * poseScale;
	}

	/**
	 * (x, y) GUI konumuna w×h GUI boyutunda bir şekil çizer. {@code painter} Graphics2D'ye GUI biriminde çizer
	 * (ölçek önceden ayarlanır); şekiller beyaz çizilip {@code color} ile renklendirilir.
	 */
	public static void draw(GuiGraphics graphics, String key, float x, float y, float w, float h, int color,
			Consumer<Graphics2D> painter) {
		float scale = pixelScale(graphics);
		int pw = Math.max(1, Math.round(w * scale));
		int ph = Math.max(1, Math.round(h * scale));
		Entry entry = texture(key, pw, ph, scale, painter);
		blit(graphics, entry, x, y, scale, 0, 0, pw, ph, color);
	}

	/**
	 * Aynı dokudan bir bölge çizer (ör. animasyon şeridinden tek kare). {@code u}, {@code v}, {@code uw}, {@code vh}
	 * GUI birimindedir; doku tamamı {@code w}×{@code h} GUI boyutundadır.
	 */
	public static void drawRegion(GuiGraphics graphics, String key, float x, float y, float w, float h,
			float u, float v, float uw, float vh, int color, Consumer<Graphics2D> painter) {
		float scale = pixelScale(graphics);
		int pw = Math.max(1, Math.round(w * scale));
		int ph = Math.max(1, Math.round(h * scale));
		Entry entry = texture(key, pw, ph, scale, painter);
		blit(graphics, entry, x, y, scale, Math.round(u * scale), Math.round(v * scale),
			Math.round(uw * scale), Math.round(vh * scale), color);
	}

	private static void blit(GuiGraphics graphics, Entry entry, float x, float y, float scale,
			int u, int v, int uw, int vh, int color) {
		graphics.pose().pushMatrix();
		// Ekran pikseline hizala: yarım piksele düşen doku bulanıklaşmasın.
		graphics.pose().translate(Math.round(x * scale) / scale, Math.round(y * scale) / scale);
		graphics.pose().scale(1f / scale, 1f / scale);
		graphics.blit(RenderPipelines.GUI_TEXTURED, entry.id, 0, 0, u, v, uw, vh, entry.width, entry.height, color);
		graphics.pose().popMatrix();
	}

	private static Entry texture(String key, int pw, int ph, float scale, Consumer<Graphics2D> painter) {
		Entry entry = CACHE.get(key);
		if (entry != null && entry.width == pw && entry.height == ph) {
			return entry;
		}
		if (entry != null) {
			Minecraft.getInstance().getTextureManager().release(entry.id);
		}

		BufferedImage image = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.scale(scale, scale);
		g.setColor(java.awt.Color.WHITE);
		painter.accept(g);
		g.dispose();

		NativeImage pixels = new NativeImage(pw, ph, false);
		for (int py = 0; py < ph; py++) {
			for (int px = 0; px < pw; px++) {
				pixels.setPixel(px, py, image.getRGB(px, py));
			}
		}
		String path = "raster/" + key.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_") + "_" + (counter++);
		Identifier id = Identifier.fromNamespaceAndPath(CevherClientMod.MOD_ID, path);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> id.toString(), pixels));
		entry = new Entry(id, pw, ph);
		CACHE.put(key, entry);
		return entry;
	}
}
