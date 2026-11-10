package net.cevherclient.client.sinif;

import com.google.gson.JsonObject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.cevherclient.client.sinif.SinifVeri.Agac;
import net.cevherclient.client.sinif.SinifVeri.Dugum;
import net.cevherclient.client.sinif.SinifVeri.Kenar;
import net.cevherclient.client.sinif.SinifVeri.Parca;
import net.cevherclient.client.sinif.SinifVeri.Satir;
import net.cevherclient.client.sinif.SinifVeri.Sinif;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Stat ağacı ekranı: üstte sınıf sekmeleri (amblem + ad, aktif sınıf parlak), turuncu ayraç, sağ üstte "Kalan
 * puan" paneli, ortada elmas ağaç. Yollar ve düğümler tek bir dokuda çizilir ve yalnızca veri değişince
 * yeniden üretilir. Yol rengi: gri = kapalı, beyaz = açılabilir, turuncu = açık yol.
 */
public final class SinifAgaciEkrani extends SinifEkrani {
	private static final float SEKME_ARALIK = 64;
	private static final float SEKME_Y = -149;
	private static final float AMBLEM = 30;
	private static final float AYRAC_Y = -114;
	private static final float PANEL_X = 206;
	private static final float PANEL_Y = -168;
	private static final float PANEL_W = 108;
	private static final float PANEL_H = 34;

	private Agac veri;
	private long surum;
	private final Map<String, Dugum> dugumler = new HashMap<>();
	private float aralik = 30;
	private float merkezX;
	private float merkezY;
	private int minX;
	private int maxX;
	private int minY;
	private int maxY;
	private String amblem = "";

	public SinifAgaciEkrani(Agac veri) {
		super(Component.literal("Stat Ağacı"));
		guncelle(veri);
	}

	public void guncelle(Agac yeni) {
		this.veri = yeni;
		surum++;
		dugumler.clear();
		for (Dugum d : yeni.dugumler()) {
			dugumler.put(d.id(), d);
		}
		amblem = "";
		for (Sinif s : yeni.siniflar()) {
			if (s.aktif()) {
				amblem = s.amblem();
			}
		}
		yerlesim();
		bildir(yeni.bildirim());
	}

	private void yerlesim() {
		minX = Integer.MAX_VALUE;
		maxX = Integer.MIN_VALUE;
		minY = Integer.MAX_VALUE;
		maxY = Integer.MIN_VALUE;
		for (Dugum d : veri.dugumler()) {
			minX = Math.min(minX, d.x());
			maxX = Math.max(maxX, d.x());
			minY = Math.min(minY, d.y());
			maxY = Math.max(maxY, d.y());
		}
		if (minX > maxX) {
			minX = maxX = minY = maxY = 0;
		}
		float spanX = Math.max(1, maxX - minX);
		float spanY = Math.max(1, maxY - minY);
		aralik = Math.min(32f, Math.min(252f / spanY, 440f / spanX));
		float ortaY = (AYRAC_Y + 12 + TH / 2f - 14) / 2f;
		merkezX = -(minX + maxX) / 2f * aralik;
		merkezY = ortaY - (minY + maxY) / 2f * aralik;
	}

	private float k() {
		return aralik / 30f;
	}

	private float dx(Dugum d) {
		return merkezX + d.x() * aralik;
	}

	private float dy(Dugum d) {
		return merkezY + d.y() * aralik;
	}

	private float yaricap(Dugum d) {
		float r = switch (d.boyut()) {
			case "baslangic" -> 9.5f;
			case "buyuk" -> 7.5f;
			default -> 5.6f;
		};
		return r * k();
	}

	private float sekmeX(int i) {
		return (i - (veri.siniflar().size() - 1) / 2f) * SEKME_ARALIK;
	}

	private float sifirlaX() {
		return merkezX + maxX * aralik + 4 * k();
	}

	private float sifirlaY() {
		return merkezY + maxY * aralik - 2 * k();
	}

	// --- Çizim ---

	@Override
	protected float parlaklikMerkezi() {
		return 0.56f;
	}

	@Override
	protected void icerik(GuiGraphics g, float mx, float my, float alpha, boolean etkilesim) {
		ustBar(g, mx, my, alpha, etkilesim);
		puanPaneli(g, alpha);
		agac(g, mx, my, alpha, etkilesim);
		sifirlaDugmesi(g, mx, my, alpha, etkilesim);
		yetenekLogo(g, mx, my, alpha, etkilesim);
		yetenekPaneli(g, mx, my, alpha, etkilesim);
	}

