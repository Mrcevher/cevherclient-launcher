package net.cevherclient.client.sinif;

import com.google.gson.JsonObject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import net.cevherclient.client.sinif.SinifVeri.Parca;
import net.cevherclient.client.sinif.SinifVeri.Satir;
import net.cevherclient.client.sinif.SinifVeri.Secim;
import net.cevherclient.client.sinif.SinifVeri.Sinif;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Sınıf seçim ekranı: başlık, ayraç ve her sınıf için bir kart (amblem, ad, açıklama, kayıtlı ilerleme).
 * Karta tıklayınca onay kutusu açılır; seçim sunucuya gider, sunucu sınıfı değiştirip ekranı kapatır.
 * Zorunlu seçimde ekran kapatılırsa sunucu birkaç saniye sonra yeniden açar.
 */
public final class SinifSecimEkrani extends SinifEkrani {
	private static final float KART_W = 98;
	private static final float KART_H = 160;
	private static final float KART_ARA = 10;
	private static final float KART_Y = -96;
	private static final float AMBLEM = 46;

	private Secim veri;
	private boolean secimGonderildi;

	public SinifSecimEkrani(Secim veri) {
		super(Component.literal("Sınıfını Seç"));
		guncelle(veri);
	}

	public void guncelle(Secim yeni) {
		this.veri = yeni;
		secimGonderildi = false;
		bildir(yeni.bildirim());
	}

	private float kartX(int i) {
		int n = veri.siniflar().size();
		float toplam = n * KART_W + (n - 1) * KART_ARA;
		return -toplam / 2f + i * (KART_W + KART_ARA);
	}

	@Override
	protected float parlaklikMerkezi() {
		return 0.42f;
	}

	@Override
	protected void icerik(GuiGraphics g, float mx, float my, float alpha, boolean etkilesim) {
		Satir baslik = veri.baslik().bos() ? Satir.duz("Sınıfını Seç", 0xFFF2D4B0) : veri.baslik();
		SinifCizim.satir(g, baslik, 0, -158, 12f, false, 1, alpha);
		if (!veri.altbaslik().bos()) {
			SinifCizim.satir(g, veri.altbaslik(), 0, -137, 6.4f, true, 1, alpha);
		}
		float genis = Math.max(TW, sag() - sol());
		SinifDoku.ciz(g, "secim|ayrac", 1, -genis / 2f, -122, genis, 8, alpha, p -> SinifCizim.ayrac(p, 0, 3, genis, 0.9f));

		List<Sinif> siniflar = veri.siniflar();
		for (int i = 0; i < siniflar.size(); i++) {
			Sinif s = siniflar.get(i);
			float x = kartX(i);
			boolean ustunde = etkilesim && mx >= x && mx < x + KART_W && my >= KART_Y && my < KART_Y + KART_H;
			float y = KART_Y - (ustunde ? 2.5f : 0);
			kart(g, s, x, y, ustunde, alpha);
		}
	}

