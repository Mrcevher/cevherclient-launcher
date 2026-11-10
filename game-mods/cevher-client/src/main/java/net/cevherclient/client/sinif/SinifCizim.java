package net.cevherclient.client.sinif;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.imageio.ImageIO;
import net.cevherclient.client.CevherClientMod;
import net.cevherclient.client.sinif.SinifVeri.Parca;
import net.cevherclient.client.sinif.SinifVeri.Satir;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Sınıf ekranlarının ortak çizim yardımcıları: tasarımdaki renkler, Java2D yazı (Segoe UI, yoksa sistemin
 * sans-serif fontu), sınıf amblemleri ve amblemden çıkarılan düğüm simgesi, yıldızlı arka plan, ipucu kutusu.
 */
public final class SinifCizim {
	public static final int ZEMIN = 0xFF09080B;
	public static final int PARILTI = 0xFF46321E;
	public static final int CIZGI = 0xFFEAB58E;
	public static final int GRI = 0xFF6E6B69;
	public static final int BEYAZ = 0xFFE4E2E0;
	public static final int TURUNCU = 0xFFF2B48A;
	public static final int YILDIZ = 0xFFF6E2C8;
	public static final int CERCEVE = 0xFF3A3837;
	public static final int PANEL = 0xEE141211;
	public static final int YAZI = 0xFFD8CFC8;
	public static final int SOLUK = 0xFF9A8A7A;

	private static final FontRenderContext FRC = new FontRenderContext(null, true, true);
	private static final String AILE = aile();
	private static final Map<String, BufferedImage> AMBLEMLER = new HashMap<>();
	private static final Map<String, BufferedImage> SIMGELER = new HashMap<>();

	private SinifCizim() {
	}

	private static String aile() {
		try {
			for (String ad : new String[] {"Segoe UI", "Helvetica Neue", "Arial"}) {
				if (new Font(ad, Font.PLAIN, 10).getFamily().equalsIgnoreCase(ad)) {
					return ad;
				}
			}
		} catch (Throwable ignored) {
			// font sistemi yoksa varsayılan
		}
		return Font.SANS_SERIF;
	}

	public static Color awt(int argb) {
		return new Color(argb, true);
	}

	public static Color awt(int argb, float alpha) {
		int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
		return new Color((a << 24) | (argb & 0xFFFFFF), true);
	}

	// --- Yazı ---

	public static Font font(float boyut, boolean kalin, boolean italik) {
		int stil = (kalin ? Font.BOLD : 0) | (italik ? Font.ITALIC : 0);
		return new Font(AILE, stil, 10).deriveFont(boyut);
	}

	public static float genislik(Satir s, float boyut, boolean italik) {
		float w = 0;
		for (Parca p : s.parcalar()) {
			w += (float) font(boyut, p.kalin(), italik).getStringBounds(p.metin(), FRC).getWidth();
		}
		return w;
	}

	public static float genislik(String metin, Font f) {
		return (float) f.getStringBounds(metin, FRC).getWidth();
	}

	/** Bir satırın yüksekliği (yazı boyutuna göre). */
	public static float yukseklik(float boyut) {
		LineMetrics lm = font(boyut, false, false).getLineMetrics("ÇĞAgy", FRC);
		return lm.getAscent() + lm.getDescent();
	}

	private static float ascent(float boyut) {
		return font(boyut, false, false).getLineMetrics("ÇĞAgy", FRC).getAscent();
	}

	/** Graphics2D'ye satırı (x, üst) noktasından yazar. */
	public static void yaz(Graphics2D g, Satir s, float x, float ust, float boyut, boolean italik) {
		float taban = ust + ascent(boyut);
		float imlec = x;
		for (Parca p : s.parcalar()) {
			Font f = font(boyut, p.kalin(), italik);
			g.setFont(f);
			g.setColor(awt(p.renk()));
			g.drawString(p.metin(), imlec, taban);
			imlec += genislik(p.metin(), f);
		}
	}

	/** Gölgeli yazı (koyu zemin üstünde okunurluk için). */
	public static void yazGolgeli(Graphics2D g, Satir s, float x, float ust, float boyut, boolean italik) {
		Satir golge = new Satir(s.parcalar().stream().map(p -> new Parca(p.metin(), 0xB0000000, p.kalin())).toList());
		yaz(g, golge, x + boyut * 0.08f, ust + boyut * 0.08f, boyut, italik);
		yaz(g, s, x, ust, boyut, italik);
	}