	// --- Yetenek paneli (sol üst logo + soldan açılan liste) ---

	/** Sunucudan gelen son yetenek listesi (ekran yeniden açılsa da korunur). */
	static SinifVeri.Yetenekler yetenekler;
	private static final float LOGO_D = 30;
	private static final float YP_W = 176;
	private static final float YP_SATIR = 27;
	private boolean yetenekAcik;
	private long yetenekT0;
	private long yetenekSurum;

	public void yetenekYenile() {
		yetenekSurum++;
	}

	private float logoX() {
		return sol() + 30;
	}

	private float logoY() {
		return ust() + 28;
	}

	private float panelT() {
		float t = Math.min(1f, (Util.getMillis() - yetenekT0) / 220f);
		t = yetenekAcik ? t : 1f - t;
		return 1f - (1f - t) * (1f - t);
	}

	private void yetenekLogo(GuiGraphics g, float mx, float my, float alpha, boolean etkilesim) {
		float cx = logoX();
		float cy = logoY();
		boolean ustunde = etkilesim && Math.hypot(mx - cx, my - cy) <= LOGO_D / 2f + 2;
		String ab = amblem;
		float pay = 6;
		float boy = LOGO_D + pay * 2;
		SinifDoku.ciz(g, "agac|ylogo|" + ab + "|" + yetenekAcik + "|" + ustunde, 1, cx - boy / 2f, cy - boy / 2f, boy, boy,
			alpha, p -> {
				p.setColor(SinifCizim.awt(yetenekAcik || ustunde ? 0xFFF4EEE8 : 0xFFD8D4D0));
				p.fill(new Ellipse2D.Float(pay, pay, LOGO_D, LOGO_D));
				if (yetenekAcik) {
					p.setColor(SinifCizim.awt(SinifCizim.TURUNCU));
					p.setStroke(new BasicStroke(1.4f));
					p.draw(new Ellipse2D.Float(pay - 1, pay - 1, LOGO_D + 2, LOGO_D + 2));
				}
				SinifCizim.amblemCiz(p, ab, 0xFF222222, pay + 4, pay + 4, LOGO_D - 8);
			});
		SinifCizim.satir(g, Satir.duz("Yetenekler", 0xFFD8D4D0), cx, cy + LOGO_D / 2f + 3, 6.2f, true, 1, alpha);
		if (ustunde) {
			ipucu = List.of(Satir.kalin("Yetenekler", 0xFFF2D4B0), Satir.duz("Tıkla: yetenek listesini aç/kapat", 0xFFBBBBBB));
		}
	}

	private int secimSlot;
	private static final float YP_SATIR2 = 30;
	private static final float IKON = 24;

	private float ypX() {
		return sol() + 8 - (1f - panelT()) * (YP_W + 16);
	}

	private float ypY() {
		return ust() + 60;
	}

	/** Seçim ekranında gösterilecek yetenekler (slot türüne uyanlar; kilitliler soluk). */
	private List<SinifVeri.Yetenek> secimListesi() {
		List<SinifVeri.Yetenek> l = new ArrayList<>();
		if (yetenekler == null) {
			return l;
		}
		boolean pasifSlot = secimSlot == 7;
		for (SinifVeri.Yetenek s : yetenekler.liste()) {
			if (s.pasif() == pasifSlot) {
				l.add(s);
			}
		}
		return l;
	}

	private SinifVeri.Yetenek slottaki(int slot) {
		if (yetenekler == null) {
			return null;
		}
		for (SinifVeri.Yetenek s : yetenekler.liste()) {
			if (s.slot() == slot) {
				return s;
			}
		}
		return null;
	}

