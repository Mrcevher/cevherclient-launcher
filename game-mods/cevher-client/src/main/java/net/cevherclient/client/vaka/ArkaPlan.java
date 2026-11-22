package net.cevherclient.client.vaka;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import net.cevherclient.client.CevherClientMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jcodec.api.FrameGrab;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.ColorUtil;
import org.jcodec.scale.Transform;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Vaka menüsünün özel arka planı: fotoğraf (PNG/JPG/BMP), hareketli GIF ya da video (H.264 MP4/MOV, sessiz,
 * döngüde). Video ayrı bir iş parçacığında çözülür; ekrana yalnızca son kare yüklenir.
 */
final class ArkaPlan {
	private static final Identifier ID = Identifier.fromNamespaceAndPath(CevherClientMod.MOD_ID, "vaka_arkaplan");
	private static final int MAX_RESIM = 1920;
	private static final int MAX_HAREKET = 960;
	private static final int MAX_GIF_KARE = 400;
	/** Hareketli arka planın en yüksek kare hızı; bu değerin üstü hem çözmede hem GPU yüklemesinde atlanır. */
	private static final int HEDEF_FPS = 60;
	private static final long KARE_NS = 1_000_000_000L / HEDEF_FPS;

	private static String yukluYol = "";
	private static DynamicTexture doku;
	private static int w, h;
	private static String hata = "";

	// GIF
	private static List<int[]> gifKareler = List.of();
	private static int[] gifSure = new int[0];
	private static int gifToplam;
	private static int sonGifKare = -1;

	// Video
	private static Thread videoIs;
	private static volatile boolean videoDur;
	private static volatile int[] videoKare;
	private static volatile int videoNo;
	private static int yuklenenNo = -1;
	private static long sonVideoYukleme;

	private ArkaPlan() {
	}

	static String hata() {
		return hata;
	}

	static boolean var() {
		return doku != null;
	}

	static boolean video(String yol) {
		String s = yol.toLowerCase(Locale.ROOT);
		return s.endsWith(".mp4") || s.endsWith(".m4v") || s.endsWith(".mov");
	}

	/** Wallpaper Engine bağlıysa duvar kağıdının adı ve ne kullanıldığı; değilse boş. */
	static String bilgi() {
		return bilgi;
	}

	private static String bilgi = "";

	/** Ayardaki dosyayı (değiştiyse) yükler. {@link WallpaperEngine#SECIM} ise masaüstündeki duvar kağıdını bulur. */
	static void hazirla(String yol) {
		if (yol.equals(yukluYol)) {
			return;
		}
		birak();
		yukluYol = yol;
		hata = "";
		bilgi = "";
		if (yol.isEmpty()) {
			return;
		}
		if (WallpaperEngine.SECIM.equals(yol)) {
			WallpaperEngine.Sonuc s = WallpaperEngine.aktif();
			if (s.adaylar().isEmpty()) {
				hata = s.hata();
				return;
			}
			String ilkHata = "";
			for (File f : s.adaylar()) {
				if (dosyaYukle(f, yol)) {
					boolean vid = video(f.getName());
					bilgi = s.ad() + (vid ? " (video)" : " (" + (s.tur().equals("video") ? "video açılamadı, " : "") + "önizleme)");
					hata = "";
					return;
				}
				if (ilkHata.isEmpty()) {
					ilkHata = hata;
				}
			}
			hata = "Wallpaper Engine: " + ilkHata;
			return;
		}
		dosyaYukle(new File(yol), yol);
	}

