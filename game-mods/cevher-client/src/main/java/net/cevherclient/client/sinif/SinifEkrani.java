package net.cevherclient.client.sinif;

import java.awt.BasicStroke;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import net.cevherclient.client.sinif.SinifVeri.Parca;
import net.cevherclient.client.sinif.SinifVeri.Satir;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

/**
 * Sınıf seçimi ve stat ağacı ekranlarının ortak tabanı. İçerik 640×360'lık "tasarım biriminde" (ekranın ortası
 * 0,0) çizilir ve ekrana sığacak şekilde ölçeklenir; arka plan bütün ekranı kaplar. Onay kutusu, ipucu ve
 * alttaki bildirim satırı burada.
 */
public abstract class SinifEkrani extends Screen {
	protected static final float TW = 640;
	protected static final float TH = 360;
	private static final long BILDIRIM_MS = 3200;

	protected record Onay(Satir baslik, List<Satir> metin, Satir evet, Runnable eylem) {
	}

	protected final long acilis = Util.getMillis();
	protected float olcek = 1f;
	/** Sunucu ekranı kapattı ya da yerine başka bir sınıf ekranı açıldı: kapanış bildirimi gönderilmez. */
	protected boolean sunucuKapatti;
	protected Onay onay;

	/** Bu karede gösterilecek ipucu (alt sınıf {@link #icerik} içinde doldurur). */
	protected List<Satir> ipucu;
	private Satir bildirim = Satir.BOS;
	private long bildirimZamani;
	private int onayHover = -1;

	protected SinifEkrani(Component baslik) {
		super(baslik);
	}

	/** Arka plan parıltısının ekran yüksekliğine oranla dikey konumu. */
	protected float parlaklikMerkezi() {
		return 0.55f;
	}

	protected abstract void icerik(GuiGraphics g, float mx, float my, float alpha, boolean etkilesim);

	/** Tasarım biriminde tıklama; onay kutusu açıkken çağrılmaz. */
	protected abstract void tikla(float mx, float my, MouseButtonEvent olay);

	public void sunucuKapatti() {
		sunucuKapatti = true;
	}

	protected void bildir(Satir s) {
		if (s != null && !s.bos()) {
			bildirim = s;
			bildirimZamani = Util.getMillis();
		}
	}