	private void ikonKutu(GuiGraphics g, String id, float x, float y, boolean acik, float alpha) {
		java.awt.image.BufferedImage img = SinifIkon.al(id);
		SinifDoku.ciz(g, "yik|" + id + "|" + acik + "|" + (img != null), 1, x, y, IKON, IKON, alpha, p -> {
			p.setColor(SinifCizim.awt(0xFF262422));
			p.fill(new RoundRectangle2D.Float(0, 0, IKON, IKON, 2, 2));
			if (img != null) {
				p.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
					java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				p.drawImage(img, 1.5f > 0 ? 2 : 0, 2, (int) IKON - 4, (int) IKON - 4, null);
			}
			if (!acik) {
				p.setColor(SinifCizim.awt(0x99000000));
				p.fill(new RoundRectangle2D.Float(0, 0, IKON, IKON, 2, 2));
			}
			p.setColor(SinifCizim.awt(SinifCizim.CERCEVE));
			p.setStroke(new BasicStroke(0.8f));
			p.draw(new RoundRectangle2D.Float(0.4f, 0.4f, IKON - 0.8f, IKON - 0.8f, 2, 2));
		});
	}

	private void yetenekPaneli(GuiGraphics g, float mx, float my, float alpha, boolean etkilesim) {
		float t = panelT();
		if (t <= 0.01f) {
			return;
		}
		SinifVeri.Yetenekler y = yetenekler;
		float x = ypX();
		float yy = ypY();
		float a = alpha * t;
		float h = 52 + 8 * YP_SATIR2 + 6;
		SinifDoku.ciz(g, "agac|ypanel|" + yetenekSurum + "|" + secimSlot, 1, x, yy, YP_W, h, a, p -> {
			p.setColor(SinifCizim.awt(0xE60C0A09));
			p.fill(new RoundRectangle2D.Float(0, 0, YP_W, h, 4, 4));
			p.setColor(SinifCizim.awt(SinifCizim.CERCEVE));
			p.setStroke(new BasicStroke(0.8f));
			p.draw(new RoundRectangle2D.Float(0.4f, 0.4f, YP_W - 0.8f, h - 0.8f, 4, 4));
			SinifCizim.ayrac(p, 8, 44, YP_W - 16, 0.8f);
		});
		if (y == null) {
			SinifCizim.satir(g, Satir.duz("Yükleniyor...", 0xFF9A9A9A), x + 12, yy + 52, 7f, false, 0, a);
			return;
		}
		boolean etk = etkilesim && t > 0.95f;
		if (secimSlot == 0) {
			SinifCizim.satir(g, new Satir(List.of(new Parca(veri.siniflar().stream().filter(Sinif::aktif).map(s -> s.ad().duz()).findFirst().orElse(y.sinif()) + " - ", 0xFFF0A868, false),
				new Parca("Seviye " + y.seviye(), 0xFF8DA4FF, false))), x + 10, yy + 7, 6.2f, false, 0, a);
			SinifCizim.satir(g, Satir.kalin("Yetenekler", 0xFFFFFFFF), x + 10, yy + 18, 10f, false, 0, a);
			for (int i = 1; i <= 7; i++) {
				float ry = yy + 52 + (i - 1) * YP_SATIR2;
				SinifVeri.Yetenek s = slottaki(i);
				boolean ustunde = etk && mx >= x && mx <= x + YP_W && my >= ry && my < ry + YP_SATIR2;
				if (ustunde) {
					SinifDoku.ciz(g, "yp|hover", 1, x + 4, ry, YP_W - 8, YP_SATIR2 - 2, a * 0.5f, p -> {
						p.setColor(SinifCizim.awt(0x33F0A868));
						p.fill(new RoundRectangle2D.Float(0, 0, YP_W - 8, YP_SATIR2 - 2, 3, 3));
					});
				}
				ikonKutu(g, s == null ? "" : s.id(), x + 10, ry + 2, true, a);
				SinifCizim.satir(g, Satir.duz(i == 7 ? "Pasif" : "Yetenek " + i, 0xFF9A9A9A), x + 42, ry + 3, 5.6f, false, 0, a);
				SinifCizim.satir(g, s == null ? Satir.duz("Boş slot", 0xFF6E6E6E) : s.ad(), x + 42, ry + 12.5f, 7.8f, false, 0, a);
				if (ustunde && s != null) {
					List<Satir> ip = new ArrayList<>();
					ip.add(s.ad());
					ip.addAll(s.lore());
					ipucu = ip;
				}
			}
			return;
		}
		// Yetenek seçimi
		boolean geriUst = etk && mx >= x && mx <= x + YP_W && my >= yy && my < yy + 44;
		SinifCizim.satir(g, Satir.duz("◀ Geri", geriUst ? 0xFFFFE27A : 0xFFBBBBBB), x + 10, yy + 7, 6.4f, false, 0, a);
		SinifCizim.satir(g, Satir.kalin(secimSlot == 7 ? "Pasif slot" : "Yetenek " + secimSlot, 0xFFFFFFFF), x + 10, yy + 18,
			10f, false, 0, a);
		float ry = yy + 52;
		if (slottaki(secimSlot) != null) {
			boolean ustunde = etk && mx >= x && mx <= x + YP_W && my >= ry && my < ry + YP_SATIR2;
			SinifCizim.satir(g, Satir.kalin("✕", 0xFFFF6B6B), x + 17, ry + 6, 11f, false, 0, a);
			SinifCizim.satir(g, Satir.duz("Slotu boşalt", ustunde ? 0xFFFFFFFF : 0xFFBBBBBB), x + 42, ry + 9.5f, 7.8f, false, 0, a);
			ry += YP_SATIR2;
		}
		for (SinifVeri.Yetenek s : secimListesi()) {
			boolean ustunde = etk && mx >= x && mx <= x + YP_W && my >= ry && my < ry + YP_SATIR2;
			if (ustunde && s.acik()) {
				SinifDoku.ciz(g, "yp|hover", 1, x + 4, ry, YP_W - 8, YP_SATIR2 - 2, a * 0.5f, p -> {
					p.setColor(SinifCizim.awt(0x33F0A868));
					p.fill(new RoundRectangle2D.Float(0, 0, YP_W - 8, YP_SATIR2 - 2, 3, 3));
				});
			}
			ikonKutu(g, s.id(), x + 10, ry + 2, s.acik(), a);
			Satir ad = s.acik() ? s.ad() : new Satir(s.ad().parcalar().stream()
				.map(pp -> new Parca(pp.metin(), karart(pp.renk(), 0.45f), pp.kalin())).toList());
			SinifCizim.satir(g, ad, x + 42, ry + 3, 7.8f, false, 0, a);
			Satir alt = !s.acik() ? Satir.duz("Seviye " + s.acilis() + "'de açılır", 0xFF8A8A8A)
				: s.slot() > 0 ? Satir.duz("Şu an: " + (s.slot() == 7 ? "Pasif" : "Yetenek " + s.slot()), 0xFFF0A868)
				: Satir.duz("Tıkla: yerleştir", 0xFF9FD08A);
			SinifCizim.satir(g, alt, x + 42, ry + 15, 5.8f, false, 0, a);
			if (ustunde) {
				List<Satir> ip = new ArrayList<>();
				ip.add(s.ad());
				ip.addAll(s.lore());
				ipucu = ip;
			}
			ry += YP_SATIR2;
		}
	}

