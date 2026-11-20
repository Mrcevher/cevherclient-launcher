package net.cevherclient.client.vaka;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/**
 * Hile vakaları ekranı: solda bekleyen vakalar, sağda seçili vakanın tüm detayları. "Hileydi" onay kutusu açar;
 * onaylanırsa sunucu oyuncuyu kalıcı yasaklar ve vakayı arşive taşır. Görünüm paneli: özel arka plan (fotoğraf,
 * GIF, video), karartma, panel opaklığı ve yazı boyutu.
 */
public final class VakaEkrani extends Screen {
	private static final DateTimeFormatter SAAT = DateTimeFormatter.ofPattern("dd.MM HH:mm:ss").withZone(ZoneId.systemDefault());
	private static final int M = 8;
	private static final int UST = 30;
	private static final int ALT_BOSLUK = 34;

	private static final int BEYAZ = 0xFFF2F2F5;
	private static final int GRI = 0xFF9AA0A8;
	private static final int SOLUK = 0xFF6B717A;
	private static final int VURGU = 0xFFE5484D;
	private static final int MAVI = 0xFF4FC3F7;
	private static final int TURUNCU = 0xFFFFB020;
	private static final int YESIL = 0xFF4ADE80;
	private static final int CERCEVE = 0x30FFFFFF;

	private enum Modal { YOK, ONAY, GORUNUM }

	@FunctionalInterface
	private interface Cizici {
		void ciz(GuiGraphics g, int x, int y, int w);
	}

	private record Oge(int h, Cizici c) {
	}

	private record Dugme(int x, int y, int w, int h, String metin, int renk, boolean aktif, Runnable is) {
		boolean icinde(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private final Screen onceki;
	private final Gorunum gor = Gorunum.al();
	private List<JsonObject> vakalar = List.of();
	private int secili;
	private float listeKaydir;
	private float detayKaydir;
	private int gorulenSurum = -1;
	private List<Oge> detay = List.of();
	private String detayAnahtar = "";
	private int detayYukseklik;
	private final List<Dugme> dugmeler = new ArrayList<>();
	private int modalIlk = Integer.MAX_VALUE;
	private Modal modal = Modal.YOK;
	private JsonObject onayVaka;
	private String tost = "";
	private int tostRenk = BEYAZ;
	private long tostZaman;
	private volatile boolean dosyaSeciliyor;

	public VakaEkrani(Screen onceki) {
		super(Component.literal("Hile vakaları"));
		this.onceki = onceki;
	}

	@Override
	protected void init() {
		yenile(true);
	}

	// ── veri ───────────────────────────────────────────────────────────────────

	private void yenile(boolean ilk) {
		String id = str(sec(), "id");
		vakalar = VakaMerkezi.liste();
		gorulenSurum = VakaMerkezi.surum;
		int yeni = ilk ? 0 : Math.min(secili, Math.max(0, vakalar.size() - 1));
		for (int i = 0; i < vakalar.size(); i++) {
			if (str(vakalar.get(i), "id").equals(id)) {
				yeni = i;
				break;
			}
		}
		if (!str(sec(), "id").equals(id) || ilk) {
			detayKaydir = 0;
		}
		secili = vakalar.isEmpty() ? -1 : yeni;
		detayAnahtar = "";
		if (onayVaka != null && vakalar.stream().noneMatch(v -> str(v, "id").equals(str(onayVaka, "id")))) {
			modal = Modal.YOK; // vaka bu arada başka bir yetkili tarafından karara bağlandı
			onayVaka = null;
		}
	}

	private JsonObject sec() {
		return secili >= 0 && secili < vakalar.size() ? vakalar.get(secili) : null;
	}

	private static String str(JsonObject o, String k) {
		return o == null ? "" : VakaMerkezi.str(o, k);
	}

	private static String saat(JsonObject o, String k) {
		return o.has(k) ? SAAT.format(Instant.ofEpochMilli(o.get(k).getAsLong())) : "?";
	}

	private static String sure(JsonObject o) {
		long ms = o.has("bas") && o.has("bit") ? Math.max(0, o.get("bit").getAsLong() - o.get("bas").getAsLong()) : 0;
		long s = ms / 1000;
		return String.format("%02d:%02d", s / 60, s % 60);
	}

	private void komut(String k, boolean kapat) {
		if (minecraft != null && minecraft.player != null) {
			minecraft.player.connection.sendCommand(k);
			if (kapat) {
				onClose();
			}
		}
	}

	private void tost(String s, int renk) {
		tost = s;
		tostRenk = renk;
		tostZaman = Util.getMillis();
	}

	// ── yerleşim ───────────────────────────────────────────────────────────────

	private float s() {
		return gor.yazi;
	}

	private int satirH() {
		return (int) Math.ceil(font.lineHeight * s()) + 2;
	}

	private int listeW() {
		return Math.max(150, Math.min(260, (int) (width * 0.27f * Math.min(1.3f, s()))));
	}

	private int altY() {
		return height - ALT_BOSLUK;
	}

	private int detayX() {
		return M + listeW() + 6;
	}

	private int kartH() {
		return (int) (font.lineHeight * s() * 2 + 12);
	}

	private int panelRenk() {
		return ((int) (gor.panel * 255) << 24) | 0x0D0E14;
	}

	// ── çizim yardımcıları ─────────────────────────────────────────────────────

	private static void kutu(GuiGraphics g, int x1, int y1, int x2, int y2, int c) {
		if (x2 - x1 < 3 || y2 - y1 < 3) {
			g.fill(x1, y1, x2, y2, c);
			return;
		}
		g.fill(x1 + 1, y1, x2 - 1, y2, c);
		g.fill(x1, y1 + 1, x1 + 1, y2 - 1, c);
		g.fill(x2 - 1, y1 + 1, x2, y2 - 1, c);
	}

	private static void cerceve(GuiGraphics g, int x1, int y1, int x2, int y2, int c) {
		g.fill(x1 + 1, y1, x2 - 1, y1 + 1, c);
		g.fill(x1 + 1, y2 - 1, x2 - 1, y2, c);
		g.fill(x1, y1 + 1, x1 + 1, y2 - 1, c);
		g.fill(x2 - 1, y1 + 1, x2, y2 - 1, c);
	}

	private static int alfa(int renk, int a) {
		return (a << 24) | (renk & 0xFFFFFF);
	}

	private int gen(String t, float sc) {
		return (int) Math.ceil(font.width(t) * sc);
	}

	private void yaz(GuiGraphics g, String t, float x, float y, int renk, float sc, boolean golge) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(sc, sc);
		g.drawString(font, t, 0, 0, renk, golge);
		g.pose().popMatrix();
	}