	/** Tek dosyayı yükler; başarısızsa {@link #hata} dolar ve false döner. */
	private static boolean dosyaYukle(File f, String yol) {
		if (!f.isFile()) {
			hata = "Dosya bulunamadı: " + f.getName();
			return false;
		}
		String yol0 = f.getPath();
		try {
			String s = yol0.toLowerCase(Locale.ROOT);
			if (video(s)) {
				videoBaslat(f);
			} else if (s.endsWith(".gif")) {
				gifYukle(f);
			} else {
				BufferedImage r = ImageIO.read(f);
				if (r == null) {
					hata = "Bu resim biçimi okunamadı (PNG, JPG, BMP veya GIF kullan)";
					return false;
				}
				r = olcekle(r, MAX_RESIM);
				dokuKur(r.getWidth(), r.getHeight());
				int[] p = r.getRGB(0, 0, w, h, null, 0, w);
				yukle(p);
			}
			return doku != null;
		} catch (Throwable e) {
			hata = "Açılamadı: " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " — " + e.getMessage());
			CevherClientMod.LOGGER.warn("Vaka arka planı yüklenemedi ({}): {}", f.getName(), e.toString());
			String b = bilgi;
			birak();
			yukluYol = yol;
			bilgi = b;
			return false;
		}
	}

	/** Ekran kapanınca belleği ve video iş parçacığını bırakır. */
	static void birak() {
		videoDur = true;
		if (videoIs != null) {
			videoIs.interrupt();
			videoIs = null;
		}
		videoKare = null;
		yuklenenNo = -1;
		gifKareler = List.of();
		gifSure = new int[0];
		sonGifKare = -1;
		if (doku != null) {
			Minecraft.getInstance().getTextureManager().release(ID);
			doku = null;
		}
		yukluYol = "";
	}

	private static void dokuKur(int gw, int gh) {
		w = gw;
		h = gh;
		// Varsayılan örnekleme "en yakın komşu"dur (piksel piksel görünür); büyütmede yumuşak olsun diye doğrusal süzgeç.
		doku = new DynamicTexture(() -> "cevherclient vaka arka planı", new NativeImage(w, h, false)) {
			{
				this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
			}
		};
		Minecraft.getInstance().getTextureManager().register(ID, doku);
	}

	private static void yukle(int[] argb) {
		NativeImage img = doku.getPixels();
		if (img == null) {
			return;
		}
		int i = 0;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				img.setPixel(x, y, argb[i++] | 0xFF000000);
			}
		}
		doku.upload();
	}

	/** Görüntüyü oranını bozmadan ekranı kaplayacak biçimde (kenarlar kırpılabilir) çizer. */
	static void ciz(GuiGraphics g, int sw, int sh) {
		if (doku == null) {
			return;
		}
		if (!gifKareler.isEmpty()) {
			long t = gifToplam <= 0 ? 0 : Util.getMillis() % gifToplam;
			int k = 0;
			while (k < gifSure.length - 1 && t >= gifSure[k]) {
				t -= gifSure[k];
				k++;
			}
			if (k != sonGifKare) {
				sonGifKare = k;
				yukle(gifKareler.get(k));
			}
		} else if (videoKare != null && videoNo != yuklenenNo && System.nanoTime() - sonVideoYukleme >= KARE_NS) {
			// Render thread'i kare başına en fazla HEDEF_FPS kez yüklesin (piksel yazımı + GPU aktarımı pahalı).
			yuklenenNo = videoNo;
			sonVideoYukleme = System.nanoTime();
			yukle(videoKare);
		}
		// Ekranı tamamen kapla (tam ekran): kaynak oranı farklıysa taşan kenarlar kırpılır, boşluk kalmaz.
		float s = Math.max(sw / (float) w, sh / (float) h);
		float x = (sw - w * s) / 2f;
		float y = (sh - h * s) / 2f;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(s, s);
		g.blit(RenderPipelines.GUI_TEXTURED, ID, 0, 0, 0, 0, w, h, w, h, 0xFFFFFFFF);
		g.pose().popMatrix();
	}

	private static BufferedImage olcekle(BufferedImage r, int max) {
		int rw = r.getWidth(), rh = r.getHeight();
		if (rw <= max && rh <= max && r.getType() == BufferedImage.TYPE_INT_ARGB) {
			return r;
		}
		float k = Math.min(1f, max / (float) Math.max(rw, rh));
		int nw = Math.max(1, Math.round(rw * k)), nh = Math.max(1, Math.round(rh * k));
		BufferedImage o = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = o.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(r, 0, 0, nw, nh, null);
		g.dispose();
		return o;
	}

	// ── GIF ────────────────────────────────────────────────────────────────────

	private static void gifYukle(File f) throws Exception {
		Iterator<ImageReader> it = ImageIO.getImageReadersByFormatName("gif");
		if (!it.hasNext()) {
			throw new IllegalStateException("GIF okuyucu yok");
		}
		ImageReader r = it.next();
		try (ImageInputStream in = ImageIO.createImageInputStream(f)) {
			r.setInput(in, false);
			int n = Math.min(MAX_GIF_KARE, r.getNumImages(true));
			int cw = 0, ch = 0;
			try {
				Node d = bul(r.getStreamMetadata().getAsTree("javax_imageio_gif_stream_1.0"), "LogicalScreenDescriptor");
				if (d != null) {
					cw = sayi(d, "logicalScreenWidth", 0);
					ch = sayi(d, "logicalScreenHeight", 0);
				}
			} catch (Exception ignored) {
				// akış bilgisi yok
			}
			BufferedImage tuval = null;
			List<int[]> kareler = new ArrayList<>();
			int[] sureler = new int[n];
			int toplam = 0;
			for (int i = 0; i < n; i++) {
				BufferedImage kare = r.read(i);
				IIOMetadata m = r.getImageMetadata(i);
				Node kok = m.getAsTree("javax_imageio_gif_image_1.0");
				Node desc = bul(kok, "ImageDescriptor");
				Node gce = bul(kok, "GraphicControlExtension");
				int ox = desc == null ? 0 : sayi(desc, "imageLeftPosition", 0);
				int oy = desc == null ? 0 : sayi(desc, "imageTopPosition", 0);
				int gecikme = gce == null ? 10 : sayi(gce, "delayTime", 10);
				String atma = gce == null ? "none" : yazi(gce, "disposalMethod");
				if (tuval == null) {
					tuval = new BufferedImage(cw > 0 ? cw : kare.getWidth(), ch > 0 ? ch : kare.getHeight(), BufferedImage.TYPE_INT_ARGB);
				}
				BufferedImage once = "restoreToPrevious".equals(atma) ? kopya(tuval) : null;
				Graphics2D g = tuval.createGraphics();
				g.drawImage(kare, ox, oy, null);
				g.dispose();
				BufferedImage kucuk = olcekle(kopya(tuval), MAX_HAREKET);
				kareler.add(kucuk.getRGB(0, 0, kucuk.getWidth(), kucuk.getHeight(), null, 0, kucuk.getWidth()));
				if (i == 0) {
					dokuKur(kucuk.getWidth(), kucuk.getHeight());
				}
				sureler[i] = Math.max(20, gecikme * 10);
				toplam += sureler[i];
				if ("restoreToBackgroundColor".equals(atma)) {
					Graphics2D c = tuval.createGraphics();
					c.setComposite(java.awt.AlphaComposite.Clear);
					c.fillRect(ox, oy, kare.getWidth(), kare.getHeight());
					c.dispose();
				} else if (once != null) {
					tuval = once;
				}
			}
			gifKareler = kareler;
			gifSure = sureler;
			gifToplam = toplam;
			sonGifKare = -1;
		} finally {
			r.dispose();
		}
	}

	private static BufferedImage kopya(BufferedImage b) {
		BufferedImage o = new BufferedImage(b.getWidth(), b.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = o.createGraphics();
		g.drawImage(b, 0, 0, null);
		g.dispose();
		return o;
	}

	private static Node bul(Node kok, String ad) {
		for (Node n = kok.getFirstChild(); n != null; n = n.getNextSibling()) {
			if (ad.equals(n.getNodeName())) {
				return n;
			}
		}
		return null;
	}

	private static String yazi(Node n, String ad) {
		NamedNodeMap a = n.getAttributes();
		Node x = a == null ? null : a.getNamedItem(ad);
		return x == null ? "" : x.getNodeValue();
	}

	private static int sayi(Node n, String ad, int varsayilan) {
		try {
			return Integer.parseInt(yazi(n, ad));
		} catch (NumberFormatException e) {
			return varsayilan;
		}
	}

	// ── video ──────────────────────────────────────────────────────────────────

	private static void videoBaslat(File f) throws Exception {
		// İlk kareyi hemen çöz: boyutu öğren, dosya açılamıyorsa hata ver.
		int[] boyut = new int[2];
		try (SeekableByteChannel ch = NIOUtils.readableChannel(f)) {
			FrameGrab grab = FrameGrab.createFrameGrab(ch);
			Picture p = grab.getNativeFrame();
			if (p == null) {
				throw new IllegalStateException("videoda kare yok");
			}
			int[] ilk = kareCevir(p, boyut);
			dokuKur(boyut[0], boyut[1]);
			yukle(ilk);
		}
		videoDur = false;
		Path yol = f.toPath();
		videoIs = new Thread(() -> videoDongu(yol.toFile()), "Cevherclient vaka arka plan videosu");
		videoIs.setDaemon(true);
		videoIs.setPriority(Thread.MIN_PRIORITY);
		videoIs.start();
	}

	private static void videoDongu(File f) {
		int[] boyut = new int[2];
		while (!videoDur) {
			try (SeekableByteChannel ch = NIOUtils.readableChannel(f)) {
				FrameGrab grab = FrameGrab.createFrameGrab(ch);
				double fps = 30;
				try {
					var meta = grab.getVideoTrack().getMeta();
					if (meta.getTotalDuration() > 0 && meta.getTotalFrames() > 0) {
						fps = meta.getTotalFrames() / meta.getTotalDuration();
					}
				} catch (Exception ignored) {
					// varsayılan 30
				}
				long kareNs = Math.max(KARE_NS, Math.round(1_000_000_000d / Math.max(1, Math.min(HEDEF_FPS, fps))));
				long sonraki = System.nanoTime();
				Picture p;
				while (!videoDur && (p = grab.getNativeFrame()) != null) {
					int[] k = kareCevir(p, boyut);
					if (boyut[0] == w && boyut[1] == h) {
						videoKare = k;
						videoNo++;
					}
					sonraki += kareNs;
					long bekleNs = sonraki - System.nanoTime();
					if (bekleNs > 0) {
						Thread.sleep(bekleNs / 1_000_000L, (int) (bekleNs % 1_000_000L));
					} else if (bekleNs < -1_000_000_000L) {
						sonraki = System.nanoTime(); // çözme yetişemiyor: geride kalma
					}
				}
			} catch (InterruptedException e) {
				return;
			} catch (Throwable e) {
				hata = "Video oynatılamadı: " + e.getClass().getSimpleName();
				CevherClientMod.LOGGER.warn("Vaka arka plan videosu durdu: {}", e.toString());
				return;
			}
		}
	}

	/**
	 * JCodec karesini ARGB'ye çevirir ve en fazla {@link #MAX_HAREKET} genişliğe küçültür. boyut[0..1] = çıktı boyutu.
	 * YUV420 karelerde tam kareyi dönüştürmek yerine yalnızca örneklenen pikseller çevrilir (~%60 daha hızlı).
	 */
	private static int[] kareCevir(Picture p, int[] boyut) {
		int kw = p.getCroppedWidth(), kh = p.getCroppedHeight();
		float k = Math.min(1f, MAX_HAREKET / (float) Math.max(kw, kh));
		int ow = Math.max(1, Math.round(kw * k)), oh = Math.max(1, Math.round(kh * k));
		int[] o = new int[ow * oh];
		boyut[0] = ow;
		boyut[1] = oh;
		ColorSpace cs = p.getColor();
		if (cs == ColorSpace.YUV420 || cs == ColorSpace.YUV420J) {
			boolean tam = cs == ColorSpace.YUV420J;
			byte[] yp = p.getPlaneData(0), up = p.getPlaneData(1), vp = p.getPlaneData(2);
			int yw = p.getPlaneWidth(0), cw = p.getPlaneWidth(1);
			for (int y = 0; y < oh; y++) {
				int sy = Math.min(kh - 1, (int) (y / k));
				for (int x = 0; x < ow; x++) {
					int sx = Math.min(kw - 1, (int) (x / k));
					int yy = yp[sy * yw + sx] + 128;
					int ci = (sy >> 1) * cw + (sx >> 1);
					int u = up[ci], v = vp[ci];
					int r, g, b;
					if (tam) {
						r = yy + ((359 * v) >> 8);
						g = yy - ((88 * u + 183 * v) >> 8);
						b = yy + ((454 * u) >> 8);
					} else {
						int c = 298 * (yy - 16);
						r = (c + 409 * v + 128) >> 8;
						g = (c - 100 * u - 208 * v + 128) >> 8;
						b = (c + 516 * u + 128) >> 8;
					}
					r = r < 0 ? 0 : Math.min(255, r);
					g = g < 0 ? 0 : Math.min(255, g);
					b = b < 0 ? 0 : Math.min(255, b);
					o[y * ow + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
				}
			}
			return o;
		}
		Picture rgb = Picture.create(p.getWidth(), p.getHeight(), ColorSpace.RGB);
		Transform t = ColorUtil.getTransform(cs, ColorSpace.RGB);
		if (t == null) {
			throw new IllegalStateException("desteklenmeyen renk biçimi " + cs);
		}
		t.transform(p, rgb);
		int stride = rgb.getWidth() * 3;
		byte[] d = rgb.getPlaneData(0);
		for (int y = 0; y < oh; y++) {
			int satir = Math.min(kh - 1, (int) (y / k)) * stride;
			for (int x = 0; x < ow; x++) {
				int i = satir + Math.min(kw - 1, (int) (x / k)) * 3;
				o[y * ow + x] = 0xFF000000 | ((d[i] + 128) << 16) | ((d[i + 1] + 128) << 8) | (d[i + 2] + 128);
			}
		}
		return o;
	}
}