	/** Panel içi tık. Tıklama panele düştüyse true. */
	private boolean yetenekTikla(float mx, float my) {
		if (panelT() < 0.95f || !yetenekAcik || yetenekler == null) {
			return false;
		}
		float x = ypX();
		float yy = ypY();
		if (mx < x || mx > x + YP_W || my < yy || my > yy + 52 + 8 * YP_SATIR2) {
			return false;
		}
		if (secimSlot == 0) {
			int i = (int) Math.floor((my - (yy + 52)) / YP_SATIR2) + 1;
			if (i >= 1 && i <= 7) {
				secimSlot = i;
				ses(1.1f);
				yetenekSurum++;
			}
			return true;
		}
		if (my < yy + 44) {
			secimSlot = 0;
			ses(0.9f);
			yetenekSurum++;
			return true;
		}
		float ry = yy + 52;
		if (slottaki(secimSlot) != null) {
			if (my >= ry && my < ry + YP_SATIR2) {
				bagla(secimSlot, "");
				return true;
			}
			ry += YP_SATIR2;
		}
		for (SinifVeri.Yetenek s : secimListesi()) {
			if (my >= ry && my < ry + YP_SATIR2) {
				if (s.acik()) {
					bagla(secimSlot, s.id());
				} else {
					ses(0.6f);
					bildir(Satir.duz("Bu yetenek Seviye " + s.acilis() + "'de açılır.", 0xFFFFE27A));
				}
				return true;
			}
			ry += YP_SATIR2;
		}
		return true;
	}