	private void yaz(GuiGraphics g, Component t, float x, float y, int renk, float sc, boolean golge) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(sc, sc);
		g.drawString(font, t, 0, 0, renk, golge);
		g.pose().popMatrix();
	}

	private void yaz(GuiGraphics g, FormattedCharSequence t, float x, float y, int renk, float sc) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(sc, sc);
		g.drawString(font, t, 0, 0, renk, true);
		g.pose().popMatrix();
	}

	/** Renkli hap etiket; genişliğini döndürür. */
	private int hap(GuiGraphics g, String t, int x, int y, int renk, float sc) {
		int w = gen(t, sc) + 8;
		int h = (int) Math.ceil(font.lineHeight * sc) + 3;
		kutu(g, x, y, x + w, y + h, alfa(renk, 0x38));
		cerceve(g, x, y, x + w, y + h, alfa(renk, 0x90));
		yaz(g, t, x + 4, y + 2, renk, sc, false);
		return w;
	}

	private String sigdir(String t, int w, float sc) {
		return VakaMerkezi.sigdir(font, t, (int) (w / sc));
	}

	private void dugme(int x, int y, int w, int h, String metin, int renk, boolean aktif, Runnable is) {
		dugmeler.add(new Dugme(x, y, w, h, metin, renk, aktif, is));
	}

	private void dugmeleriCiz(GuiGraphics g, int mx, int my, int ilk) {
		for (int i = ilk; i < dugmeler.size(); i++) {
			Dugme d = dugmeler.get(i);
			boolean ustte = d.aktif() && d.icinde(mx, my);
			int r = d.aktif() ? d.renk() : SOLUK;
			kutu(g, d.x(), d.y(), d.x() + d.w(), d.y() + d.h(), alfa(r, ustte ? 0x70 : 0x30));
			cerceve(g, d.x(), d.y(), d.x() + d.w(), d.y() + d.h(), alfa(r, ustte ? 0xFF : 0xA0));
			float sc = Math.min(s(), 1.25f);
			String t = sigdir(d.metin(), d.w() - 6, sc);
			yaz(g, t, d.x() + (d.w() - gen(t, sc)) / 2f, d.y() + (d.h() - font.lineHeight * sc) / 2f + 1, d.aktif() ? BEYAZ : SOLUK, sc, true);
		}
	}

	// ── çizim ──────────────────────────────────────────────────────────────────

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		ArkaPlan.hazirla(gor.arkaplan);
		if (ArkaPlan.var()) {
			ArkaPlan.ciz(g, width, height);
			g.fill(0, 0, width, height, (int) (gor.karartma * 255) << 24);
		} else {
			super.renderBackground(g, mouseX, mouseY, partialTick);
			g.fill(0, 0, width, height, (int) (gor.karartma * 0.6f * 255) << 24);
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		if (VakaMerkezi.surum != gorulenSurum) {
			yenile(false);
		}
		super.render(g, mouseX, mouseY, partialTick);
		dugmeler.clear();
		boolean modalVar = modal != Modal.YOK;
		int mx = modalVar ? -1 : mouseX, my = modalVar ? -1 : mouseY;

		ustBar(g);
		liste(g, mx, my);
		detayCiz(g);
		altBar(g);
		dugmeleriCiz(g, mx, my, 0);

		modalIlk = Integer.MAX_VALUE;
		if (modalVar) {
			int ilk = dugmeler.size();
			modalIlk = ilk;
			g.fill(0, 0, width, height, 0xB0000000);
			if (modal == Modal.ONAY) {
				onayCiz(g);
			} else {
				gorunumCiz(g);
			}
			dugmeleriCiz(g, mouseX, mouseY, ilk);
		}
		tostCiz(g);
	}

	private void ustBar(GuiGraphics g) {
		g.fillGradient(0, 0, width, 24, alfa(0x18070A, (int) (gor.panel * 230)), alfa(0x0D0E14, (int) (gor.panel * 140)));
		g.fill(0, 24, width, 25, alfa(VURGU, 0xB0));
		float sc = Math.min(1.4f, s() * 1.15f);
		Component baslik = Component.literal("⚠ HİLE VAKALARI").withStyle(ChatFormatting.BOLD);
		yaz(g, baslik, M + 2, (24 - font.lineHeight * sc) / 2f + 1, BEYAZ, sc, true);
		int x = M + 2 + (int) Math.ceil(font.width(baslik) * sc) + 8;
		int bekleyen = vakalar.size();
		hap(g, bekleyen == 0 ? "bekleyen yok" : bekleyen + " bekleyen", x, 7, bekleyen == 0 ? YESIL : TURUNCU, 0.85f);
		int sag = width - M;
		dugme(sag - 22, 4, 22, 16, "A+", MAVI, gor.yazi < 2.0f, () -> yaziDegistir(0.125f));
		dugme(sag - 47, 4, 22, 16, "A−", MAVI, gor.yazi > 0.75f, () -> yaziDegistir(-0.125f));
		dugme(sag - 47 - 74, 4, 70, 16, "Görünüm", MAVI, true, () -> modal = Modal.GORUNUM);
	}

	private void yaziDegistir(float d) {
		gor.yazi = Math.round((gor.yazi + d) * 8) / 8f;
		gor.kaydet();
		detayAnahtar = "";
		tost("Yazı boyutu %" + Math.round(gor.yazi * 100), MAVI);
	}

	private void liste(GuiGraphics g, int mx, int my) {
		int x1 = M, x2 = M + listeW(), y1 = UST, y2 = altY();
		kutu(g, x1, y1, x2, y2, panelRenk());
		cerceve(g, x1, y1, x2, y2, CERCEVE);
		float sc = s();
		if (vakalar.isEmpty()) {
			yaz(g, "Bekleyen vaka yok.", x1 + 8, y1 + 10, GRI, sc, true);
			List<FormattedCharSequence> l = font.split(Component.literal("Karara bağlananlar sunucuda plugins/CevherKoruma/arsiv/ klasöründe."),
				(int) ((x2 - x1 - 16) / sc));
			for (int i = 0; i < l.size(); i++) {
				yaz(g, l.get(i), x1 + 8, y1 + 12 + (i + 1) * satirH(), SOLUK, sc);
			}
			return;
		}
		int kh = kartH();
		int adim = kh + 3;
		float max = Math.max(0, vakalar.size() * adim + 6 - (y2 - y1));
		listeKaydir = Math.max(0, Math.min(max, listeKaydir));
		g.enableScissor(x1 + 1, y1 + 1, x2 - 1, y2 - 1);
		for (int i = 0; i < vakalar.size(); i++) {
			int y = y1 + 4 + i * adim - (int) listeKaydir;
			if (y + kh < y1 || y > y2) {
				continue;
			}
			JsonObject v = vakalar.get(i);
			String durum = str(v, "durum");
			int renk = VakaMerkezi.renk(durum);
			boolean ustte = mx >= x1 && mx < x2 && my >= Math.max(y, y1) && my < Math.min(y + kh, y2);
			int kx1 = x1 + 4, kx2 = x2 - 4;
			kutu(g, kx1, y, kx2, y + kh, i == secili ? alfa(renk, 0x40) : ustte ? 0x28FFFFFF : 0x14FFFFFF);
			if (i == secili) {
				cerceve(g, kx1, y, kx2, y + kh, alfa(renk, 0xC0));
			}
			g.fill(kx1, y + 1, kx1 + 3, y + kh - 1, renk);
			String rozet = durum + (v.has("guven") && v.get("guven").getAsInt() >= 0 ? " %" + v.get("guven").getAsInt() : "");
			float rs = sc * 0.8f;
			int rw = gen(rozet, rs) + 8;
			hap(g, rozet, kx2 - 4 - rw, y + 4, renk, rs);
			String ad = sigdir(str(v, "ad"), kx2 - kx1 - rw - 26, sc);
			yaz(g, Component.literal(ad).withStyle(ChatFormatting.BOLD), kx1 + 8, y + 5, BEYAZ, sc, true);
			String alt = saat(v, "bas") + " · " + sure(v) + (v.has("test") && v.get("test").getAsBoolean() ? " · TEST" : "");
			yaz(g, sigdir(alt, kx2 - kx1 - 14, sc * 0.9f), kx1 + 8, y + 7 + font.lineHeight * sc, GRI, sc * 0.9f, false);
		}
		g.disableScissor();
	}

	private void detayCiz(GuiGraphics g) {
		int x1 = detayX(), x2 = width - M, y1 = UST, y2 = altY();
		kutu(g, x1, y1, x2, y2, panelRenk());
		cerceve(g, x1, y1, x2, y2, CERCEVE);
		JsonObject v = sec();
		if (v == null) {
			yaz(g, "Soldan bir vaka seç.", x1 + 12, y1 + 12, GRI, s(), true);
			return;
		}
		int w = x2 - x1 - 24;
		String anahtar = str(v, "id") + "|" + w + "|" + gor.yazi + "|" + v.hashCode();
		if (!anahtar.equals(detayAnahtar)) {
			detay = detayKur(v, w);
			detayAnahtar = anahtar;
			detayYukseklik = 0;
			for (Oge o : detay) {
				detayYukseklik += o.h();
			}
		}
		float max = Math.max(0, detayYukseklik + 20 - (y2 - y1));
		detayKaydir = Math.max(0, Math.min(max, detayKaydir));
		g.enableScissor(x1 + 1, y1 + 1, x2 - 1, y2 - 1);
		int y = y1 + 10 - (int) detayKaydir;
		for (Oge o : detay) {
			if (y + o.h() >= y1 && y <= y2) {
				o.c().ciz(g, x1 + 12, y, w);
			}
			y += o.h();
		}
		g.disableScissor();
		if (max > 0) {
			int iz = y2 - y1 - 4;
			int bh = Math.max(16, (int) (iz * (y2 - y1) / (float) (detayYukseklik + 20)));
			int by = y1 + 2 + (int) ((iz - bh) * (detayKaydir / max));
			g.fill(x2 - 4, by, x2 - 2, by + bh, 0x60FFFFFF);
		}
	}

	private void altBar(GuiGraphics g) {
		JsonObject v = sec();
		boolean var = v != null;
		int y = height - 28, h = 22;
		int x = detayX();
		int sag = width - M;
		int bosluk = 4;
		int[] gw = {104, 112, 112, 130};
		boolean genis = sag - x >= gw[0] + gw[1] + gw[2] + gw[3] + 3 * bosluk + 16;
		if (!genis) {
			int esit = Math.max(40, (sag - x - 3 * bosluk) / 4);
			gw = new int[] {esit, esit, esit, esit};
		}
		boolean kisa = gw[2] < 108;
		dugme(x, y, gw[0], h, "✔ Hileydi", VURGU, var, () -> {
			onayVaka = sec();
			modal = Modal.ONAY;
		});
		dugme(x + gw[0] + bosluk, y, gw[1], h, kisa ? "✖ Yanlış" : "✖ Yanlış alarm", YESIL, var, () -> {
			JsonObject o = sec();
			if (o != null) {
				komut("hilevaka geri " + str(o, "id") + " temiz", false);
				tost(str(o, "ad") + ": yanlış alarm olarak arşivlendi", YESIL);
			}
		});
		dugme(sag - gw[3], y, gw[3], h, kisa ? "Yeniden incelet" : "LLM'e yeniden incelet", MAVI, var, () -> {
			JsonObject o = sec();
			if (o != null) {
				komut("hilevaka incele " + str(o, "id"), false);
				tost("Vaka yeniden yapay zekâya gönderildi", MAVI);
			}
		});
		dugme(sag - gw[3] - bosluk - gw[2], y, gw[2], h, kisa ? "Işınlan" : "Oyuncuya ışınlan", MAVI, var, () -> {
			JsonObject o = sec();
			if (o != null) {
				komut("tp " + str(o, "ad"), true);
			}
		});
		yaz(g, sigdir("Tekerlek: kaydır · ↑↓: seç · ESC: kapat", listeW() - 4, 0.85f), M + 2, height - 20, SOLUK, 0.85f, false);
	}

	// ── detay içeriği ──────────────────────────────────────────────────────────

	private List<Oge> detayKur(JsonObject v, int w) {
		List<Oge> l = new ArrayList<>();
		float sc = s();
		int lh = satirH();
		String durum = str(v, "durum");
		int renk = VakaMerkezi.renk(durum);
		int guven = v.has("guven") ? v.get("guven").getAsInt() : -1;

		// Başlık: büyük ad + durum hapı
		float bs = sc * 1.6f;
		int bh = (int) Math.ceil(font.lineHeight * bs) + 6;
		l.add(new Oge(bh, (g, x, y, ww) -> {
			Component ad = Component.literal(str(v, "ad")).withStyle(ChatFormatting.BOLD);
			yaz(g, ad, x, y, BEYAZ, bs, true);
			int ax = x + (int) Math.ceil(font.width(ad) * bs) + 10;
			int px = ax + hap(g, durum, ax, y + 2, renk, sc) + 4;
			if (v.has("test") && v.get("test").getAsBoolean()) {
				hap(g, "TEST VAKASI", px, y + 2, TURUNCU, sc);
			}
		}));
		metin(l, "Vaka " + str(v, "id") + " · puan " + str(v, "puan") + (v.has("kesin") && v.get("kesin").getAsBoolean() ? " · kesin eşik aşıldı" : ""),
			SOLUK, w, sc * 0.9f);
		bosluk(l, 4);

		// Yapay zekâ güven çubuğu
		if (guven >= 0) {
			int ch = lh + 10;
			l.add(new Oge(ch, (g, x, y, ww) -> {
				String t = "Yapay zekâ kararı: " + kararYazi(str(v, "karar")) + " — %" + guven + " emin";
				yaz(g, t, x, y, BEYAZ, sc, true);
				int by = y + lh + 1;
				int bw = Math.min(ww, 320);
				kutu(g, x, by, x + bw, by + 6, 0x30FFFFFF);
				kutu(g, x, by, x + Math.max(3, bw * guven / 100), by + 6, renk);
			}));
			bosluk(l, 4);
		}

		// Bilgi hapları
		List<String> cipler = new ArrayList<>();
		cipler.add("Başladı " + saat(v, "bas"));
		cipler.add("Süre " + sure(v));
		cipler.add("Ping " + str(v, "pingOrt") + " ms (" + str(v, "pingMin") + "–" + str(v, "pingMax") + ")");
		cipler.add("TPS " + str(v, "tps"));
		cipler.add("Mod " + str(v, "mod"));
		cipler.add("İstemci " + str(v, "istemci"));
		cipler.add(str(v, "dunya") + " " + str(v, "konum"));
		if (!str(v, "ip").isEmpty()) {
			cipler.add("IP " + str(v, "ip"));
		}
		float cs = sc * 0.9f;
		int chH = (int) Math.ceil(font.lineHeight * cs) + 6;
		List<List<String>> sira = new ArrayList<>();
		List<String> mevcut = new ArrayList<>();
		int kalan = w;
		for (String c : cipler) {
			int cw = gen(c, cs) + 12;
			if (cw > kalan && !mevcut.isEmpty()) {
				sira.add(mevcut);
				mevcut = new ArrayList<>();
				kalan = w;
			}
			mevcut.add(c);
			kalan -= cw;
		}
		if (!mevcut.isEmpty()) {
			sira.add(mevcut);
		}
		for (List<String> s : sira) {
			l.add(new Oge(chH, (g, x, y, ww) -> {
				int cx = x;
				for (String c : s) {
					cx += hap(g, c, cx, y, 0xFFB8C0CC, cs) + 4;
				}
			}));
		}

		// Yapay zekâ değerlendirmesi
		if (!str(v, "tur").isEmpty() || !str(v, "ozet").isEmpty() || !str(v, "gerekce").isEmpty() || !str(v, "llmHata").isEmpty()) {
			bolum(l, "YAPAY ZEKÂ DEĞERLENDİRMESİ", MAVI, w);
			if (!str(v, "tur").isEmpty()) {
				metin(l, "Hile türü: " + str(v, "tur"), 0xFFFF8A8A, w, sc);
			}
			if (!str(v, "ozet").isEmpty()) {
				metin(l, str(v, "ozet"), BEYAZ, w, sc);
			}
			if (!str(v, "gerekce").isEmpty()) {
				metin(l, str(v, "gerekce"), GRI, w, sc);
			}
			if (!str(v, "llmHata").isEmpty()) {
				metin(l, "Yapay zekâ değerlendiremedi: " + str(v, "llmHata") + " — lütfen kendin incele.", TURUNCU, w, sc);
			}
			if (v.has("llmMs") && v.get("llmMs").getAsLong() > 0) {
				metin(l, "Karar süresi: " + (v.get("llmMs").getAsLong() / 1000) + " sn", SOLUK, w, sc * 0.85f);
			}
		}

		// Ne yaptı
		if (v.has("kontroller") && !v.getAsJsonArray("kontroller").isEmpty()) {
			bolum(l, "NE YAPTI", TURUNCU, w);
			int enCok = 1;
			for (JsonElement x : v.getAsJsonArray("kontroller")) {
				enCok = Math.max(enCok, intDeger(x.getAsJsonObject(), "sayi"));
			}
			final int tepe = enCok;
			for (JsonElement x : v.getAsJsonArray("kontroller")) {
				JsonObject c = x.getAsJsonObject();
				int sayi = intDeger(c, "sayi");
				l.add(new Oge(lh + 5, (g, gx, y, ww) -> {
					String et = str(c, "etiket");
					yaz(g, Component.literal(et).withStyle(ChatFormatting.BOLD), gx, y, BEYAZ, sc, true);
					String n = "×" + sayi;
					int nx = gx + gen(et, sc) + 10;
					yaz(g, n, nx, y, TURUNCU, sc, true);
					int bx = nx + gen(n, sc) + 8;
					int bw = Math.max(20, Math.min(160, ww - (bx - gx)));
					kutu(g, bx, y + 2, bx + bw, y + 2 + Math.max(4, lh - 6), 0x24FFFFFF);
					kutu(g, bx, y + 2, bx + Math.max(3, bw * sayi / tepe), y + 2 + Math.max(4, lh - 6), alfa(TURUNCU, 0xC0));
				}));
				metin(l, "en yüksek: " + str(c, "enYuksek"), GRI, w - 10, sc * 0.9f, 10);
			}
		}

		// Zaman çizelgesi
		if (v.has("satirlar") && !v.getAsJsonArray("satirlar").isEmpty()) {
			bolum(l, "ZAMAN ÇİZELGESİ", 0xFFB794F6, w);
			var satirlar = v.getAsJsonArray("satirlar");
			for (int i = 0; i < satirlar.size(); i++) {
				String satir = satirlar.get(i).getAsString().trim();
				int bosl = satir.indexOf(' ');
				String zaman = bosl > 0 && bosl <= 9 ? satir.substring(0, bosl) : "";
				String geri = zaman.isEmpty() ? satir : satir.substring(bosl + 1);
				float ts = sc * 0.9f;
				int zw = gen("00:00:00", ts) + 14;
				List<FormattedCharSequence> parcalar = font.split(Component.literal(geri), (int) ((w - zw) / ts));
				int th = Math.max(1, parcalar.size()) * ((int) Math.ceil(font.lineHeight * ts) + 1) + 3;
				boolean son = i == satirlar.size() - 1;
				l.add(new Oge(th, (g, x, y, ww) -> {
					g.fill(x + 2, y + 2, x + 6, y + 6, 0xFFB794F6);
					if (!son) {
						g.fill(x + 3, y + 7, x + 5, y + th + 2, 0x50B794F6);
					}
					yaz(g, zaman, x + 10, y, 0xFFB794F6, ts, false);
					int yy = y;
					for (FormattedCharSequence p : parcalar) {
						yaz(g, p, x + zw, yy, 0xFFC8CCD4, ts);
						yy += (int) Math.ceil(font.lineHeight * ts) + 1;
					}
				}));
			}
		}
		bosluk(l, 10);
		return l;
	}

	private static int intDeger(JsonObject o, String k) {
		try {
			return Integer.parseInt(VakaMerkezi.str(o, k));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static String kararYazi(String k) {
		return switch (k) {
			case "HILE" -> "HİLE";
			case "TEMIZ" -> "TEMİZ";
			case "SUPHELI" -> "ŞÜPHELİ";
			default -> k.isEmpty() ? "?" : k;
		};
	}

	private void bosluk(List<Oge> l, int h) {
		l.add(new Oge(h, (g, x, y, w) -> { }));
	}

	private void bolum(List<Oge> l, String baslik, int renk, int w) {
		float sc = s() * 0.95f;
		int h = (int) Math.ceil(font.lineHeight * sc) + 14;
		l.add(new Oge(h, (g, x, y, ww) -> {
			Component c = Component.literal(baslik).withStyle(ChatFormatting.BOLD);
			yaz(g, c, x, y + 8, renk, sc, true);
			int cx = x + (int) Math.ceil(font.width(c) * sc) + 8;
			int cy = y + 8 + (int) (font.lineHeight * sc / 2);
			g.fill(cx, cy, x + ww, cy + 1, alfa(renk, 0x50));
		}));
	}

	private void metin(List<Oge> l, String t, int renk, int w, float sc) {
		metin(l, t, renk, w, sc, 0);
	}

	private void metin(List<Oge> l, String t, int renk, int w, float sc, int girinti) {
		int lh = (int) Math.ceil(font.lineHeight * sc) + 2;
		for (FormattedCharSequence p : font.split(Component.literal(t), (int) ((w - girinti) / sc))) {
			l.add(new Oge(lh, (g, x, y, ww) -> yaz(g, p, x + girinti, y, renk, sc)));
		}
	}

	// ── onay kutusu ────────────────────────────────────────────────────────────

	private void onayCiz(GuiGraphics g) {
		JsonObject v = onayVaka;
		if (v == null) {
			modal = Modal.YOK;
			return;
		}
		float sc = Math.min(s(), 1.4f);
		boolean test = v.has("test") && v.get("test").getAsBoolean();
		String ad = str(v, "ad");
		String ip = str(v, "ip").isEmpty() ? "bilinmiyor" : str(v, "ip");
		List<String[]> satirlar = new ArrayList<>();
		satirlar.add(new String[] {ad + " sunucudan KALICI olarak yasaklanacak:", "B"});
		satirlar.add(new String[] {"•  Hesabı: " + ad, "G"});
		satirlar.add(new String[] {"•  IP adresi: " + ip, "G"});
		satirlar.add(new String[] {"•  Bilgisayarı: cihaz izi + bilgisayarına yazılan yasak damgası", "G"});
		satirlar.add(new String[] {"•  Aynı bilgisayardan veya IP'den girecek başka hesaplar da otomatik yasaklanır", "G"});
		satirlar.add(new String[] {"", "S"});
		satirlar.add(new String[] {"Vaka arşive yazılır ve listeden kaldırılır. Geri almak için: /cevherauth yasakkaldir " + ad, "S"});
		if (test) {
			satirlar.add(new String[] {"Bu bir TEST vakası: yasak uygulanmaz, yalnızca arşivlenir.", "T"});
		}
		int bw = Math.min(width - 40, (int) (380 * sc));
		List<FormattedCharSequence> cizgiler = new ArrayList<>();
		List<Integer> renkler = new ArrayList<>();
		for (String[] s : satirlar) {
			int r = switch (s[1]) {
				case "B" -> BEYAZ;
				case "G" -> 0xFFD0D4DC;
				case "T" -> TURUNCU;
				default -> GRI;
			};
			if (s[0].isEmpty()) {
				cizgiler.add(FormattedCharSequence.EMPTY);
				renkler.add(r);
				continue;
			}
			for (FormattedCharSequence p : font.split(Component.literal(s[0]), (int) ((bw - 28) / sc))) {
				cizgiler.add(p);
				renkler.add(r);
			}
		}
		int lh = (int) Math.ceil(font.lineHeight * sc) + 2;
		int bh = 34 + cizgiler.size() * lh + 44;
		int x1 = (width - bw) / 2, y1 = (height - bh) / 2;
		kutu(g, x1, y1, x1 + bw, y1 + bh, 0xF0121218);
		cerceve(g, x1, y1, x1 + bw, y1 + bh, alfa(VURGU, 0xD0));
		g.fill(x1 + 1, y1 + 1, x1 + bw - 1, y1 + 26, alfa(VURGU, 0x40));
		yaz(g, Component.literal("⚠ KALICI YASAK").withStyle(ChatFormatting.BOLD), x1 + 12, y1 + (26 - font.lineHeight * sc) / 2f + 1, 0xFFFF7A7E, sc, true);
		int y = y1 + 34;
		for (int i = 0; i < cizgiler.size(); i++) {
			yaz(g, cizgiler.get(i), x1 + 14, y, renkler.get(i), sc);
			y += lh;
		}
		int dy = y1 + bh - 32;
		int dw = (bw - 36) / 2;
		dugme(x1 + 12, dy, dw, 22, test ? "Onayla — Arşivle" : "Onayla — Yasakla", VURGU, true, () -> {
			komut("hilevaka yasakla " + str(v, "id"), false);
			tost(test ? ad + ": test vakası arşivlendi" : ad + " için kalıcı yasak gönderildi", VURGU);
			modal = Modal.YOK;
			onayVaka = null;
		});
		dugme(x1 + bw - 12 - dw, dy, dw, 22, "İptal", GRI, true, () -> {
			modal = Modal.YOK;
			onayVaka = null;
		});
	}

	// ── görünüm paneli ─────────────────────────────────────────────────────────

	private void gorunumCiz(GuiGraphics g) {
		float sc = Math.min(s(), 1.3f);
		int lh = (int) Math.ceil(font.lineHeight * sc) + 2;
		int bw = Math.min(width - 40, (int) (360 * sc));
		int bh = 34 + lh * 3 + 30 + 3 * (lh + 12) + 50;
		int x1 = (width - bw) / 2, y1 = (height - bh) / 2;
		kutu(g, x1, y1, x1 + bw, y1 + bh, 0xF0121218);
		cerceve(g, x1, y1, x1 + bw, y1 + bh, alfa(MAVI, 0xC0));
		g.fill(x1 + 1, y1 + 1, x1 + bw - 1, y1 + 26, alfa(MAVI, 0x30));
		yaz(g, Component.literal("GÖRÜNÜM").withStyle(ChatFormatting.BOLD), x1 + 12, y1 + (26 - font.lineHeight * sc) / 2f + 1, MAVI, sc, true);
		int y = y1 + 34;
		String dosya = gor.arkaplan.isEmpty() ? "Yok (oyunun bulanık görüntüsü)" : Path.of(gor.arkaplan).getFileName().toString();
		yaz(g, "Arka plan: " + sigdir(dosya, bw - 100, sc), x1 + 14, y, BEYAZ, sc, true);
		y += lh;
		String hata = ArkaPlan.hata();
		yaz(g, dosyaSeciliyor ? "Dosya seçme penceresi açık…" : !hata.isEmpty() ? sigdir(hata, bw - 28, sc * 0.9f)
			: "PNG, JPG, BMP, GIF (hareketli) veya MP4/MOV video (H.264, sessiz)", x1 + 14, y, !hata.isEmpty() ? TURUNCU : SOLUK, sc * 0.9f, false);
		y += lh + 4;
		int dw = (bw - 36) / 2;
		dugme(x1 + 12, y, dw, 20, "Fotoğraf / video seç…", MAVI, !dosyaSeciliyor, this::dosyaSec);
		dugme(x1 + bw - 12 - dw, y, dw, 20, "Arka planı kaldır", GRI, !gor.arkaplan.isEmpty(), () -> {
			gor.arkaplan = "";
			gor.kaydet();
			medyaTemizle(null);
			tost("Arka plan kaldırıldı", GRI);
		});
		y += 30;
		y = ayar(g, x1, y, bw, sc, "Arka plan karartma", "%" + Math.round(gor.karartma * 100), gor.karartma > 0f, gor.karartma < 0.9f,
			() -> gor.karartma = Math.max(0f, gor.karartma - 0.05f), () -> gor.karartma = Math.min(0.9f, gor.karartma + 0.05f));
		y = ayar(g, x1, y, bw, sc, "Panel opaklığı", "%" + Math.round(gor.panel * 100), gor.panel > 0.25f, gor.panel < 0.97f,
			() -> gor.panel = Math.max(0.25f, gor.panel - 0.05f), () -> gor.panel = Math.min(0.97f, gor.panel + 0.05f));
		y = ayar(g, x1, y, bw, sc, "Yazı boyutu", "%" + Math.round(gor.yazi * 100), gor.yazi > 0.75f, gor.yazi < 2.0f,
			() -> yaziDegistir(-0.125f), () -> yaziDegistir(0.125f));
		int dy = y1 + bh - 32;
		dugme(x1 + 12, dy, dw, 22, "Varsayılana dön", GRI, true, () -> {
			gor.karartma = 0.45f;
			gor.panel = 0.78f;
			gor.yazi = 1.0f;
			gor.kaydet();
			detayAnahtar = "";
		});
		dugme(x1 + bw - 12 - dw, dy, dw, 22, "Kapat", MAVI, true, () -> {
			gor.kaydet();
			modal = Modal.YOK;
		});
	}

	private int ayar(GuiGraphics g, int x1, int y, int bw, float sc, String ad, String deger, boolean azalt, boolean arttir, Runnable eksi, Runnable arti) {
		yaz(g, ad, x1 + 14, y + 5, BEYAZ, sc, true);
		int sag = x1 + bw - 14;
		dugme(sag - 22, y, 22, 18, "+", MAVI, arttir, () -> {
			arti.run();
			gor.kaydet();
		});
		String d = deger;
		int dw = Math.max(gen("%100", sc), gen(d, sc)) + 10;
		yaz(g, d, sag - 26 - dw + (dw - gen(d, sc)) / 2f, y + 5, MAVI, sc, true);
		dugme(sag - 26 - dw - 26, y, 22, 18, "−", MAVI, azalt, () -> {
			eksi.run();
			gor.kaydet();
		});
		return y + (int) Math.ceil(font.lineHeight * sc) + 14;
	}

	private void dosyaSec() {
		if (dosyaSeciliyor) {
			return;
		}
		dosyaSeciliyor = true;
		Thread t = new Thread(() -> {
			String secilen = null;
			String hata = null;
			try (MemoryStack st = MemoryStack.stackPush()) {
				String[] desen = {"*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif", "*.mp4", "*.m4v", "*.mov"};
				PointerBuffer f = st.mallocPointer(desen.length);
				for (String d : desen) {
					f.put(st.UTF8(d));
				}
				f.flip();
				secilen = TinyFileDialogs.tinyfd_openFileDialog("Vaka menüsü arka planı", "", f, "Resim veya video", false);
			} catch (Throwable e) {
				hata = "Dosya penceresi açılamadı: " + e.getClass().getSimpleName();
			}
			String kopya = null;
			if (secilen != null) {
				try {
					Path kaynak = Path.of(secilen);
					long boyut = Files.size(kaynak);
					if (boyut > 600L * 1024 * 1024) {
						hata = "Dosya çok büyük (en fazla 600 MB)";
					} else {
						String ad = kaynak.getFileName().toString();
						String uzanti = ad.contains(".") ? ad.substring(ad.lastIndexOf('.')).toLowerCase(Locale.ROOT) : "";
						Path hedef = Gorunum.medyaKlasoru().resolve("arkaplan-" + System.currentTimeMillis() + uzanti);
						Files.createDirectories(hedef.getParent());
						Files.copy(kaynak, hedef, StandardCopyOption.REPLACE_EXISTING);
						kopya = hedef.toAbsolutePath().toString();
					}
				} catch (Exception e) {
					hata = "Dosya kopyalanamadı: " + e.getMessage();
				}
			}
			String k = kopya, h = hata;
			Minecraft.getInstance().execute(() -> {
				dosyaSeciliyor = false;
				if (h != null) {
					tost(h, TURUNCU);
				} else if (k != null) {
					gor.arkaplan = k;
					gor.kaydet();
					medyaTemizle(Path.of(k));
					tost("Arka plan ayarlandı" + (ArkaPlan.video(k) ? " (video, sessiz döngü)" : ""), MAVI);
				}
			});
		}, "Cevherclient arka plan seçimi");
		t.setDaemon(true);
		t.start();
	}

	/** vaka-arkaplan klasöründeki eski kopyaları siler (yer kaplamasın). */
	private static void medyaTemizle(Path tut) {
		try (Stream<Path> s = Files.list(Gorunum.medyaKlasoru())) {
			s.filter(p -> tut == null || !p.toAbsolutePath().equals(tut.toAbsolutePath())).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (Exception ignored) {
					// kullanımda olabilir
				}
			});
		} catch (Exception ignored) {
			// klasör yok
		}
	}

	private void tostCiz(GuiGraphics g) {
		long gecen = Util.getMillis() - tostZaman;
		if (tost.isEmpty() || gecen > 3500) {
			return;
		}
		float sc = Math.min(s(), 1.3f);
		int tw = gen(tost, sc) + 20;
		int th = (int) Math.ceil(font.lineHeight * sc) + 10;
		int x = (width - tw) / 2;
		int y = 32 + (int) (Math.max(0, 200 - gecen) / 200f * -12);
		kutu(g, x, y, x + tw, y + th, 0xF0121218);
		cerceve(g, x, y, x + tw, y + th, tostRenk);
		yaz(g, tost, x + 10, y + 5, tostRenk, sc, true);
	}

	// ── girdi ──────────────────────────────────────────────────────────────────

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double x = event.x(), y = event.y();
		if (event.button() == 0) {
			// Son karede çizilen düğmeler (modal açıksa yalnızca onunkiler etkin)
			for (int i = dugmeler.size() - 1; i >= 0; i--) {
				Dugme d = dugmeler.get(i);
				if (d.aktif() && d.icinde(x, y) && (modal == Modal.YOK || i >= modalIlk)) {
					d.is().run();
					return true;
				}
			}
		}
		if (modal != Modal.YOK) {
			return true;
		}
		int x1 = M, x2 = M + listeW();
		if (event.button() == 0 && x >= x1 && x < x2 && y >= UST && y < altY() && !vakalar.isEmpty()) {
			int adim = kartH() + 3;
			int i = (int) ((y - UST - 4 + listeKaydir) / adim);
			if (i >= 0 && i < vakalar.size()) {
				if (i != secili) {
					secili = i;
					detayKaydir = 0;
				}
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
		if (modal != Modal.YOK) {
			return true;
		}
		float adim = (float) (-vertical * 22 * s());
		if (x < M + listeW()) {
			listeKaydir += adim;
		} else {
			detayKaydir += adim;
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ESCAPE && modal != Modal.YOK) {
			modal = Modal.YOK;
			onayVaka = null;
			return true;
		}
		if (modal == Modal.YOK && !vakalar.isEmpty()) {
			if (event.key() == GLFW.GLFW_KEY_DOWN || event.key() == GLFW.GLFW_KEY_UP) {
				int yeni = Math.max(0, Math.min(vakalar.size() - 1, secili + (event.key() == GLFW.GLFW_KEY_DOWN ? 1 : -1)));
				if (yeni != secili) {
					secili = yeni;
					detayKaydir = 0;
					int adim = kartH() + 3;
					float ust = secili * adim, alt = ust + adim + 8 - (altY() - UST);
					listeKaydir = Math.max(alt, Math.min(ust, listeKaydir));
				}
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public void removed() {
		ArkaPlan.birak();
		super.removed();
	}

	@Override
	public void onClose() {
		if (minecraft != null) {
			minecraft.setScreen(onceki);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