	private void kart(GuiGraphics g, Sinif s, float x, float y, boolean ustunde, float alpha) {
		boolean aktif = s.aktif();
		String anahtar = "secim|kart|" + s.id() + "|" + aktif + "|" + ustunde;
		SinifDoku.ciz(g, anahtar, 1, x, y, KART_W, KART_H, alpha, p -> {
			p.setColor(SinifCizim.awt(0xE8171413));
			p.fill(new RoundRectangle2D.Float(0, 0, KART_W, KART_H, 5, 5));
			if (ustunde || aktif) {
				p.setPaint(new RadialGradientPaint(new Point2D.Float(KART_W / 2f, 38), 60, new float[] {0f, 1f},
					new Color[] {SinifCizim.awt(s.renk(), ustunde ? 0.30f : 0.18f), SinifCizim.awt(s.renk(), 0f)},
					MultipleGradientPaint.CycleMethod.NO_CYCLE));
				p.fill(new RoundRectangle2D.Float(0, 0, KART_W, KART_H, 5, 5));
			}
			p.setColor(SinifCizim.awt(s.renk(), 0.85f));
			p.fill(new Rectangle2D.Float(10, 0.6f, KART_W - 20, 1.6f));
			int cerceve = ustunde ? SinifCizim.TURUNCU : aktif ? SinifCizim.CIZGI : SinifCizim.CERCEVE;
			p.setColor(SinifCizim.awt(cerceve, ustunde || aktif ? 1f : 0.85f));
			p.setStroke(new BasicStroke(ustunde ? 1.2f : 0.9f));
			p.draw(new RoundRectangle2D.Float(0.5f, 0.5f, KART_W - 1f, KART_H - 1f, 5, 5));
			SinifCizim.amblemCiz(p, s.amblem(), s.renk(), (KART_W - AMBLEM) / 2f, 14, AMBLEM);
		});
		Satir ad = new Satir(s.ad().parcalar().stream()
			.map(pp -> new Parca(pp.metin(), ustunde || aktif ? 0xFFFFFFFF : pp.renk(), true)).toList());
		SinifCizim.satir(g, ad, x + KART_W / 2f, y + 66, 9f, true, 1, alpha);

		float yy = y + 84;
		float boyut = 5.7f;
		float satirH = SinifCizim.yukseklik(boyut) + 0.8f;
		float sinir = y + KART_H - 24;
		for (Satir satir : s.aciklama()) {
			if (satir.bos()) {
				yy += satirH * 0.5f;
				continue;
			}
			for (Satir parca : SinifCizim.sar(satir, KART_W - 14, boyut, false)) {
				if (yy + satirH > sinir) {
					break;
				}
				SinifCizim.satir(g, parca, x + KART_W / 2f, yy, boyut, false, 1, alpha);
				yy += satirH;
			}
		}
		if (!s.durum().bos()) {
			SinifCizim.satir(g, s.durum(), x + KART_W / 2f, y + KART_H - 16, 5.8f, false, 1, alpha);
		}
	}

	// --- Girdi ---

	@Override
	protected void tikla(float mx, float my, MouseButtonEvent olay) {
		if (olay.button() != 0) {
			return;
		}
		List<Sinif> siniflar = veri.siniflar();
		for (int i = 0; i < siniflar.size(); i++) {
			float x = kartX(i);
			if (mx >= x && mx < x + KART_W && my >= KART_Y - 3 && my < KART_Y + KART_H) {
				sec(siniflar.get(i));
				return;
			}
		}
	}

	private void sec(Sinif s) {
		if (s.aktif()) {
			ses(0.8f);
			bildir(Satir.duz("Zaten bu sınıftasın.", 0xFFBBBBBB));
			return;
		}
		ses(1.1f);
		boolean ilk = veri.mevcut().isEmpty();
		List<Satir> metin = new ArrayList<>();
		List<Parca> satir = new ArrayList<>();
		if (ilk) {
			satir.add(new Parca("Sınıfını ", 0xFFE8E2DC, false));
			satir.addAll(s.ad().parcalar());
			satir.add(new Parca(" olarak seçiyorsun.", 0xFFE8E2DC, false));
			metin.add(new Satir(satir));
			metin.add(Satir.duz("Sınıfını daha sonra /sinif degistir ile değiştirebilirsin.", 0xFF9A9A9A));
		} else {
			satir.addAll(s.ad().parcalar());
			satir.add(new Parca(" sınıfına geçilsin mi?", 0xFFE8E2DC, false));
			metin.add(new Satir(satir));
			metin.add(Satir.duz(s.seviye() > 0 ? "Kayıtlı ilerlemen: Seviye " + s.seviye() : "Bu sınıf 1. seviyeden başlar.",
				0xFF9A9A9A));
			metin.add(Satir.duz("Şu anki sınıfının ilerlemesi saklanır.", 0xFF9A9A9A));
		}
		onay = new Onay(Satir.kalin(ilk ? "Sınıf Seçimi" : "Sınıf Değiştir", 0xFFF2D4B0), metin,
			Satir.kalin(ilk ? "Seç" : "Geç", 0xFF2A1A10), () -> {
				secimGonderildi = true;
				JsonObject o = new JsonObject();
				o.addProperty("t", "sec");
				o.addProperty("sinif", s.id());
				SinifNet.gonder(o);
			});
	}

	@Override
	public void removed() {
		super.removed();
		if (veri.zorunlu() && !secimGonderildi && !sunucuKapatti) {
			JsonObject o = new JsonObject();
			o.addProperty("t", "kapandi");
			SinifNet.gonder(o);
		}
	}
}