	private void bagla(int slot, String id) {
		JsonObject o = new JsonObject();
		o.addProperty("t", "bagla");
		o.addProperty("slot", slot);
		o.addProperty("id", id);
		SinifNet.gonder(o);
		secimSlot = 0;
		ses(1.3f);
		yetenekSurum++;
	}

	private void ustBar(GuiGraphics g, float mx, float my, float alpha, boolean etkilesim) {
		float genis = Math.max(TW, sag() - sol());
		SinifDoku.ciz(g, "agac|ayrac", 1, -genis / 2f, AYRAC_Y - 3, genis, 8, alpha,
			p -> SinifCizim.ayrac(p, 0, 3, genis, 0.9f));
		List<Sinif> siniflar = veri.siniflar();
		for (int i = 0; i < siniflar.size(); i++) {
			Sinif s = siniflar.get(i);
			float cx = sekmeX(i);
			boolean ustunde = etkilesim && Math.hypot(mx - cx, my - SEKME_Y) <= AMBLEM / 2f + 2;
			boolean aktif = s.aktif();
			float pay = 10;
			float boy = AMBLEM + pay * 2;
			String anahtar = "sekme|" + s.id() + "|" + aktif + "|" + ustunde;
			SinifDoku.ciz(g, anahtar, 1, cx - boy / 2f, SEKME_Y - boy / 2f, boy, boy, alpha, p -> {
				if (aktif || ustunde) {
					p.setPaint(new RadialGradientPaint(new Point2D.Float(boy / 2f, boy / 2f), boy / 2f,
						new float[] {0f, 0.6f, 1f},
						new Color[] {SinifCizim.awt(SinifCizim.TURUNCU, aktif ? 0.45f : 0.25f),
							SinifCizim.awt(SinifCizim.TURUNCU, aktif ? 0.12f : 0.06f), SinifCizim.awt(SinifCizim.TURUNCU, 0f)},
						MultipleGradientPaint.CycleMethod.NO_CYCLE));
					p.fill(new Rectangle2D.Float(0, 0, boy, boy));
				}
				SinifCizim.amblemCiz(p, s.amblem(), s.renk(), pay, pay, AMBLEM);
				if (!aktif) {
					p.setColor(SinifCizim.awt(ustunde ? 0x50000000 : 0x8C000000));
					p.fill(new Ellipse2D.Float(pay, pay, AMBLEM, AMBLEM));
				}
			});
			Satir ad = s.ad();
			if (!aktif) {
				ad = new Satir(ad.parcalar().stream()
					.map(pp -> new Parca(pp.metin(), karart(pp.renk(), ustunde ? 0.85f : 0.62f), pp.kalin())).toList());
			} else {
				ad = new Satir(ad.parcalar().stream().map(pp -> new Parca(pp.metin(), 0xFFFFFFFF, true)).toList());
			}
			SinifCizim.satir(g, ad, cx, SEKME_Y + AMBLEM / 2f + 3, 7f, true, 1, alpha);
			if (ustunde) {
				ipucu = sekmeIpucu(s);
			}
		}
	}

	private List<Satir> sekmeIpucu(Sinif s) {
		List<Satir> l = new ArrayList<>();
		l.add(s.ad());
		if (s.aktif()) {
			l.add(Satir.duz("▶ Şu anki sınıfın", 0xFFF0A868));
		} else {
			l.add(s.seviye() > 0 ? Satir.duz("Kayıtlı ilerleme: Seviye " + s.seviye(), 0xFFBBBBBB)
				: Satir.duz("Bu sınıf 1. seviyeden başlar.", 0xFF8A8A8A));
			if (veri.degistirebilir()) {
				l.add(Satir.BOS);
				l.add(Satir.duz("Tıkla: bu sınıfa geç", 0xFFFFE27A));
			}
		}
		return l;
	}

	private static int karart(int renk, float f) {
		int r = Math.round(((renk >> 16) & 0xFF) * f);
		int gg = Math.round(((renk >> 8) & 0xFF) * f);
		int b = Math.round((renk & 0xFF) * f);
		return 0xFF000000 | (r << 16) | (gg << 8) | b;
	}