	/**
	 * Satırı kendi dokusu olarak çizer. {@code hiza}: 0 sol, 1 orta, 2 sağ ({@code x} o kenarın konumu).
	 *
	 * @return satırın genişliği
	 */
	public static float satir(GuiGraphics g, Satir s, float x, float y, float boyut, boolean italik, int hiza,
			float alpha) {
		if (s.bos()) {
			return 0;
		}
		float w = genislik(s, boyut, italik);
		float h = yukseklik(boyut) + boyut * 0.2f;
		float sol = hiza == 1 ? x - w / 2f : hiza == 2 ? x - w : x;
		float pay = boyut * 0.3f;
		String anahtar = "s|" + boyut + "|" + italik + "|" + s.anahtar();
		SinifDoku.ciz(g, anahtar, 0, sol - pay, y, w + pay * 2, h, alpha,
			p -> yazGolgeli(p, s, pay, 0, boyut, italik));
		return w;
	}

	/** Satırı kelime sınırlarından {@code maxW} genişliğe sarar (renkler korunur). */
	public static List<Satir> sar(Satir s, float maxW, float boyut, boolean italik) {
		List<Satir> sonuc = new ArrayList<>();
		if (genislik(s, boyut, italik) <= maxW) {
			sonuc.add(s);
			return sonuc;
		}
		List<Parca> mevcut = new ArrayList<>();
		float w = 0;
		for (Parca p : s.parcalar()) {
			Font f = font(boyut, p.kalin(), italik);
			String[] kelimeler = p.metin().split("(?<= )");
			for (String k : kelimeler) {
				float kw = genislik(k, f);
				if (w + kw > maxW && !mevcut.isEmpty()) {
					sonuc.add(new Satir(List.copyOf(mevcut)));
					mevcut.clear();
					w = 0;
					k = k.stripLeading();
					kw = genislik(k, f);
				}
				if (!k.isEmpty()) {
					mevcut.add(new Parca(k, p.renk(), p.kalin()));
					w += kw;
				}
			}
		}
		if (!mevcut.isEmpty()) {
			sonuc.add(new Satir(List.copyOf(mevcut)));
		}
		return sonuc;
	}

	// --- İpucu kutusu ---

	/**
	 * İmlecin yanında ipucu kutusu. Sınırlar geçerli poz biriminde ekranın görünen kenarlarıdır.
	 */
	public static void ipucu(GuiGraphics g, List<Satir> satirlar, float mx, float my, float sol, float sag, float ust,
			float alt, float alpha) {
		if (satirlar.isEmpty()) {
			return;
		}
		float boyut = 6.2f;
		float satirH = yukseklik(boyut) + 1.2f;
		float bosH = satirH * 0.5f;
		float w = 0;
		float h = 0;
		for (Satir s : satirlar) {
			w = Math.max(w, genislik(s, boyut, false));
			h += s.bos() ? bosH : satirH;
		}
		float pad = 6f;
		float kw = w + pad * 2;
		float kh = h + pad * 2 - 1;
		float x = mx + 10;
		float y = my - 4;
		if (x + kw > sag - 2) {
			x = mx - 10 - kw;
		}
		if (y + kh > alt - 2) {
			y = alt - 2 - kh;
		}
		x = Math.max(sol + 2, x);
		y = Math.max(ust + 2, y);
		StringBuilder anahtar = new StringBuilder("ipucu|");
		for (Satir s : satirlar) {
			anahtar.append(s.anahtar()).append('\u0002');
		}
		final float fw = kw;
		final float fh = kh;
		SinifDoku.ciz(g, anahtar.toString(), 0, x, y, kw, kh, alpha, p -> {
			p.setColor(awt(0xF2100E0D));
			p.fill(new RoundRectangle2D.Float(0, 0, fw, fh, 4, 4));
			p.setColor(awt(CERCEVE));
			p.setStroke(new BasicStroke(0.8f));
			p.draw(new RoundRectangle2D.Float(0.4f, 0.4f, fw - 0.8f, fh - 0.8f, 4, 4));
			p.setColor(awt(TURUNCU, 0.7f));
			p.fill(new Rectangle2D.Float(3, 0.4f, fw - 6, 0.8f));
			float yy = pad;
			for (Satir s : satirlar) {
				if (s.bos()) {
					yy += bosH;
					continue;
				}
				yaz(p, s, pad, yy, boyut, false);
				yy += satirH;
			}
		});
	}

	// --- Amblem ve simge ---

	/** Sınıf amblemi (yüksek çözünürlüklü PNG); bulunamazsa null. */
	public static BufferedImage amblem(String ad) {
		String temiz = ad == null ? "" : ad.replaceAll("[^a-z0-9_]", "");
		return AMBLEMLER.computeIfAbsent(temiz, k -> {
			if (k.isEmpty()) {
				return null;
			}
			try (InputStream in = SinifCizim.class.getResourceAsStream("/assets/cevher_client/sinif/amblem/" + k + ".png")) {
				return in == null ? null : ImageIO.read(in);
			} catch (Exception e) {
				CevherClientMod.LOGGER.warn("Sınıf amblemi okunamadı: {}", k);
				return null;
			}
		});
	}