	protected void ses(float perde) {
		if (minecraft != null) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, perde, 0.4f));
		}
	}

	// --- Sınırlar (tasarım biriminde ekranın görünen kenarları) ---

	protected float sol() {
		return -width / 2f / olcek;
	}

	protected float sag() {
		return width / 2f / olcek;
	}

	protected float ust() {
		return -height / 2f / olcek;
	}

	protected float alt() {
		return height / 2f / olcek;
	}

	private float tasarimX(double x) {
		return (float) ((x - width / 2.0) / olcek);
	}

	private float tasarimY(double y) {
		return (float) ((y - height / 2.0) / olcek);
	}

	// --- Çizim ---

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		float cy = height * parlaklikMerkezi();
		int w = width;
		int h = height;
		SinifDoku.ciz(g, "arka|" + getClass().getSimpleName(), 1, 0, 0, w, h, 1f,
			p -> SinifCizim.arkaPlan(p, w, h, w / 2f, cy, SinifCizim.PARILTI, 7L));
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		olcek = Math.max(0.5f, Math.min(4f, Math.min(width / TW, height / TH)));
		long t = Util.getMillis() - acilis;
		float alpha = 1f - (float) Math.pow(1f - Math.min(1f, t / 220f), 3);
		float mx = tasarimX(mouseX);
		float my = tasarimY(mouseY);

		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(width / 2f, height / 2f);
		pose.scale(olcek, olcek);
		ipucu = null;
		icerik(g, mx, my, alpha, onay == null);
		bildirimCiz(g, alpha);
		if (onay != null) {
			onayCiz(g, mx, my, alpha);
		} else if (ipucu != null) {
			SinifCizim.ipucu(g, ipucu, mx, my, sol(), sag(), ust(), alt(), alpha);
		}
		pose.popMatrix();
	}

	private void bildirimCiz(GuiGraphics g, float alpha) {
		if (bildirim.bos()) {
			return;
		}
		long e = Util.getMillis() - bildirimZamani;
		if (e > BILDIRIM_MS + 600) {
			bildirim = Satir.BOS;
			return;
		}
		float a = e < BILDIRIM_MS ? 1f : 1f - (e - BILDIRIM_MS) / 600f;
		SinifCizim.satir(g, bildirim, 0, Math.min(TH / 2f, alt()) - 20, 7.5f, false, 1, alpha * a);
	}

	// --- Onay kutusu ---

	private static final float OW = 236;
	private static final float OH = 96;
	private static final float BW = 92;
	private static final float BH = 18;

	private float dugmeX(int i) {
		return i == 0 ? -BW - 6 : 6;
	}

	private static final float DUGME_Y = OH / 2f - BH - 12;

	private void onayCiz(GuiGraphics g, float mx, float my, float alpha) {
		g.fill((int) Math.floor(sol()) - 1, (int) Math.floor(ust()) - 1, (int) Math.ceil(sag()) + 1, (int) Math.ceil(alt()) + 1,
			0xA0000000);
		SinifDoku.ciz(g, "onay|kutu", 1, -OW / 2f, -OH / 2f, OW, OH, alpha, p -> {
			p.setColor(SinifCizim.awt(0xF2141211));
			p.fill(new RoundRectangle2D.Float(0, 0, OW, OH, 6, 6));
			p.setColor(SinifCizim.awt(SinifCizim.CERCEVE));
			p.setStroke(new BasicStroke(0.9f));
			p.draw(new RoundRectangle2D.Float(0.45f, 0.45f, OW - 0.9f, OH - 0.9f, 6, 6));
			SinifCizim.ayrac(p, 10, 26, OW - 20, 0.8f);
		});
		SinifCizim.satir(g, onay.baslik(), 0, -OH / 2f + 8, 8.5f, false, 1, alpha);
		float y = -OH / 2f + 34;
		for (Satir s : onay.metin()) {
			SinifCizim.satir(g, s, 0, y, 6.6f, false, 1, alpha);
			y += SinifCizim.yukseklik(6.6f) + 2;
		}
		onayHover = -1;
		for (int i = 0; i < 2; i++) {
			float x = dugmeX(i);
			boolean ustunde = mx >= x && mx < x + BW && my >= DUGME_Y && my < DUGME_Y + BH;
			if (ustunde) {
				onayHover = i;
			}
			int dolgu = i == 0 ? (ustunde ? 0xFFF2B48A : 0xFFD9946A) : (ustunde ? 0xFF4A4644 : 0xFF2E2B2A);
			int cerceve = i == 0 ? 0xFFFFD9BC : 0xFF5A5654;
			SinifDoku.ciz(g, "onay|dugme|" + i + "|" + ustunde, 1, x, DUGME_Y, BW, BH, alpha, p -> {
				p.setColor(SinifCizim.awt(dolgu));
				p.fill(new RoundRectangle2D.Float(0, 0, BW, BH, 4, 4));
				p.setColor(SinifCizim.awt(cerceve, 0.8f));
				p.setStroke(new BasicStroke(0.7f));
				p.draw(new RoundRectangle2D.Float(0.35f, 0.35f, BW - 0.7f, BH - 0.7f, 4, 4));
			});
			Satir yazi = i == 0 ? onay.evet() : Satir.kalin("Vazgeç", 0xFFD8CFC8);
			if (i == 0) {
				yazi = new Satir(yazi.parcalar().stream().map(pp -> new Parca(pp.metin(), 0xFF2A1A10, true)).toList());
			}
			SinifCizim.satir(g, yazi, x + BW / 2f, DUGME_Y + (BH - SinifCizim.yukseklik(7f)) / 2f - 0.6f, 7f, false, 1, alpha);
		}
	}

	// --- Girdi ---

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		float mx = tasarimX(event.x());
		float my = tasarimY(event.y());
		if (onay != null) {
			if (event.button() == 0) {
				float y = DUGME_Y;
				if (my >= y && my < y + BH) {
					if (mx >= dugmeX(0) && mx < dugmeX(0) + BW) {
						Runnable eylem = onay.eylem();
						onay = null;
						ses(1.4f);
						eylem.run();
					} else if (mx >= dugmeX(1) && mx < dugmeX(1) + BW) {
						onay = null;
						ses(0.9f);
					}
				}
			}
			return true;
		}
		tikla(mx, my, event);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ESCAPE && onay != null) {
			onay = null;
			return true;
		}
		if ((event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) && onay != null) {
			Runnable eylem = onay.eylem();
			onay = null;
			eylem.run();
			return true;
		}
		if (onay == null && minecraft != null && minecraft.options.keyInventory.matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void removed() {
		SinifDoku.temizle();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