	private void puanPaneli(GuiGraphics g, float alpha) {
		float x = Math.min(PANEL_X, sag() - PANEL_W - 8);
		SinifDoku.ciz(g, "agac|panel", 1, x, PANEL_Y, PANEL_W, PANEL_H, alpha, p -> {
			p.setColor(SinifCizim.awt(SinifCizim.PANEL));
			p.fill(new RoundRectangle2D.Float(0, 0, PANEL_W, PANEL_H, 3, 3));
			p.setColor(SinifCizim.awt(SinifCizim.CERCEVE));
			p.setStroke(new BasicStroke(0.8f));
			p.draw(new RoundRectangle2D.Float(0.4f, 0.4f, PANEL_W - 0.8f, PANEL_H - 0.8f, 3, 3));
			SinifCizim.puanSimgesi(p, 16, PANEL_H / 2f, 8.5f);
		});
		Satir baslik = veri.puanYazi().bos() ? Satir.duz("Kalan puan", SinifCizim.YAZI) : veri.puanYazi();
		SinifCizim.satir(g, baslik, x + 31, PANEL_Y + 5, 6.2f, false, 0, alpha);
		SinifCizim.satir(g, Satir.kalin(String.valueOf(veri.puan()), 0xFFFFFFFF), x + 31, PANEL_Y + 14.5f, 10.5f, false, 0,
			alpha);
	}

	private void agac(GuiGraphics g, float mx, float my, float alpha, boolean etkilesim) {
		float pay = 12 * k() + 4;
		float x0 = merkezX + minX * aralik - pay;
		float y0 = merkezY + minY * aralik - pay;
		float w = (maxX - minX) * aralik + pay * 2;
		float h = (maxY - minY) * aralik + pay * 2;
		SinifDoku.ciz(g, "agac|govde", surum, x0, y0, w, h, alpha, p -> {
			p.translate(-x0, -y0);
			yollar(p);
			for (Dugum d : veri.dugumler()) {
				dugumCiz(p, d);
			}
		});

		Dugum ustunde = null;
		if (etkilesim) {
			for (Dugum d : veri.dugumler()) {
				float r = yaricap(d) + 2f;
				if (Math.abs(mx - dx(d)) + Math.abs(my - dy(d)) <= r) {
					ustunde = d;
					break;
				}
			}
		}
		if (ustunde != null) {
			float r = yaricap(ustunde) + 3.2f * k();
			float nabiz = 0.55f + 0.45f * (float) Math.sin((Util.getMillis() - acilis) / 180.0);
			float boy = r * 2 + 4;
			SinifDoku.ciz(g, "agac|vurgu|" + Math.round(r * 10), 1, dx(ustunde) - boy / 2f, dy(ustunde) - boy / 2f, boy, boy,
				alpha * nabiz, p -> {
					p.setColor(SinifCizim.awt(0xFFFFE0C8));
					p.setStroke(new BasicStroke(1.1f * k()));
					p.draw(SinifCizim.elmas(boy / 2f, boy / 2f, r));
				});
			List<Satir> l = new ArrayList<>();
			l.add(ustunde.ad());
			l.addAll(ustunde.lore());
			ipucu = l;
		}
	}

