package net.cevherclient.client.duello;

import com.google.gson.JsonObject;
import net.cevherclient.client.sao.Sao;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

/**
 * SAO düello isteği penceresi: başlık yatayda, gövde dikeyde açılır; üç kazanma koşulundan biri seçilince
 * sunucuya {@code kabul} gider. ESC pencereyi kapatır (istek süresi dolana kadar komutla da kabul edilebilir).
 * İçerik "tasarım biriminde" (ekranın ortası 0,0) çizilir ve ekrana sığacak şekilde ölçeklenir.
 */
public final class DuelloEkrani extends Screen {
	private static final float WX = -150;
	private static final float WY = -86;
	private static final float WW = 300;
	private static final float WH = 172;
	private static final float HEADER = 22;
	private static final float SATIR_Y = 60;
	private static final float SATIR_H = 22;
	private static final float SATIR_ARA = 26;

	private record Secenek(String mod, String ad, String aciklama) {
	}

	private static final Secenek[] SECENEKLER = {
		new Secenek("ilk", "First-Strike  ·  İlk Vuruş", "İlk temiz vuruşu yapan kazanır. Ölüm riski yok."),
		new Secenek("yari", "Half-Loss  ·  Yarı Can", "Rakibin canını %50'ye düşüren kazanır."),
		new Secenek("tam", "Total-Loss  ·  Tam Kayıp", "Canı 0'a inen kaybeder. Gerçek ölüm ve eşya kaybı yok."),
	};

	private final String ad;
	private final long sureMs;
	private final long acilis = Util.getMillis();
	private float scale = 1f;
	private boolean gonderildi;

	DuelloEkrani(String ad, int saniye) {
		super(Component.literal("Düello"));
		this.ad = ad;
		this.sureMs = saniye * 1000L;
	}

	@Override
	protected void init() {
		DuelloNet.ses(1.2f);
	}

	private void olcekle() {
		scale = Math.max(0.5f, Math.min(2.2f, Math.min(width / (WW + 40f), height / (WH + 30f))));
	}

	private float tasarimX(double x) {
		return (float) ((x - width / 2.0) / scale);
	}

	private float tasarimY(double y) {
		return (float) ((y - height / 2.0) / scale);
	}

	private float satirY(int i) {
		return WY + SATIR_Y + i * SATIR_ARA;
	}

	private boolean reddetUstunde(float mx, float my) {
		return Sao.inside(mx, my, WX + WW - 54 - 12, WY + WH - 30, 24, 24);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		olcekle();
		long t = Util.getMillis() - acilis;
		float mx = tasarimX(mouseX);
		float my = tasarimY(mouseY);
		float eski = Sao.alpha;
		Sao.alpha = Sao.easeOut(Sao.progress(t, 0, 140));
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(width / 2f, height / 2f);
		pose.scale(scale, scale);
		try {
			Sao.window(g, WX, WY, WW, WH, HEADER, t);
			if (t > 140) {
				Sao.text(g, "Düello İsteği", WX + 12, WY + 7, 7.5f, Sao.TITLE);
			}
			if (t > 230) {
				govde(g, mx, my, t);
			}
		} finally {
			pose.popMatrix();
			Sao.alpha = eski;
		}
		if (Util.getMillis() - acilis > sureMs && !gonderildi) {
			onClose(); // istek süresi doldu
		}
	}

	private void govde(GuiGraphics g, float mx, float my, long t) {
		Sao.centered(g, Sao.fit(ad + " seni düelloya çağırıyor!", WW - 24, 7.5f), 0, WY + 32, 7.5f, Sao.TEXT);
		Sao.centered(g, "Kazanma koşulunu seç:", 0, WY + 46, 5.5f, Sao.MUTED);

		for (int i = 0; i < SECENEKLER.length; i++) {
			float p = Sao.easeOut(Sao.progress(t, 230 + i * 70L, 140));
			if (p <= 0) {
				continue;
			}
			float x = WX + 14;
			float y = satirY(i);
			float w = (WW - 28) * p;
			boolean ustunde = p >= 1f && Sao.inside(mx, my, x, y, WW - 28, SATIR_H);
			Sao.rect(g, x, y, w, SATIR_H, ustunde ? Sao.CELL_HOVER : Sao.CELL_BG);
			if (ustunde) {
				Sao.outline(g, x, y, w, SATIR_H, Sao.ACCENT);
			}
			if (p < 1f) {
				continue;
			}
			Sao.disk(g, x + 13, y + SATIR_H / 2f, 9, ustunde ? Sao.ACCENT : Sao.ICON_DIM);
			Sao.text(g, SECENEKLER[i].ad(), x + 28, y + 4, 6.5f, ustunde ? Sao.ICON : Sao.TEXT);
			Sao.text(g, Sao.fit(SECENEKLER[i].aciklama(), WW - 28 - 36, 4.8f), x + 28, y + 13.5f, 4.8f, Sao.MUTED);
		}

		// kalan süre çubuğu
		long kalan = Math.max(0, sureMs - (Util.getMillis() - acilis));
		float oran = sureMs <= 0 ? 0f : kalan / (float) sureMs;
		Sao.text(g, "Kalan: " + (kalan / 1000 + 1) + " sn", WX + 14, WY + WH - 28, 5f, Sao.MUTED);
		Sao.rect(g, WX + 14, WY + WH - 18, 170, 4, Sao.FIELD_BG);
		Sao.rect(g, WX + 14, WY + WH - 18, 170 * oran, 4, Sao.ACCENT);

		boolean red = reddetUstunde(mx, my);
		float rx = WX + WW - 54, ry = WY + WH - 18;
		Sao.disk(g, rx, ry, 23, red ? 0xFFFFFFFF : 0xFFE6E6E6);
		Sao.disk(g, rx, ry, 17, red ? 0xFFEF5A58 : 0xFFD9453A);
		var pz = g.pose();
		for (int sg = -1; sg <= 1; sg += 2) {
			pz.pushMatrix();
			pz.translate(rx, ry);
			pz.rotate((float) (sg * Math.PI / 4));
			Sao.rect(g, -1f, -6, 2, 12, 0xFFFFFFFF);
			pz.popMatrix();
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != 0 || gonderildi || Util.getMillis() - acilis < 600) {
			return true;
		}
		float mx = tasarimX(event.x());
		float my = tasarimY(event.y());
		for (int i = 0; i < SECENEKLER.length; i++) {
			if (Sao.inside(mx, my, WX + 14, satirY(i), WW - 28, SATIR_H)) {
				gonder("kabul", SECENEKLER[i].mod());
				return true;
			}
		}
		if (reddetUstunde(mx, my)) {
			gonder("reddet", null);
		}
		return true;
	}

	private void gonder(String tur, String mod) {
		gonderildi = true;
		JsonObject o = new JsonObject();
		o.addProperty("t", tur);
		o.addProperty("ad", ad);
		if (mod != null) {
			o.addProperty("mod", mod);
		}
		DuelloNet.gonder(o);
		DuelloNet.ses(tur.equals("kabul") ? 1.6f : 0.9f);
		onClose();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
