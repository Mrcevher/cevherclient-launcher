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
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import net.cevherclient.client.risk.RiskHud;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
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

	private record Dugme(int x, int y, int w, int h, String metin, int renk, boolean aktif, Runnable is, boolean gizli) {
		boolean icinde(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private enum Sekme { HILE, RISK, YASAK }

	/** Onay kutusunun hangi iş için açıldığı. */
	private enum OnayTur { VAKA, RISK, KALDIR, RED }

	/** Sekme ve "görüldü" bilgisi ekran kapanıp açılınca da korunur (oturum boyunca). */
	private static Sekme sonSekme = Sekme.HILE;
	private static final Set<String> GORULEN_VAKA = new HashSet<>();
	private static final Set<String> GORULEN_RISK = new HashSet<>();
	private static final Set<String> GORULEN_YASAK = new HashSet<>();

	private final Screen onceki;
	private final Gorunum gor = Gorunum.al();
	private Sekme sekme = sonSekme;
	private List<JsonObject> vakalar = List.of();
	private int secili;
	private List<JsonObject> riskler = List.of();
	private int riskSecili;
	private int riskSurum = -1;
	private List<JsonObject> yasaklar = List.of();
	private int yasakSecili;
	private int yasakSurum = -1;
	private OnayTur onayTur = OnayTur.VAKA;
	private EditBox nedenKutu;
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
		yenileRisk();
		yenileYasak();
	}

	private void yenileYasak() {
		String id = str(yasakSec(), "id");
		if (yasakSurum >= 0 && yasakSurum != RiskHud.yasakSurum) {
			ONAYLADIM.clear(); // sunucudan yeni liste geldi: onay durumu artık listede
		}
		yasaklar = RiskHud.yasaklar();
		yasakSurum = RiskHud.yasakSurum;
		int yeni = Math.min(Math.max(0, yasakSecili), Math.max(0, yasaklar.size() - 1));
		for (int i = 0; i < yasaklar.size(); i++) {
			if (str(yasaklar.get(i), "id").equals(id)) {
				yeni = i;
				break;
			}
		}
		yasakSecili = yasaklar.isEmpty() ? -1 : yeni;
		if (sekme == Sekme.YASAK) {
			detayAnahtar = "";
		}
		if (onayVaka != null && (onayTur == OnayTur.KALDIR || onayTur == OnayTur.RED)
				&& yasaklar.stream().noneMatch(y -> str(y, "id").equals(str(onayVaka, "id")))) {
			modalKapat(); // yasak bu arada kaldırıldı
		}
	}

	private JsonObject yasakSec() {
		return yasakSecili >= 0 && yasakSecili < yasaklar.size() ? yasaklar.get(yasakSecili) : null;
	}

	/** Bir yasaktaki "görülmemiş" anahtarlar: yasağın kendisi + bekleyen her itiraz. */
	private static List<String> yasakAnahtarlari(JsonObject y) {
		List<String> l = new ArrayList<>();
		l.add(VakaMerkezi.str(y, "id"));
		if (y.has("talepler") && y.get("talepler").isJsonArray()) {
			for (JsonElement e : y.getAsJsonArray("talepler")) {
				if (e.isJsonObject() && "BEKLIYOR".equals(VakaMerkezi.str(e.getAsJsonObject(), "durum"))) {
					l.add(VakaMerkezi.str(y, "id") + ":" + VakaMerkezi.str(e.getAsJsonObject(), "id"));
				}
			}
		}
		return l;
	}

	private static int bekleyenTalep(JsonObject y) {
		return yasakAnahtarlari(y).size() - 1;
	}

	private void modalKapat() {
		modal = Modal.YOK;
		onayVaka = null;
		if (nedenKutu != null) {
			removeWidget(nedenKutu);
			nedenKutu = null;
		}
	}

	private void yenileRisk() {
		String ad = str(riskSec(), "ad");
		riskler = RiskHud.oyuncular();
		riskSurum = RiskHud.surum;
		int yeni = Math.min(Math.max(0, riskSecili), Math.max(0, riskler.size() - 1));
		for (int i = 0; i < riskler.size(); i++) {
			if (str(riskler.get(i), "ad").equals(ad)) {
				yeni = i;
				break;
			}
		}
		riskSecili = riskler.isEmpty() ? -1 : yeni;
		if (sekme == Sekme.RISK) {
			detayAnahtar = "";
		}
	}

	private JsonObject riskSec() {
		return riskSecili >= 0 && riskSecili < riskler.size() ? riskler.get(riskSecili) : null;
	}

	private List<JsonObject> aktif() {
		return switch (sekme) {
			case HILE -> vakalar;
			case RISK -> riskler;
			case YASAK -> yasaklar;
		};
	}

	private int aktifSecili() {
		return switch (sekme) {
			case HILE -> secili;
			case RISK -> riskSecili;
			case YASAK -> yasakSecili;
		};
	}

	private JsonObject aktifSec() {
		return switch (sekme) {
			case HILE -> sec();
			case RISK -> riskSec();
			case YASAK -> yasakSec();
		};
	}

	private void seciliYap(int i) {
		if (i == aktifSecili()) {
			return;
		}
		switch (sekme) {
			case HILE -> secili = i;
			case RISK -> riskSecili = i;
			case YASAK -> yasakSecili = i;
		}
		detayKaydir = 0;
	}

	private void sekmeYap(Sekme s) {
		if (s == sekme) {
			return;
		}
		sekme = s;
		sonSekme = s;
		listeKaydir = 0;
		detayKaydir = 0;
		detayAnahtar = "";
	}

	private int gorulmemis(Sekme s) {
		int n = 0;
		if (s == Sekme.HILE) {
			for (JsonObject v : vakalar) {
				n += GORULEN_VAKA.contains(str(v, "id")) ? 0 : 1;
			}
		} else if (s == Sekme.RISK) {
			for (JsonObject o : riskler) {
				n += GORULEN_RISK.contains(RiskHud.anahtar(o)) ? 0 : 1;
			}
		} else {
			for (JsonObject y : yasaklar) {
				for (String a : yasakAnahtarlari(y)) {
					n += GORULEN_YASAK.contains(a) ? 0 : 1;
				}
			}
		}
		return n;
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
		if (onayVaka != null && onayTur == OnayTur.VAKA && vakalar.stream().noneMatch(v -> str(v, "id").equals(str(onayVaka, "id")))) {
			modalKapat(); // vaka bu arada başka bir yetkili tarafından karara bağlandı
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

	/** Ekranda IP'nin yalnızca ilk bölümü görünür: 176.42.242.198 → 176.xxx.xxx.xxx */
	static String ipGizle(String ip) {
		if (ip == null || ip.isEmpty()) {
			return "";
		}
		char ayrac = ip.indexOf('.') >= 0 ? '.' : ':';
		int i = ip.indexOf(ayrac);
		if (i <= 0) {
			return ip.length() <= 3 ? ip : ip.substring(0, 3) + "xxxxx";
		}
		StringBuilder sb = new StringBuilder(ip.substring(0, i));
		for (int n = 0, parca = ayrac == '.' ? 3 : 2; n < parca; n++) {
			sb.append(ayrac).append("xxx");
		}
		return sb.toString();
	}

	/** Bu oturumda kaldırma onayı gönderdiğim yasaklar (sunucu listesi gelene kadar düğme yine tıklanmasın). */
	private static final java.util.Map<String, Long> ONAYLADIM = new java.util.HashMap<>();

	private String benimAdim() {
		return minecraft != null ? minecraft.getUser().getName() : "";
	}

	/** Sunucu yasak yetkim olmadığını bildirdiyse true (onay/red düğmeleri kapatılır). */
	private static boolean yetkisiz() {
		String rol = RiskHud.yasakRol;
		return rol != null && rol.isEmpty();
	}

	/** Bu yasağın kaldırılmasını zaten onayladım mı? */
	private boolean onayVerdim(JsonObject y) {
		if (y == null) {
			return false;
		}
		Long gonderim = ONAYLADIM.get(str(y, "id"));
		if (gonderim != null && Util.getMillis() - gonderim < 20_000L) {
			return true; // sunucunun güncel listesi gelene kadar
		}
		String ben = benimAdim();
		return diziObj(y, "onaylar").stream().anyMatch(o -> str(o, "ad").equalsIgnoreCase(ben));
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
		dugmeler.add(new Dugme(x, y, w, h, metin, renk, aktif, is, false));
	}

	/** Görünmez tıklama alanı (çizimini çağıran yapar). */
	private void alan(int x, int y, int w, int h, Runnable is) {
		dugmeler.add(new Dugme(x, y, w, h, "", 0, true, is, true));
	}

	private void dugmeleriCiz(GuiGraphics g, int mx, int my, int ilk) {
		for (int i = ilk; i < dugmeler.size(); i++) {
			Dugme d = dugmeler.get(i);
			if (d.gizli()) {
				continue;
			}
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
		if (RiskHud.surum != riskSurum) {
			yenileRisk();
		}
		if (RiskHud.yasakSurum != yasakSurum) {
			yenileYasak();
		}
		// Açık sekmedeki kayıtlar görülmüş sayılır; diğer sekmede yeni kayıt varsa rozet çıkar.
		if (sekme == Sekme.HILE) {
			vakalar.forEach(v -> GORULEN_VAKA.add(str(v, "id")));
		} else if (sekme == Sekme.RISK) {
			riskler.forEach(o -> GORULEN_RISK.add(RiskHud.anahtar(o)));
		} else {
			yasaklar.forEach(y -> GORULEN_YASAK.addAll(yasakAnahtarlari(y)));
		}
		super.render(g, mouseX, mouseY, partialTick);
		dugmeler.clear();
		boolean modalVar = modal != Modal.YOK;
		int mx = modalVar ? -1 : mouseX, my = modalVar ? -1 : mouseY;

		ustBar(g, mx, my);
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
			if (nedenKutu != null && modal == Modal.ONAY) {
				nedenKutu.render(g, mouseX, mouseY, partialTick);
			}
		}
		tostCiz(g);
	}

	private void ustBar(GuiGraphics g, int mx, int my) {
		g.fillGradient(0, 0, width, 24, alfa(0x18070A, (int) (gor.panel * 230)), alfa(0x0D0E14, (int) (gor.panel * 140)));
		g.fill(0, 24, width, 25, alfa(VURGU, 0xB0));
		int sag = width - M;
		float sc = Math.min(1.3f, s() * 1.1f);
		float tsc = Math.min(1.15f, s());
		int x = M + 2;
		int[] sayilar = {vakalar.size(), riskler.size(), yasaklar.size()};
		String[] uzun = {"Hile vakaları", "Karantina & risk", "Yasaklar"};
		String[] kisa = {"Vakalar", "Risk", "Yasak"};
		// Sığma hesabı: önce uzun adlar + "Görünüm"; sığmazsa kısa adlar; yine sığmazsa "Görünüm" simgeye döner.
		int uzunW = 0, kisaW = 0;
		for (int i = 0; i < 3; i++) {
			uzunW += gen(uzun[i] + "  " + sayilar[i], tsc) + 16 + 22 + 4;
			kisaW += gen(kisa[i] + "  " + sayilar[i], tsc) + 16 + 22 + 4;
		}
		Component baslik = Component.literal("⚠ KORUMA").withStyle(ChatFormatting.BOLD);
		int baslikW = (int) Math.ceil(font.width(baslik) * sc) + 10;
		int simgeW = (int) Math.ceil(font.width("⚠") * sc) + 10;
		int sagGenis = 47 + 74 + 6, sagDar = 47 + 26 + 6;
		boolean basTam = x + baslikW + uzunW <= sag - sagGenis;
		boolean adUzun = basTam || x + simgeW + uzunW <= sag - sagGenis;
		boolean gorunumTam = adUzun || x + simgeW + kisaW <= sag - sagGenis;
		int sagKenar = sag - (gorunumTam ? sagGenis : sagDar);
		Component b = basTam ? baslik : Component.literal("⚠").withStyle(ChatFormatting.BOLD);
		yaz(g, b, x, (24 - font.lineHeight * sc) / 2f + 1, BEYAZ, sc, true);
		x += basTam ? baslikW : simgeW;
		String[] ad = adUzun ? uzun : kisa;
		x = sekmeCiz(g, mx, my, x, sagKenar, Sekme.HILE, ad[0], sayilar[0], VURGU);
		x = sekmeCiz(g, mx, my, x + 4, sagKenar, Sekme.RISK, ad[1], sayilar[1], TURUNCU);
		sekmeCiz(g, mx, my, x + 4, sagKenar, Sekme.YASAK, ad[2], sayilar[2], 0xFFB794F6);
		dugme(sag - 22, 4, 22, 16, "A+", MAVI, gor.yazi < 2.0f, () -> yaziDegistir(0.125f));
		dugme(sag - 47, 4, 22, 16, "A−", MAVI, gor.yazi > 0.75f, () -> yaziDegistir(-0.125f));
		if (gorunumTam) {
			dugme(sag - 47 - 74, 4, 70, 16, "Görünüm", MAVI, true, () -> modal = Modal.GORUNUM);
		} else {
			dugme(sag - 47 - 26, 4, 22, 16, "⚙", MAVI, true, () -> modal = Modal.GORUNUM);
		}
	}

	/** Üst çubukta bir sekme; görülmemiş kayıt varsa kırmızı rozet. Sağ kenarını döndürür. */
	private int sekmeCiz(GuiGraphics g, int mx, int my, int x, int sinir, Sekme s, String ad, int sayi, int renk) {
		boolean secik = sekme == s;
		int yeni = s == sekme ? 0 : gorulmemis(s);
		float sc = Math.min(1.15f, s());
		String metin = ad + "  " + sayi;
		int rozetW = yeni > 0 ? gen(String.valueOf(yeni), 0.8f) + 8 : 0;
		int w = gen(metin, sc) + 16 + (rozetW > 0 ? rozetW + 4 : 0);
		if (x + w > sinir) {
			metin = ad.substring(0, Math.min(4, ad.length())) + "… " + sayi;
			w = gen(metin, sc) + 16 + (rozetW > 0 ? rozetW + 4 : 0);
		}
		int y1 = 3, y2 = 24;
		boolean ustte = mx >= x && mx < x + w && my >= y1 && my < y2;
		kutu(g, x, y1, x + w, y2, secik ? alfa(renk, 0x48) : ustte ? 0x30FFFFFF : 0x16FFFFFF);
		if (secik) {
			g.fill(x, y2 - 2, x + w, y2, renk);
		}
		yaz(g, metin, x + 8, (y1 + y2 - font.lineHeight * sc) / 2f + 1, secik ? BEYAZ : GRI, sc, true);
		if (yeni > 0) {
			int rx = x + w - rozetW - 6;
			int ry = y1 + 4;
			int rh = (int) Math.ceil(font.lineHeight * 0.8f) + 4;
			boolean yanip = (Util.getMillis() / 500) % 2 == 0;
			kutu(g, rx, ry, rx + rozetW, ry + rh, yanip ? 0xFFE5484D : 0xFFB8323A);
			yaz(g, String.valueOf(yeni), rx + 4, ry + 2.5f, 0xFFFFFFFF, 0.8f, false);
		}
		alan(x, y1, w, y2 - y1, () -> sekmeYap(s));
		return x + w;
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
		List<JsonObject> l0 = aktif();
		if (l0.isEmpty()) {
			String bos = switch (sekme) {
				case HILE -> "Bekleyen vaka yok.";
				case RISK -> "Riskli oyuncu yok.";
				case YASAK -> "Kalıcı yasak yok.";
			};
			String acik = switch (sekme) {
				case HILE -> "Karara bağlananlar sunucuda plugins/CevherKoruma/arsiv/ klasöründe.";
				case RISK -> "Launcher karantinası, hile istemcisi, IP engeli ve Vulcan bulguları burada listelenir.";
				case YASAK -> "Kalıcı yasaklananlar, kanıtları, kaldırma onayları ve siteden gelen itirazlar burada listelenir. Karar verilen (kaldırılan ya da reddedilen) yasaklar listeden düşer.";
			};
			yaz(g, bos, x1 + 8, y1 + 10, GRI, sc, true);
			List<FormattedCharSequence> l = font.split(Component.literal(acik),
				(int) ((x2 - x1 - 16) / sc));
			for (int i = 0; i < l.size(); i++) {
				yaz(g, l.get(i), x1 + 8, y1 + 12 + (i + 1) * satirH(), SOLUK, sc);
			}
			return;
		}
		int kh = kartH();
		int adim = kh + 3;
		float max = Math.max(0, l0.size() * adim + 6 - (y2 - y1));
		listeKaydir = Math.max(0, Math.min(max, listeKaydir));
		int sec = aktifSecili();
		g.enableScissor(x1 + 1, y1 + 1, x2 - 1, y2 - 1);
		for (int i = 0; i < l0.size(); i++) {
			int y = y1 + 4 + i * adim - (int) listeKaydir;
			if (y + kh < y1 || y > y2) {
				continue;
			}
			JsonObject v = l0.get(i);
			boolean ustte = mx >= x1 && mx < x2 && my >= Math.max(y, y1) && my < Math.min(y + kh, y2);
			switch (sekme) {
				case HILE -> vakaKarti(g, v, x1 + 4, x2 - 4, y, kh, i == sec, ustte, sc);
				case RISK -> riskKarti(g, v, x1 + 4, x2 - 4, y, kh, i == sec, ustte, sc);
				case YASAK -> yasakKarti(g, v, x1 + 4, x2 - 4, y, kh, i == sec, ustte, sc);
			}
		}
		g.disableScissor();
	}

	private void vakaKarti(GuiGraphics g, JsonObject v, int kx1, int kx2, int y, int kh, boolean secik, boolean ustte, float sc) {
		String durum = str(v, "durum");
		int renk = VakaMerkezi.renk(durum);
		kutu(g, kx1, y, kx2, y + kh, secik ? alfa(renk, 0x40) : ustte ? 0x28FFFFFF : 0x14FFFFFF);
		if (secik) {
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

	private void riskKarti(GuiGraphics g, JsonObject o, int kx1, int kx2, int y, int kh, boolean secik, boolean ustte, float sc) {
		String sev = str(o, "seviye");
		int renk = seviyeRenk(sev);
		boolean online = o.has("online") && o.get("online").getAsBoolean();
		kutu(g, kx1, y, kx2, y + kh, secik ? alfa(renk, 0x40) : ustte ? 0x28FFFFFF : 0x14FFFFFF);
		if (secik) {
			cerceve(g, kx1, y, kx2, y + kh, alfa(renk, 0xC0));
		}
		g.fill(kx1, y + 1, kx1 + 3, y + kh - 1, renk);
		float rs = sc * 0.8f;
		String rozet = seviyeYazi(sev);
		int rw = gen(rozet, rs) + 8;
		hap(g, rozet, kx2 - 4 - rw, y + 4, renk, rs);
		int nokta = (int) Math.ceil(font.lineHeight * sc * 0.5f);
		int ny = y + 5 + (int) (font.lineHeight * sc / 2) - nokta / 2;
		kutu(g, kx1 + 8, ny, kx1 + 8 + nokta, ny + nokta, online ? YESIL : SOLUK);
		String ad = sigdir(str(o, "ad"), kx2 - kx1 - rw - 30 - nokta, sc);
		yaz(g, Component.literal(ad).withStyle(ChatFormatting.BOLD), kx1 + 12 + nokta, y + 5, online ? BEYAZ : GRI, sc, true);
		String alt = kaynakYazi(str(o, "neden"));
		yaz(g, sigdir(alt, kx2 - kx1 - 14, sc * 0.9f), kx1 + 8, y + 7 + font.lineHeight * sc, GRI, sc * 0.9f, false);
	}

	static int seviyeRenk(String s) {
		return switch (s) {
			case "KRITIK" -> 0xFFFF5555;
			case "YUKSEK" -> 0xFFFFAA00;
			case "ORTA" -> 0xFFFFFF55;
			case "TEMIZ" -> YESIL;
			default -> 0xFFAAAAAA;
		};
	}

	static String seviyeYazi(String s) {
		return switch (s) {
			case "KRITIK" -> "KRİTİK";
			case "YUKSEK" -> "YÜKSEK";
			case "DUSUK" -> "DÜŞÜK";
			case "TEMIZ" -> "TEMİZ";
			default -> s;
		};
	}

	/** "lobi:launcher-karantina: x" → "Launcher karantinası (lobi): x" */
	static String kaynakYazi(String s) {
		if (s == null || s.isEmpty()) {
			return "";
		}
		String lobi = "";
		if (s.startsWith("lobi:")) {
			s = s.substring(5);
			lobi = " (lobi)";
		}
		int i = s.indexOf(':');
		String k = i < 0 ? s : s.substring(0, i);
		String geri = i < 0 ? "" : s.substring(i);
		String ad = switch (k) {
			case "launcher-karantina" -> "Launcher karantinası";
			case "ayni-ip" -> "Aynı IP";
			case "ip-engeli" -> "IP engeli";
			case "vulcan" -> "Vulcan";
			case "vulcan-ceza" -> "Vulcan cezası";
			case "koruma" -> "CevherKoruma";
			case "kalici-yasak" -> "Kalıcı yasaklıyla eşleşme";
			case "hile-istemcisi" -> "Hile istemcisi";
			case "mod" -> "Mod denetimi";
			default -> k;
		};
		return ad + lobi + geri;
	}

	private void detayCiz(GuiGraphics g) {
		int x1 = detayX(), x2 = width - M, y1 = UST, y2 = altY();
		kutu(g, x1, y1, x2, y2, panelRenk());
		cerceve(g, x1, y1, x2, y2, CERCEVE);
		JsonObject v = aktifSec();
		if (v == null) {
			yaz(g, sekme == Sekme.HILE ? "Soldan bir vaka seç." : sekme == Sekme.RISK ? "Soldan bir oyuncu seç." : "Soldan bir yasak seç.",
				x1 + 12, y1 + 12, GRI, s(), true);
			return;
		}
		int w = x2 - x1 - 24;
		String anahtar = sekme + "|" + str(v, "id") + str(v, "ad") + "|" + w + "|" + gor.yazi + "|" + v.hashCode();
		if (!anahtar.equals(detayAnahtar)) {
			detay = switch (sekme) {
				case HILE -> detayKur(v, w);
				case RISK -> riskDetayKur(v, w);
				case YASAK -> yasakDetayKur(v, w);
			};
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

	private void riskAltBar(GuiGraphics g) {
		JsonObject o = riskSec();
		boolean var = o != null;
		boolean online = var && o.has("online") && o.get("online").getAsBoolean();
		int y = height - 28, h = 22;
		int x = detayX();
		int sag = width - M;
		int bw = Math.min(130, Math.max(60, (sag - x - 8) / 3));
		float dsc = Math.min(s(), 1.25f);
		boolean kisa = gen("Oyuncuya ışınlan", dsc) > bw - 8;
		dugme(x, y, bw, h, kisa ? "Yasakla" : "Kalıcı yasakla", VURGU, var, () -> {
			onayVaka = riskSec();
			onayTur = OnayTur.RISK;
			modal = Modal.ONAY;
		});
		dugme(x + bw + 4, y, bw, h, kisa ? "Kaydı sil" : "Risk kaydını sil", GRI, var, () -> {
			JsonObject r = riskSec();
			if (r != null) {
				komut("cevherauth risktemizle " + str(r, "ad"), false);
				tost(str(r, "ad") + ": risk kaydı silindi (listeden birkaç saniyede kalkar)", GRI);
			}
		});
		dugme(sag - bw, y, bw, h, online ? (kisa ? "Işınlan" : "Oyuncuya ışınlan") : "Çevrimdışı", MAVI, online, () -> {
			JsonObject r = riskSec();
			if (r != null) {
				komut("tp " + str(r, "ad"), true);
			}
		});
		yaz(g, sigdir("Tab: sekme · ↑↓: seç · ESC: kapat", listeW() - 4, 0.85f), M + 2, height - 20, SOLUK, 0.85f, false);
	}

	private List<Oge> riskDetayKur(JsonObject o, int w) {
		List<Oge> l = new ArrayList<>();
		float sc = s();
		int lh = satirH();
		String sev = str(o, "seviye");
		int renk = seviyeRenk(sev);
		boolean online = o.has("online") && o.get("online").getAsBoolean();
		float bs = sc * 1.6f;
		int bh = (int) Math.ceil(font.lineHeight * bs) + 6;
		Component adC = Component.literal(str(o, "ad")).withStyle(ChatFormatting.BOLD);
		String sevHap = seviyeYazi(sev) + " RİSK";
		String onHap = online ? "çevrimiçi" : "çevrimdışı";
		int hapW = gen(sevHap, sc) + 8 + 4 + gen(onHap, sc) + 8;
		boolean yanYana = (int) Math.ceil(font.width(adC) * bs) + 10 + hapW <= w;
		l.add(new Oge(bh, (g, x, y, ww) -> {
			yaz(g, adC, x, y, BEYAZ, bs, true);
			if (yanYana) {
				int ax = x + (int) Math.ceil(font.width(adC) * bs) + 10;
				ax += hap(g, sevHap, ax, y + 2, renk, sc) + 4;
				hap(g, onHap, ax, y + 2, online ? YESIL : SOLUK, sc);
			}
		}));
		if (!yanYana) {
			l.add(new Oge((int) Math.ceil(font.lineHeight * sc) + 7, (g, x, y, ww) -> {
				int ax = x + hap(g, sevHap, x, y, renk, sc) + 4;
				hap(g, onHap, ax, y, online ? YESIL : SOLUK, sc);
			}));
		}
		List<String> cipler = new ArrayList<>();
		if (!str(o, "ip").isEmpty()) {
			cipler.add("IP " + ipGizle(str(o, "ip")));
		}
		if (o.has("son") && o.get("son").getAsLong() > 0) {
			cipler.add("Son görülme " + SAAT.format(Instant.ofEpochMilli(o.get("son").getAsLong())));
		}
		cipEkle(l, cipler, w, sc * 0.9f);
		bolum(l, "BULGULAR", TURUNCU, w);
		if (o.has("bulgular") && o.get("bulgular").isJsonArray() && !o.getAsJsonArray("bulgular").isEmpty()) {
			for (JsonElement e : o.getAsJsonArray("bulgular")) {
				if (!e.isJsonObject()) {
					continue;
				}
				JsonObject b = e.getAsJsonObject();
				boolean eski = b.has("eski") && b.get("eski").getAsBoolean();
				String bsev = str(b, "seviye");
				String zaman = b.has("zaman") ? SAAT.format(Instant.ofEpochMilli(b.get("zaman").getAsLong())) : "";
				String kaynak = kaynakYazi(str(b, "kaynak")) + (eski ? "  (eski)" : "");
				float ts = sc * 0.9f;
				int basW = gen("00.00 00:00:00", ts) + 8 + gen(seviyeYazi(bsev), ts) + 8 + 6;
				boolean sigar = basW + gen(kaynak, sc) <= w;
				l.add(new Oge(lh + 4, (g, x, y, ww) -> {
					yaz(g, zaman, x, y + 2, eski ? SOLUK : 0xFFB794F6, ts, false);
					int hx = x + gen("00.00 00:00:00", ts) + 8;
					hx += hap(g, seviyeYazi(bsev), hx, y, eski ? SOLUK : seviyeRenk(bsev), ts) + 6;
					if (sigar) {
						yaz(g, Component.literal(kaynak).withStyle(ChatFormatting.BOLD), hx, y + 2, eski ? GRI : BEYAZ, sc, true);
					}
				}));
				if (!sigar) {
					for (FormattedCharSequence p : font.split(Component.literal(kaynak).withStyle(ChatFormatting.BOLD), (int) ((w - 10) / sc))) {
						l.add(new Oge(lh, (g, x, y, ww) -> yaz(g, p, x + 10, y, eski ? GRI : BEYAZ, sc)));
					}
				}
				metin(l, str(b, "detay"), eski ? SOLUK : 0xFFC8CCD4, w - 10, sc * 0.9f, 10);
				bosluk(l, 4);
			}
		} else if (!str(o, "neden").isEmpty()) {
			metin(l, kaynakYazi(str(o, "neden")), 0xFFC8CCD4, w, sc);
		}
		bosluk(l, 6);
		metin(l, "Kalıcı yasakla: hesap + IP + bilgisayar yasaklanır. Risk kaydını sil: bu oyuncunun risk geçmişi temizlenir (ör. yanlış karantina).",
			SOLUK, w, sc * 0.85f);
		bosluk(l, 10);
		return l;
	}

	/** Hap etiketleri satıra sığdığı kadar dizer, sığmayanı alt satıra geçirir. */
	private void cipEkle(List<Oge> l, List<String> cipler, int w, float cs) {
		int chH = (int) Math.ceil(font.lineHeight * cs) + 6;
		List<String> mevcut = new ArrayList<>();
		int kalan = w;
		for (String c : cipler) {
			int cw = gen(c, cs) + 12;
			if (cw > kalan && !mevcut.isEmpty()) {
				List<String> sira = mevcut;
				l.add(new Oge(chH, (g, x, y, ww) -> {
					int cx = x;
					for (String s : sira) {
						cx += hap(g, sigdir(s, ww - 8, cs), cx, y, 0xFFB8C0CC, cs) + 4;
					}
				}));
				mevcut = new ArrayList<>();
				kalan = w;
			}
			mevcut.add(c);
			kalan -= cw;
		}
		if (!mevcut.isEmpty()) {
			List<String> sira = mevcut;
			l.add(new Oge(chH, (g, x, y, ww) -> {
				int cx = x;
				for (String s : sira) {
					cx += hap(g, sigdir(s, ww - 8, cs), cx, y, 0xFFB8C0CC, cs) + 4;
				}
			}));
		}
	}

	private void altBar(GuiGraphics g) {
		if (sekme == Sekme.RISK) {
			riskAltBar(g);
			return;
		}
		if (sekme == Sekme.YASAK) {
			yasakAltBar(g);
			return;
		}
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
			onayTur = OnayTur.VAKA;
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
		yaz(g, sigdir("Tab: sekme · ↑↓: seç · ESC: kapat", listeW() - 4, 0.85f), M + 2, height - 20, SOLUK, 0.85f, false);
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
			cipler.add("IP " + ipGizle(str(v, "ip")));
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

	// ── yasaklar sekmesi ───────────────────────────────────────────────────────

	private static final int MOR = 0xFFB794F6;

	private static List<String> diziStr(JsonObject o, String k) {
		List<String> l = new ArrayList<>();
		if (o != null && o.has(k) && o.get(k).isJsonArray()) {
			for (JsonElement e : o.getAsJsonArray(k)) {
				if (e.isJsonPrimitive()) {
					l.add(e.getAsString());
				}
			}
		}
		return l;
	}

	private static List<JsonObject> diziObj(JsonObject o, String k) {
		List<JsonObject> l = new ArrayList<>();
		if (o != null && o.has(k) && o.get(k).isJsonArray()) {
			for (JsonElement e : o.getAsJsonArray(k)) {
				if (e.isJsonObject()) {
					l.add(e.getAsJsonObject());
				}
			}
		}
		return l;
	}

	private static long uzun(JsonObject o, String k) {
		try {
			return o != null && o.has(k) ? o.get(k).getAsLong() : 0;
		} catch (RuntimeException e) {
			return 0;
		}
	}

	private static String tarih(long ms) {
		return ms <= 0 ? "?" : SAAT.format(Instant.ofEpochMilli(ms));
	}

	private void yasakKarti(GuiGraphics g, JsonObject y, int kx1, int kx2, int yy, int kh, boolean secik, boolean ustte, float sc) {
		int talep = bekleyenTalep(y);
		int renk = talep > 0 ? TURUNCU : MOR;
		kutu(g, kx1, yy, kx2, yy + kh, secik ? alfa(renk, 0x40) : ustte ? 0x28FFFFFF : 0x14FFFFFF);
		if (secik) {
			cerceve(g, kx1, yy, kx2, yy + kh, alfa(renk, 0xC0));
		}
		g.fill(kx1, yy + 1, kx1 + 3, yy + kh - 1, renk);
		float rs = sc * 0.8f;
		int hesap = diziStr(y, "adlar").size();
		String rozet = talep > 0 ? "İTİRAZ" : hesap + " hesap";
		int rw = gen(rozet, rs) + 8;
		hap(g, rozet, kx2 - 4 - rw, yy + 4, talep > 0 ? TURUNCU : MOR, rs);
		String ad = sigdir(str(y, "ana"), kx2 - kx1 - rw - 26, sc);
		yaz(g, Component.literal(ad).withStyle(ChatFormatting.BOLD), kx1 + 8, yy + 5, BEYAZ, sc, true);
		String alt = tarih(uzun(y, "zaman")) + " · " + str(y, "sebep");
		yaz(g, sigdir(alt, kx2 - kx1 - 14, sc * 0.9f), kx1 + 8, yy + 7 + font.lineHeight * sc, GRI, sc * 0.9f, false);
	}

	private List<Oge> yasakDetayKur(JsonObject y, int w) {
		List<Oge> l = new ArrayList<>();
		float sc = s();
		float bs = sc * 1.6f;
		int bh = (int) Math.ceil(font.lineHeight * bs) + 6;
		Component adC = Component.literal(str(y, "ana")).withStyle(ChatFormatting.BOLD);
		l.add(new Oge(bh, (g, x, yy, ww) -> yaz(g, adC, x, yy, BEYAZ, bs, true)));
		List<String> adlar = diziStr(y, "adlar");
		List<String> ipler = diziStr(y, "ipler");
		JsonObject kanit = y.has("kanit") && y.get("kanit").isJsonObject() ? y.getAsJsonObject("kanit") : null;
		int guven = kanit != null && kanit.has("guven") ? kanit.get("guven").getAsInt() : -1;
		List<String> haplar = new ArrayList<>();
		haplar.add("KALICI YASAK");
		haplar.add(adlar.size() + " hesap");
		haplar.add(ipler.size() + " IP");
		haplar.add(str(y, "cihaz") + " cihaz izi");
		if (guven >= 0) {
			haplar.add("hile %" + guven);
		}
		cipEkle(l, haplar, w, sc * 0.9f);
		bosluk(l, 2);

		bolum(l, "YASAK", 0xFFFF8A8A, w);
		satir(l, "Sebep", str(y, "sebep"), w, sc);
		satir(l, "Yasaklayan", str(y, "yetkili"), w, sc);
		satir(l, "Tarih", tarih(uzun(y, "zaman")), w, sc);
		if (!str(y, "vaka").isEmpty()) {
			satir(l, "Vaka", str(y, "vaka"), w, sc);
		}
		satir(l, "Kayıt no", str(y, "id"), w, sc);

		bolum(l, "HESAPLAR (" + adlar.size() + ")", MAVI, w);
		metin(l, String.join(", ", adlar), BEYAZ, w, sc);
		metin(l, "Aynı bilgisayardan ya da IP'den sonradan giren hesaplar otomatik eklenir.", SOLUK, w, sc * 0.85f);
		bolum(l, "IP ADRESLERİ (" + ipler.size() + ")", MAVI, w);
		metin(l, ipler.isEmpty() ? "—" : String.join(", ", ipler.stream().map(VakaEkrani::ipGizle).toList()), BEYAZ, w, sc);
		bolum(l, "BİLGİSAYAR", MAVI, w);
		metin(l, str(y, "cihaz") + " cihaz izi · " + str(y, "damga") + " yasak damgası", BEYAZ, w, sc);

		if (kanit != null) {
			bolum(l, "KANIT", TURUNCU, w);
			if (guven >= 0) {
				int renk = guven >= 85 ? VURGU : guven >= 60 ? TURUNCU : 0xFFFFFF55;
				metin(l, "Yapay zekâ: " + kararYazi(str(kanit, "karar")) + " — %" + guven + " oranında hile tespit edildi", BEYAZ, w, sc);
				l.add(new Oge(10, (g, x, yy, ww) -> {
					int by = yy + 1;
					int bw = Math.min(ww, 320);
					kutu(g, x, by, x + bw, by + 6, 0x30FFFFFF);
					kutu(g, x, by, x + Math.max(3, bw * guven / 100), by + 6, renk);
				}));
			}
			if (!str(kanit, "tur").isEmpty()) {
				satir(l, "Hile türü", str(kanit, "tur"), w, sc);
			}
			if (!str(kanit, "ozet").isEmpty()) {
				metin(l, str(kanit, "ozet"), BEYAZ, w, sc);
			}
			if (!str(kanit, "gerekce").isEmpty()) {
				metin(l, str(kanit, "gerekce"), GRI, w, sc);
			}
			if (!str(kanit, "not").isEmpty()) {
				satir(l, "Yetkili notu", str(kanit, "not"), w, sc);
			}
			List<String> ciplerK = new ArrayList<>();
			if (uzun(kanit, "vakaBas") > 0) {
				ciplerK.add("Vaka " + tarih(uzun(kanit, "vakaBas")));
			}
			ciplerK.add("Ping " + str(kanit, "ping") + " ms");
			ciplerK.add("TPS " + str(kanit, "tps"));
			ciplerK.add("Puan " + str(kanit, "puan"));
			if (!str(kanit, "dunya").isEmpty()) {
				ciplerK.add(str(kanit, "dunya") + " " + str(kanit, "konum"));
			}
			if (!str(kanit, "istemci").isEmpty()) {
				ciplerK.add("İstemci " + str(kanit, "istemci"));
			}
			cipEkle(l, ciplerK, w, sc * 0.9f);
			List<String> kontroller = diziStr(kanit, "kontroller");
			if (!kontroller.isEmpty()) {
				metin(l, "Ne yaptı:", TURUNCU, w, sc);
				for (String k : kontroller) {
					metin(l, "• " + k, 0xFFE0E0E0, w - 8, sc * 0.95f, 8);
				}
			}
			List<String> zc = diziStr(kanit, "satirlar");
			if (!zc.isEmpty()) {
				metin(l, "Zaman çizelgesi:", MOR, w, sc);
				for (String z : zc) {
					metin(l, z, 0xFFC8CCD4, w - 8, sc * 0.85f, 8);
				}
			}
		} else {
			bolum(l, "KANIT", TURUNCU, w);
			metin(l, "Bu yasak vaka ekranı dışından verildi (komutla ya da karantina sekmesinden); yapay zekâ kanıtı yok.", SOLUK, w, sc);
		}

		List<JsonObject> onaylar = diziObj(y, "onaylar");
		bolum(l, "YASAK KALDIRMA (" + onaylar.size() + "/2 ONAY)", YESIL, w);
		if (onaylar.isEmpty()) {
			metin(l, "Henüz onay yok. Yasak; bir kurucu ve bir üst yetkili (ya da ikinci bir kurucu) onaylayınca kalkar.", GRI, w, sc);
		} else {
			for (JsonObject o : onaylar) {
				metin(l, "✔ " + str(o, "ad") + " (" + str(o, "rol") + ") — " + tarih(uzun(o, "zaman")), YESIL, w, sc);
			}
			boolean kurucu = onaylar.stream().anyMatch(o -> "KURUCU".equals(str(o, "rol")));
			metin(l, kurucu ? "İkinci bir üst yetkili ya da kurucu onayı bekleniyor." : "Bir kurucu onayı bekleniyor.", TURUNCU, w, sc);
		}
		if (yetkisiz()) {
			metin(l, "Bu hesabın yasak onayı / reddi yetkisi yok (kurucu ya da üst yetkili olmalı).", TURUNCU, w, sc);
		} else if (onayVerdim(y)) {
			metin(l, "Zaten onay verdiniz. Diğer yetkilinin onayı bekleniyor.", MAVI, w, sc);
		}

		List<JsonObject> talepler = diziObj(y, "talepler");
		bolum(l, "İTİRAZ TALEPLERİ (" + talepler.size() + ")", TURUNCU, w);
		if (talepler.isEmpty()) {
			metin(l, "Siteden gelen itiraz yok.", SOLUK, w, sc);
		}
		for (int i = talepler.size() - 1; i >= 0; i--) {
			JsonObject t = talepler.get(i);
			String d = str(t, "durum");
			int dr = "BEKLIYOR".equals(d) ? TURUNCU : "KABUL".equals(d) ? YESIL : VURGU;
			String dy = "BEKLIYOR".equals(d) ? "BEKLİYOR" : "KABUL".equals(d) ? "KABUL EDİLDİ" : "REDDEDİLDİ";
			int lh = satirH();
			String ust = tarih(uzun(t, "zaman")) + " · " + str(t, "hesap");
			l.add(new Oge(lh + 4, (g, x, yy, ww) -> {
				int hx = x + hap(g, dy, x, yy, dr, sc * 0.9f) + 6;
				yaz(g, ust, hx, yy + 2, GRI, sc * 0.9f, false);
			}));
			metin(l, "“" + str(t, "mesaj") + "”", BEYAZ, w - 8, sc, 8);
			if (!str(t, "islemYapan").isEmpty()) {
				metin(l, ("REDDEDILDI".equals(d) ? "Reddeden: " : "Onaylayanlar: ") + str(t, "islemYapan")
					+ (str(t, "islemNeden").isEmpty() ? "" : " — " + str(t, "islemNeden")), SOLUK, w - 8, sc * 0.9f, 8);
			}
			bosluk(l, 4);
		}

		List<String> gecmis = diziStr(y, "gecmis");
		if (!gecmis.isEmpty()) {
			bolum(l, "GEÇMİŞ", GRI, w);
			for (int i = gecmis.size() - 1; i >= 0; i--) {
				metin(l, "• " + gecmis.get(i), 0xFFB8BCC4, w - 8, sc * 0.85f, 8);
			}
		}
		bosluk(l, 10);
		return l;
	}

	/** "Etiket: değer" satırı (etiket soluk, değer beyaz). */
	private void satir(List<Oge> l, String etiket, String deger, int w, float sc) {
		String t = etiket + ": " + deger;
		int lh = (int) Math.ceil(font.lineHeight * sc) + 2;
		List<FormattedCharSequence> p = font.split(Component.literal(t), (int) (w / sc));
		for (int i = 0; i < p.size(); i++) {
			FormattedCharSequence s = p.get(i);
			boolean ilk = i == 0;
			l.add(new Oge(lh, (g, x, y, ww) -> {
				if (ilk) {
					yaz(g, etiket + ":", x, y, GRI, sc, false);
					yaz(g, sigdir(deger, ww - gen(etiket + ": ", sc), sc), x + gen(etiket + ": ", sc), y, BEYAZ, sc, true);
				} else {
					yaz(g, s, x, y, BEYAZ, sc);
				}
			}));
			if (ilk) {
				break; // değer tek satırda sığdırılır
			}
		}
	}

	private void yasakAltBar(GuiGraphics g) {
		JsonObject y = yasakSec();
		boolean var = y != null;
		int talep = var ? bekleyenTalep(y) : 0;
		int yy = height - 28, h = 22;
		int x = detayX();
		int sag = width - M;
		int bw = Math.min(150, Math.max(60, (sag - x - 8) / 3));
		float dsc = Math.min(s(), 1.25f);
		boolean kisa = gen("Zaten onay verdiniz", dsc) > bw - 8;
		boolean verdim = onayVerdim(y);
		boolean yetkisiz = yetkisiz();
		String onayYazi = yetkisiz ? (kisa ? "Yetkin yok" : "Onay yetkin yok")
			: verdim ? (kisa ? "Onay verildi" : "Zaten onay verdiniz") : (kisa ? "Onayla" : "Kaldırmayı onayla");
		dugme(x, yy, bw, h, onayYazi, YESIL, var && !verdim && !yetkisiz, () -> {
			onayVaka = yasakSec();
			onayTur = OnayTur.KALDIR;
			modal = Modal.ONAY;
		});
		// Reddet: itiraz varsa onu reddeder (24 saat yeni itiraz açılamaz); yoksa kaldırmayı reddeder. Yasak sürer, listeden düşer.
		dugme(x + bw + 4, yy, bw, h, kisa ? "Reddet" : talep > 0 ? "İtirazı reddet" : "Kaldırmayı reddet", VURGU, var && !yetkisiz, () -> {
			onayVaka = yasakSec();
			onayTur = OnayTur.RED;
			modal = Modal.ONAY;
		});
		yaz(g, sigdir("Tab: sekme · ↑↓: seç · ESC: kapat", listeW() - 4, 0.85f), M + 2, height - 20, SOLUK, 0.85f, false);
	}

	/** Yasak kaldırma onayı / itiraz reddi kutusu. */
	private void yasakOnayCiz(GuiGraphics g) {
		JsonObject y = onayVaka;
		float sc = Math.min(s(), 1.4f);
		boolean kaldir = onayTur == OnayTur.KALDIR;
		String ad = str(y, "ana");
		int renk = kaldir ? YESIL : VURGU;
		List<String[]> satirlar = new ArrayList<>();
		if (kaldir) {
			int n = diziObj(y, "onaylar").size();
			satirlar.add(new String[] {ad + " yasağının kaldırılmasını onaylıyor musun?", "B"});
			satirlar.add(new String[] {"Şu an " + n + "/2 onay var. Yasak; bir kurucu ve bir üst yetkili (ya da ikinci bir kurucu) onaylayınca kalkar.", "G"});
			satirlar.add(new String[] {"Onayın sunucuda adınla kaydedilir. Yetkin yoksa (kurucu / üst yetkili) sunucu reddeder.", "S"});
		} else if (bekleyenTalep(y) > 0) {
			satirlar.add(new String[] {ad + " için bekleyen itiraz reddedilecek.", "B"});
			satirlar.add(new String[] {"Yasak sürer ve bu listeden kalkar. Oyuncu 24 saat boyunca yeni itiraz açamaz; verilmiş kaldırma onayları sıfırlanır.", "G"});
			satirlar.add(new String[] {"Reddetme nedeni (isteğe bağlı) oyuncunun sitesinde görünür:", "G"});
		} else {
			satirlar.add(new String[] {ad + " yasağının kaldırılması reddedilecek.", "B"});
			satirlar.add(new String[] {"Yasak sürer ve bu listeden kalkar; verilmiş kaldırma onayları sıfırlanır. Oyuncu siteden itiraz ederse yeniden görünür.", "G"});
			satirlar.add(new String[] {"Neden (isteğe bağlı, geçmişe yazılır):", "G"});
		}
		int bw = Math.min(width - 40, (int) (380 * sc));
		List<FormattedCharSequence> cizgiler = new ArrayList<>();
		List<Integer> renkler = new ArrayList<>();
		for (String[] s : satirlar) {
			int r = "B".equals(s[1]) ? BEYAZ : "G".equals(s[1]) ? 0xFFD0D4DC : GRI;
			for (FormattedCharSequence p : font.split(Component.literal(s[0]), (int) ((bw - 28) / sc))) {
				cizgiler.add(p);
				renkler.add(r);
			}
		}
		int lh = (int) Math.ceil(font.lineHeight * sc) + 2;
		int kutuH = kaldir ? 0 : 26;
		int bh = 34 + cizgiler.size() * lh + kutuH + 44;
		int x1 = (width - bw) / 2, y1 = (height - bh) / 2;
		kutu(g, x1, y1, x1 + bw, y1 + bh, 0xF0121218);
		cerceve(g, x1, y1, x1 + bw, y1 + bh, alfa(renk, 0xD0));
		g.fill(x1 + 1, y1 + 1, x1 + bw - 1, y1 + 26, alfa(renk, 0x40));
		yaz(g, Component.literal(kaldir ? "✔ YASAK KALDIRMA ONAYI" : "✖ İTİRAZI REDDET").withStyle(ChatFormatting.BOLD), x1 + 12,
			y1 + (26 - font.lineHeight * sc) / 2f + 1, renk, sc, true);
		int yy = y1 + 34;
		for (int i = 0; i < cizgiler.size(); i++) {
			yaz(g, cizgiler.get(i), x1 + 14, yy, renkler.get(i), sc);
			yy += lh;
		}
		if (!kaldir) {
			if (nedenKutu == null) {
				nedenKutu = new EditBox(font, x1 + 14, yy + 2, bw - 28, 18, Component.literal("Neden"));
				nedenKutu.setMaxLength(200);
				addWidget(nedenKutu);
				setFocused(nedenKutu);
			}
			nedenKutu.setX(x1 + 14);
			nedenKutu.setY(yy + 2);
			nedenKutu.setWidth(bw - 28);
		}
		int dy = y1 + bh - 32;
		int dw = (bw - 36) / 2;
		String id = str(y, "id");
		boolean verdim = kaldir && onayVerdim(y);
		dugme(x1 + 12, dy, dw, 22, verdim ? "Zaten onay verdiniz" : kaldir ? "Onayla" : "Reddet", renk, !verdim, () -> {
			if (kaldir) {
				ONAYLADIM.put(id, Util.getMillis());
				detayAnahtar = "";
				komut("cevherauth yasakonay " + id, false);
				tost(ad + ": kaldırma onayın gönderildi", YESIL);
			} else {
				boolean itiraz = bekleyenTalep(y) > 0;
				String neden = nedenKutu == null ? "" : nedenKutu.getValue().strip();
				komut("cevherauth talepred " + id + (neden.isEmpty() ? "" : " " + neden), false);
				tost(ad + (itiraz ? ": itiraz reddedildi (24 saat yeni itiraz yok)" : ": kaldırma reddedildi"), VURGU);
			}
			modalKapat();
		});
		dugme(x1 + bw - 12 - dw, dy, dw, 22, "İptal", GRI, true, this::modalKapat);
	}

	private void onayCiz(GuiGraphics g) {
		JsonObject v = onayVaka;
		if (v == null) {
			modalKapat();
			return;
		}
		if (onayTur == OnayTur.KALDIR || onayTur == OnayTur.RED) {
			yasakOnayCiz(g);
			return;
		}
		float sc = Math.min(s(), 1.4f);
		boolean test = v.has("test") && v.get("test").getAsBoolean();
		String ad = str(v, "ad");
		String ip = str(v, "ip").isEmpty() ? "bilinmiyor" : ipGizle(str(v, "ip"));
		List<String[]> satirlar = new ArrayList<>();
		satirlar.add(new String[] {ad + " sunucudan KALICI olarak yasaklanacak:", "B"});
		satirlar.add(new String[] {"•  Hesabı: " + ad, "G"});
		satirlar.add(new String[] {"•  IP adresi: " + ip, "G"});
		satirlar.add(new String[] {"•  Bilgisayarı: cihaz izi + bilgisayarına yazılan yasak damgası", "G"});
		satirlar.add(new String[] {"•  Aynı bilgisayardan veya IP'den girecek başka hesaplar da otomatik yasaklanır", "G"});
		satirlar.add(new String[] {"", "S"});
		if (onayTur == OnayTur.RISK) {
			satirlar.add(new String[] {"Sebep: " + kaynakYazi(str(v, "neden")), "S"});
			satirlar.add(new String[] {"Geri almak için: /cevherauth yasakkaldir " + ad, "S"});
		} else {
			satirlar.add(new String[] {"Vaka arşive yazılır ve listeden kaldırılır. Geri almak için: /cevherauth yasakkaldir " + ad, "S"});
		}
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
		boolean risk = onayTur == OnayTur.RISK;
		dugme(x1 + 12, dy, dw, 22, test ? "Onayla — Arşivle" : "Onayla — Yasakla", VURGU, true, () -> {
			if (risk) {
				String sebep = kaynakYazi(str(v, "neden"));
				sebep = sebep.isEmpty() ? "Risk listesi" : sebep;
				if (sebep.length() > 90) {
					sebep = sebep.substring(0, 89) + "…";
				}
				komut("cevherauth yasakla " + ad + " " + sebep, false);
			} else {
				komut("hilevaka yasakla " + str(v, "id"), false);
			}
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
		int bh = 34 + lh * 3 + 30 + 26 + 3 * (lh + 12) + 50;
		int x1 = (width - bw) / 2, y1 = (height - bh) / 2;
		kutu(g, x1, y1, x1 + bw, y1 + bh, 0xF0121218);
		cerceve(g, x1, y1, x1 + bw, y1 + bh, alfa(MAVI, 0xC0));
		g.fill(x1 + 1, y1 + 1, x1 + bw - 1, y1 + 26, alfa(MAVI, 0x30));
		yaz(g, Component.literal("GÖRÜNÜM").withStyle(ChatFormatting.BOLD), x1 + 12, y1 + (26 - font.lineHeight * sc) / 2f + 1, MAVI, sc, true);
		int y = y1 + 34;
		boolean we = WallpaperEngine.SECIM.equals(gor.arkaplan);
		String dosya = gor.arkaplan.isEmpty() ? "Yok (oyunun bulanık görüntüsü)"
			: we ? "Wallpaper Engine" + (ArkaPlan.bilgi().isEmpty() ? "" : " — " + ArkaPlan.bilgi())
			: Path.of(gor.arkaplan).getFileName().toString();
		yaz(g, "Arka plan: " + sigdir(dosya, bw - 100, sc), x1 + 14, y, BEYAZ, sc, true);
		y += lh;
		String hata = ArkaPlan.hata();
		String ipucu = we ? "Masaüstünde seçili duvar kağıdı; menü her açılışta yeniden okunur"
			: "PNG, JPG, BMP, GIF (hareketli) veya MP4/MOV video (H.264, sessiz)";
		yaz(g, dosyaSeciliyor ? "Dosya seçme penceresi açık…" : !hata.isEmpty() ? sigdir(hata, bw - 28, sc * 0.9f)
			: sigdir(ipucu, bw - 28, sc * 0.9f), x1 + 14, y, !hata.isEmpty() ? TURUNCU : SOLUK, sc * 0.9f, false);
		y += lh + 4;
		int dw = (bw - 36) / 2;
		dugme(x1 + 12, y, dw, 20, "Fotoğraf / video seç…", MAVI, !dosyaSeciliyor, this::dosyaSec);
		dugme(x1 + bw - 12 - dw, y, dw, 20, "Arka planı kaldır", GRI, !gor.arkaplan.isEmpty(), () -> {
			gor.arkaplan = "";
			gor.kaydet();
			medyaTemizle(null);
			tost("Arka plan kaldırıldı", GRI);
		});
		y += 26;
		dugme(x1 + 12, y, bw - 24, 20, we ? "Wallpaper Engine'i yeniden oku" : "Wallpaper Engine'e bağla", MAVI, !dosyaSeciliyor, () -> {
			gor.arkaplan = WallpaperEngine.SECIM;
			gor.kaydet();
			medyaTemizle(null);
			ArkaPlan.birak();
			ArkaPlan.hazirla(WallpaperEngine.SECIM);
			if (ArkaPlan.var()) {
				tost("Wallpaper Engine'e bağlandı: " + ArkaPlan.bilgi(), MAVI);
			} else {
				tost(ArkaPlan.hata().isEmpty() ? "Wallpaper Engine duvar kağıdı yüklenemedi" : ArkaPlan.hata(), TURUNCU);
			}
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
		if (event.button() == 0 && x >= x1 && x < x2 && y >= UST && y < altY() && !aktif().isEmpty()) {
			int adim = kartH() + 3;
			int i = (int) ((y - UST - 4 + listeKaydir) / adim);
			if (i >= 0 && i < aktif().size()) {
				seciliYap(i);
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
			modalKapat();
			return true;
		}
		if (modal == Modal.YOK && event.key() == GLFW.GLFW_KEY_TAB) {
			sekmeYap(Sekme.values()[(sekme.ordinal() + 1) % Sekme.values().length]);
			return true;
		}
		if (modal == Modal.YOK && !aktif().isEmpty()) {
			if (event.key() == GLFW.GLFW_KEY_DOWN || event.key() == GLFW.GLFW_KEY_UP) {
				int yeni = Math.max(0, Math.min(aktif().size() - 1, aktifSecili() + (event.key() == GLFW.GLFW_KEY_DOWN ? 1 : -1)));
				if (yeni != aktifSecili()) {
					seciliYap(yeni);
					int adim = kartH() + 3;
					float ust = yeni * adim, alt = ust + adim + 8 - (altY() - UST);
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