	private void yollar(Graphics2D p) {
		float k = k();
		for (int asama = 0; asama < 3; asama++) {
			for (Kenar e : veri.kenarlar()) {
				if (e.durum() != asama) {
					continue;
				}
				Dugum a = dugumler.get(e.a());
				Dugum b = dugumler.get(e.b());
				if (a == null || b == null) {
					continue;
				}
				float ax = dx(a);
				float ay = dy(a);
				float bx = dx(b);
				float by = dy(b);
				float uz = (float) Math.hypot(bx - ax, by - ay);
				if (uz < 1e-3f) {
					continue;
				}
				float ux = (bx - ax) / uz;
				float uy = (by - ay) / uz;
				float baslangic = yaricap(a) * 0.9f + 2.6f * k;
				float bitis = yaricap(b) * 0.9f + 2.6f * k;
				if (uz - baslangic - bitis < 2f) {
					continue;
				}
				Line2D.Float cizgi = new Line2D.Float(ax + ux * baslangic, ay + uy * baslangic, bx - ux * bitis, by - uy * bitis);
				int renk = asama == 2 ? SinifCizim.TURUNCU : asama == 1 ? SinifCizim.BEYAZ : SinifCizim.GRI;
				float kalinlik = (asama == 2 ? 1.7f : asama == 1 ? 1.45f : 1.3f) * k;
				if (asama == 2) {
					p.setColor(SinifCizim.awt(SinifCizim.TURUNCU, 0.16f));
					p.setStroke(new BasicStroke(kalinlik * 3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
					p.draw(cizgi);
				}
				p.setColor(SinifCizim.awt(renk, asama == 0 ? 0.85f : 1f));
				p.setStroke(new BasicStroke(kalinlik, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
					new float[] {3.0f * k, 2.4f * k}, 0f));
				p.draw(cizgi);
			}
		}
	}

	private void dugumCiz(Graphics2D p, Dugum d) {
		float x = dx(d);
		float y = dy(d);
		float r = yaricap(d);
		float k = k();
		int dolgu;
		int cerceve;
		int simge;
		boolean parlak = false;
		switch (d.durum()) {
			case "UNLOCKABLE" -> {
				dolgu = 0xFFE4E2E0;
				cerceve = 0xFFFFFFFF;
				simge = 0xFF3A3534;
			}
			case "UNLOCKED" -> {
				dolgu = 0xFF2E2019;
				cerceve = SinifCizim.TURUNCU;
				simge = SinifCizim.TURUNCU;
				parlak = d.boyut().equals("baslangic");
			}
			case "MAXED_OUT" -> {
				dolgu = SinifCizim.TURUNCU;
				cerceve = 0xFFFFD9BC;
				simge = 0xFF3A2418;
				parlak = true;
			}
			case "FULLY_LOCKED" -> {
				dolgu = 0xFF1E1A1A;
				cerceve = 0xFF5A3030;
				simge = 0xFF5A3030;
			}
			default -> {
				dolgu = 0xFF2B2826;
				cerceve = SinifCizim.GRI;
				simge = 0xFF77736F;
			}
		}
		if (parlak) {
			float rg = r * 2.6f;
			p.setPaint(new RadialGradientPaint(new Point2D.Float(x, y), rg, new float[] {0f, 1f},
				new Color[] {SinifCizim.awt(SinifCizim.TURUNCU, 0.42f), SinifCizim.awt(SinifCizim.TURUNCU, 0f)}));
			p.fill(new Ellipse2D.Float(x - rg, y - rg, rg * 2, rg * 2));
		}
		Path2D.Float elmas = SinifCizim.elmas(x, y, r);
		p.setColor(SinifCizim.awt(dolgu));
		p.fill(elmas);
		p.setColor(SinifCizim.awt(cerceve));
		p.setStroke(new BasicStroke(Math.max(0.7f, r * 0.14f)));
		p.draw(elmas);

		BufferedImage img = SinifCizim.simge(amblem, simge);
		if (img != null) {
			float boy = r * 1.02f;
			java.awt.geom.AffineTransform t = p.getTransform();
			p.translate(x - boy / 2f, y - boy / 2f);
			p.scale(boy / img.getWidth(), boy / img.getHeight());
			p.drawImage(img, 0, 0, null);
			p.setTransform(t);
		}

		if (d.max() > 1 && d.max() <= 10) {
			float nokta = 1.3f * k;
			float ara = 2.4f * k;
			float toplam = (d.max() - 1) * ara;
			float py = y + r + 2.4f * k;
			for (int i = 0; i < d.max(); i++) {
				float px = x - toplam / 2f + i * ara;
				p.setColor(i < d.seviye() ? SinifCizim.awt(SinifCizim.TURUNCU) : SinifCizim.awt(SinifCizim.GRI, 0.55f));
				p.fill(new Ellipse2D.Float(px - nokta / 2f, py - nokta / 2f, nokta, nokta));
			}
		}
	}

	private void sifirlaDugmesi(GuiGraphics g, float mx, float my, float alpha, boolean etkilesim) {
		float d = 15;
		float cx = sifirlaX();
		float cy = sifirlaY();
		boolean ustunde = etkilesim && Math.hypot(mx - cx, my - cy) <= d / 2f + 1.5f;
		SinifDoku.ciz(g, "agac|sifirla|" + ustunde, 1, cx - d / 2f, cy - d / 2f, d, d, alpha, p -> {
			int renk = ustunde ? SinifCizim.TURUNCU : 0xFFD8D4D0;
			p.setColor(SinifCizim.awt(renk));
			p.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			float m = 3.2f;
			p.draw(new Arc2D.Float(m, m, d - m * 2, d - m * 2, 70, 290, Arc2D.OPEN));
			double aci = Math.toRadians(70);
			float ux = (float) (d / 2f + Math.cos(aci) * (d / 2f - m));
			float uy = (float) (d / 2f - Math.sin(aci) * (d / 2f - m));
			Path2D.Float ok = new Path2D.Float();
			ok.moveTo(ux - 2.6f, uy - 1.6f);
			ok.lineTo(ux + 0.6f, uy);
			ok.lineTo(ux - 1.6f, uy + 2.6f);
			p.draw(ok);
			p.fill(new Ellipse2D.Float(d / 2f - 0.9f, d / 2f - 0.9f, 1.8f, 1.8f));
		});
		if (ustunde) {
			ipucu = veri.sifirlaIpucu().isEmpty() ? List.of(Satir.duz("Ağacı Sıfırla", 0xFFFF6B6B)) : veri.sifirlaIpucu();
		}
	}

	// --- Girdi ---

	@Override
	protected void tikla(float mx, float my, MouseButtonEvent olay) {
		if (olay.button() != 0 && olay.button() != 1) {
			return;
		}
		if (yetenekTikla(mx, my)) {
			return;
		}
		if (Math.hypot(mx - logoX(), my - logoY()) <= LOGO_D / 2f + 2) {
			yetenekAcik = !yetenekAcik;
			yetenekT0 = Util.getMillis();
			secimSlot = 0;
			SinifIkon.sifirla();
			yetenekSurum++;
			ses(yetenekAcik ? 1.2f : 0.9f);
			if (yetenekAcik) {
				gonder("yetenek");
			}
			return;
		}
		List<Sinif> siniflar = veri.siniflar();
		for (int i = 0; i < siniflar.size(); i++) {
			Sinif s = siniflar.get(i);
			if (Math.hypot(mx - sekmeX(i), my - SEKME_Y) <= AMBLEM / 2f + 2) {
				sekmeTikla(s);
				return;
			}
		}
		if (Math.hypot(mx - sifirlaX(), my - sifirlaY()) <= 9) {
			if (olay.hasShiftDown()) {
				ses(0.7f);
				gonder("sifirla");
			} else {
				ses(0.8f);
				bildir(Satir.duz("Sıfırlamak için Shift + Tık", 0xFFFFE27A));
			}
			return;
		}
		for (Dugum d : veri.dugumler()) {
			if (Math.abs(mx - dx(d)) + Math.abs(my - dy(d)) <= yaricap(d) + 2f) {
				ses(1.2f);
				JsonObject o = new JsonObject();
				o.addProperty("t", "dugum");
				o.addProperty("id", d.id());
				SinifNet.gonder(o);
				return;
			}
		}
	}

	private void sekmeTikla(Sinif s) {
		if (s.aktif()) {
			ses(1.0f);
			return;
		}
		if (!veri.degistirebilir()) {
			ses(0.6f);
			bildir(Satir.duz("Sınıf değiştirmek için yetkin yok.", 0xFFFF6B6B));
			return;
		}
		ses(1.1f);
		List<Satir> metin = new ArrayList<>();
		List<Parca> ilk = new ArrayList<>(s.ad().parcalar());
		ilk.add(new Parca(" sınıfına geçilsin mi?", 0xFFE8E2DC, false));
		metin.add(new Satir(ilk));
		metin.add(Satir.duz(s.seviye() > 0 ? "Kayıtlı ilerlemen: Seviye " + s.seviye() : "Bu sınıf 1. seviyeden başlar.",
			0xFF9A9A9A));
		metin.add(Satir.duz("Şu anki sınıfının ilerlemesi saklanır.", 0xFF9A9A9A));
		onay = new Onay(Satir.kalin("Sınıf Değiştir", 0xFFF2D4B0), metin, Satir.kalin("Geç", 0xFF2A1A10), () -> {
			JsonObject o = new JsonObject();
			o.addProperty("t", "sec");
			o.addProperty("sinif", s.id());
			o.addProperty("agac", true);
			SinifNet.gonder(o);
		});
	}

	private static void gonder(String tur) {
		JsonObject o = new JsonObject();
		o.addProperty("t", tur);
		SinifNet.gonder(o);
	}
}
