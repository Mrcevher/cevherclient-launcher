package net.cevherclient.client.sinif;

import com.mojang.blaze3d.platform.NativeImage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.cevherclient.client.CevherClientMod;
import net.cevherclient.client.sao.SaoRaster;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Sınıf ekranlarının Java2D ile ekranın gerçek piksel boyutunda çizilen dokuları ({@link SaoRaster} gibi).
 * Fark: her anahtarın bir sürümü vardır; veri değişince (düğüm açıldı, puan değişti) aynı anahtar yeni
 * sürümle çağrılır ve eski doku bırakılır. Ekran kapanınca {@link #temizle()} hepsini bırakır.
 */
public final class SinifDoku {
	private record Kayit(Identifier id, int w, int h, long surum) {
	}

	private static final Map<String, Kayit> ONBELLEK = new HashMap<>();
	private static int sayac = 0;

	private SinifDoku() {
	}

	/**
	 * (x, y) konumuna w×h boyutunda (geçerli poz biriminde) bir doku çizer. {@code ressam} Graphics2D'ye aynı
	 * birimde çizer (piksel ölçeği önceden ayarlanır).
	 */
	public static void ciz(GuiGraphics g, String anahtar, long surum, float x, float y, float w, float h, float alpha,
			Consumer<Graphics2D> ressam) {
		if (w <= 0 || h <= 0 || alpha <= 0.002f) {
			return;
		}
		float olcek = SaoRaster.pixelScale(g);
		int pw = Math.max(1, Math.round(w * olcek));
		int ph = Math.max(1, Math.round(h * olcek));
		Kayit k = ONBELLEK.get(anahtar);
		if (k == null || k.w != pw || k.h != ph || k.surum != surum) {
			if (k != null) {
				Minecraft.getInstance().getTextureManager().release(k.id);
			}
			k = olustur(anahtar, surum, pw, ph, olcek, ressam);
			ONBELLEK.put(anahtar, k);
		}
		int renk = (Math.round(Math.max(0f, Math.min(1f, alpha)) * 255) << 24) | 0xFFFFFF;
		g.pose().pushMatrix();
		g.pose().translate(Math.round(x * olcek) / olcek, Math.round(y * olcek) / olcek);
		g.pose().scale(1f / olcek, 1f / olcek);
		g.blit(RenderPipelines.GUI_TEXTURED, k.id, 0, 0, 0, 0, k.w, k.h, k.w, k.h, renk);
		g.pose().popMatrix();
	}

	private static Kayit olustur(String anahtar, long surum, int pw, int ph, float olcek, Consumer<Graphics2D> ressam) {
		BufferedImage resim = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = resim.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.scale(olcek, olcek);
		try {
			ressam.accept(g);
		} catch (RuntimeException e) {
			CevherClientMod.LOGGER.warn("Sınıf ekranı dokusu çizilemedi ({}): {}", anahtar, e.toString());
		}
		g.dispose();

		NativeImage pikseller = new NativeImage(pw, ph, false);
		int[] satir = new int[pw];
		for (int py = 0; py < ph; py++) {
			resim.getRGB(0, py, pw, 1, satir, 0, pw);
			for (int px = 0; px < pw; px++) {
				pikseller.setPixel(px, py, satir[px]);
			}
		}
		String yol = "sinif/" + (sayac++);
		Identifier id = Identifier.fromNamespaceAndPath(CevherClientMod.MOD_ID, yol);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> id.toString(), pikseller));
		return new Kayit(id, pw, ph, surum);
	}

	/** Bütün dokuları bırakır (ekran kapanınca). */
	public static void temizle() {
		for (Kayit k : ONBELLEK.values()) {
			Minecraft.getInstance().getTextureManager().release(k.id);
		}
		ONBELLEK.clear();
	}
}