	/**
	 * Amblemin ortasındaki koyu simgeyi verilen renkte çıkarır (düğümlerin içi için). Fırça lekelerine girmemek
	 * için yalnızca merkez çevresine bakılır.
	 */
	public static BufferedImage simge(String ad, int renk) {
		String anahtar = ad + "|" + Integer.toHexString(renk);
		if (SIMGELER.containsKey(anahtar)) {
			return SIMGELER.get(anahtar);
		}
		BufferedImage kaynak = amblem(ad);
		BufferedImage sonuc = null;
		if (kaynak != null) {
			int w = kaynak.getWidth();
			int h = kaynak.getHeight();
			float cx = w / 2f;
			float cy = h / 2f;
			float rMax = Math.min(w, h) * 0.5f * 0.72f;
			int x0 = w, y0 = h, x1 = -1, y1 = -1;
			int[] maske = new int[w * h];
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int argb = kaynak.getRGB(x, y);
					int a = argb >>> 24;
					float dx = x - cx;
					float dy = y - cy;
					if (a < 128 || dx * dx + dy * dy > rMax * rMax) {
						continue;
					}
					int lum = (((argb >> 16) & 0xFF) * 299 + ((argb >> 8) & 0xFF) * 587 + (argb & 0xFF) * 114) / 1000;
					if (lum >= 70) {
						continue;
					}
					int m = Math.round(255 * (float) Math.sqrt(1f - lum / 70f));
					maske[y * w + x] = m;
					x0 = Math.min(x0, x);
					y0 = Math.min(y0, y);
					x1 = Math.max(x1, x);
					y1 = Math.max(y1, y);
				}
			}
			if (x1 >= x0 && y1 >= y0) {
				int boy = Math.max(x1 - x0, y1 - y0) + 1;
				int ox = x0 - (boy - (x1 - x0 + 1)) / 2;
				int oy = y0 - (boy - (y1 - y0 + 1)) / 2;
				sonuc = new BufferedImage(boy, boy, BufferedImage.TYPE_INT_ARGB);
				int rgb = renk & 0xFFFFFF;
				int ra = (renk >>> 24) & 0xFF;
				for (int y = 0; y < boy; y++) {
					for (int x = 0; x < boy; x++) {
						int sx = ox + x;
						int sy = oy + y;
						if (sx < 0 || sy < 0 || sx >= w || sy >= h) {
							continue;
						}
						int m = maske[sy * w + sx];
						if (m > 0) {
							sonuc.setRGB(x, y, ((m * ra / 255) << 24) | rgb);
						}
					}
				}
			}
		}
		SIMGELER.put(anahtar, sonuc);
		return sonuc;
	}

	/** Amblemi daire içinde çizer; yoksa sınıf renginde daire. */
	public static void amblemCiz(Graphics2D g, String ad, int yedekRenk, float x, float y, float d) {
		BufferedImage img = amblem(ad);
		if (img == null) {
			g.setColor(awt(yedekRenk));
			g.fill(new Ellipse2D.Float(x, y, d, d));
			return;
		}
		// Amblem PNG'leri zaten saydam köşeli dairedir; kırpma (kenar yumuşatmasız) gerekmez.
		java.awt.geom.AffineTransform t = g.getTransform();
		g.translate(x, y);
		g.scale(d / img.getWidth(), d / img.getHeight());
		g.drawImage(img, 0, 0, null);
		g.setTransform(t);
	}

	// --- Arka plan ---

	/**
	 * Ekranı kaplayan koyu zemin: ortada sıcak parıltı, yıldız tozu, birkaç parıltı yıldızı ve kenar gölgesi.
	 * Ölçüler geçerli birimde (genişlik w, yükseklik h; parıltı merkezi cx, cy).
	 */
	public static void arkaPlan(Graphics2D g, float w, float h, float cx, float cy, int parilti, long tohum) {
		g.setColor(awt(ZEMIN));
		g.fill(new Rectangle2D.Float(0, 0, w, h));
		float r = Math.max(w, h) * 0.62f;
		g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), r, new float[] {0f, 0.35f, 1f},
			new Color[] {awt(parilti, 0.95f), awt(parilti, 0.32f), awt(ZEMIN, 0f)}, MultipleGradientPaint.CycleMethod.NO_CYCLE));
		g.fill(new Rectangle2D.Float(0, 0, w, h));

		Random rnd = new Random(tohum);
		int adet = Math.round(w * h / 160f);
		for (int i = 0; i < adet; i++) {
			float x = rnd.nextFloat() * w;
			float y = rnd.nextFloat() * h;
			float d = 0.25f + rnd.nextFloat() * rnd.nextFloat() * 0.9f;
			g.setColor(awt(YILDIZ, 0.08f + rnd.nextFloat() * 0.35f));
			g.fill(new Ellipse2D.Float(x, y, d, d));
		}
		int parlak = Math.max(6, Math.round(w * h / 9000f));
		for (int i = 0; i < parlak; i++) {
			yildiz(g, rnd.nextFloat() * w, rnd.nextFloat() * h, 1.6f + rnd.nextFloat() * 2.6f, 0.25f + rnd.nextFloat() * 0.4f);
		}

		float rv = (float) Math.hypot(w, h) * 0.62f;
		g.setPaint(new RadialGradientPaint(new Point2D.Float(w / 2f, h / 2f), rv, new float[] {0f, 0.62f, 1f},
			new Color[] {awt(0x00000000), awt(0x00000000), awt(0xA8000000)}, MultipleGradientPaint.CycleMethod.NO_CYCLE));
		g.fill(new Rectangle2D.Float(0, 0, w, h));
	}

	/** Dört köşeli parıltı yıldızı. */
	public static void yildiz(Graphics2D g, float x, float y, float r, float alpha) {
		Path2D.Float p = new Path2D.Float();
		float i = r * 0.16f;
		p.moveTo(x, y - r);
		p.lineTo(x + i, y - i);
		p.lineTo(x + r, y);
		p.lineTo(x + i, y + i);
		p.lineTo(x, y + r);
		p.lineTo(x - i, y + i);
		p.lineTo(x - r, y);
		p.lineTo(x - i, y - i);
		p.closePath();
		g.setColor(awt(YILDIZ, alpha));
		g.fill(p);
		g.setColor(awt(YILDIZ, alpha * 0.35f));
		g.fill(new Ellipse2D.Float(x - r * 0.35f, y - r * 0.35f, r * 0.7f, r * 0.7f));
	}

	/** Ortası parlak, uçları sönük yatay ayraç çizgisi. */
	public static void ayrac(Graphics2D g, float x, float y, float w, float kalinlik) {
		g.setPaint(new java.awt.LinearGradientPaint(new Point2D.Float(x, y), new Point2D.Float(x + w, y),
			new float[] {0f, 0.5f, 1f}, new Color[] {awt(CIZGI, 0f), awt(CIZGI, 0.9f), awt(CIZGI, 0f)}));
		g.fill(new Rectangle2D.Float(x, y, w, kalinlik));
		g.setPaint(new java.awt.LinearGradientPaint(new Point2D.Float(x, y), new Point2D.Float(x + w, y),
			new float[] {0f, 0.5f, 1f}, new Color[] {awt(CIZGI, 0f), awt(CIZGI, 0.18f), awt(CIZGI, 0f)}));
		g.fill(new Rectangle2D.Float(x, y - kalinlik * 1.5f, w, kalinlik * 4f));
	}

	/** Elmas (45° dönük kare); merkez (cx, cy), yarı köşegen r. */
	public static Path2D.Float elmas(float cx, float cy, float r) {
		Path2D.Float p = new Path2D.Float();
		p.moveTo(cx, cy - r);
		p.lineTo(cx + r, cy);
		p.lineTo(cx, cy + r);
		p.lineTo(cx - r, cy);
		p.closePath();
		return p;
	}

	/** Puan simgesi: içinde yıldız olan turuncu çerçeveli elmas. */
	public static void puanSimgesi(Graphics2D g, float cx, float cy, float r) {
		g.setColor(awt(TURUNCU));
		g.setStroke(new BasicStroke(r * 0.12f));
		g.draw(elmas(cx, cy, r));
		yildizTuruncu(g, cx, cy, r * 0.55f);
	}

	private static void yildizTuruncu(Graphics2D g, float x, float y, float r) {
		Composite c = g.getComposite();
		g.setComposite(AlphaComposite.SrcOver);
		Path2D.Float p = new Path2D.Float();
		float i = r * 0.22f;
		p.moveTo(x, y - r);
		p.lineTo(x + i, y - i);
		p.lineTo(x + r, y);
		p.lineTo(x + i, y + i);
		p.lineTo(x, y + r);
		p.lineTo(x - i, y + i);
		p.lineTo(x - r, y);
		p.lineTo(x - i, y - i);
		p.closePath();
		g.setColor(awt(TURUNCU));
		g.fill(p);
		g.setComposite(c);
	}
}
