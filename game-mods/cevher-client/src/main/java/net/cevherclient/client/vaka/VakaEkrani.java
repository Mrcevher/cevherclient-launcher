package net.cevherclient.client.vaka;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Hile vakaları sekmesi: solda vaka listesi, sağda seçili vakanın tüm detayları (zaman, süre, ne yaptı, LLM kararı). */
public final class VakaEkrani extends Screen {
	private static final DateTimeFormatter SAAT = DateTimeFormatter.ofPattern("dd.MM HH:mm:ss").withZone(ZoneId.systemDefault());
	private static final int LISTE_W = 180;
	private static final int SATIR_H = 22;

	private record Satir(FormattedCharSequence metin, int renk) {
	}

	private final Screen onceki;
	private List<JsonObject> vakalar = List.of();
	private int secili;
	private int listeKaydir;
	private int detayKaydir;
	private List<Satir> detay = List.of();
	private Button isinlan;
	private Button incelet;

	public VakaEkrani(Screen onceki) {
		super(Component.literal("Hile vakaları"));
		this.onceki = onceki;
	}

	@Override
	protected void init() {
		vakalar = VakaMerkezi.liste();
		int sag = width - 10;
		isinlan = addRenderableWidget(Button.builder(Component.literal("Oyuncuya ışınlan"), b -> komut("tp " + str(sec(), "ad")))
			.bounds(sag - 230, height - 28, 110, 20).build());
		incelet = addRenderableWidget(Button.builder(Component.literal("LLM'e yeniden incelet"), b -> komut("hilevaka incele " + str(sec(), "id")))
			.bounds(sag - 115, height - 28, 115, 20).build());
		detayHazirla();
	}

	private JsonObject sec() {
		return secili >= 0 && secili < vakalar.size() ? vakalar.get(secili) : null;
	}

	private void komut(String k) {
		if (minecraft != null && minecraft.player != null && sec() != null) {
			minecraft.player.connection.sendCommand(k);
			onClose();
		}
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

	private void detayHazirla() {
		JsonObject v = sec();
		boolean var = v != null;
		if (isinlan != null) {
			isinlan.active = var;
			incelet.active = var;
		}
		List<Satir> l = new ArrayList<>();
		if (!var) {
			detay = l;
			return;
		}
		int w = width - LISTE_W - 40;
		Font f = font;
		String durum = str(v, "durum");
		int guven = v.has("guven") ? v.get("guven").getAsInt() : -1;
		ekle(l, f, w, str(v, "ad") + "  —  " + durum + (guven >= 0 ? " (LLM güveni %" + guven + ")" : ""), VakaMerkezi.renk(durum));
		if (v.has("test") && v.get("test").getAsBoolean()) {
			ekle(l, f, w, "TEST VAKASI (örnek veriler)", 0xFFFFAA00);
		}
		ekle(l, f, w, "Vaka " + str(v, "id") + " · puan " + str(v, "puan") + (v.has("kesin") && v.get("kesin").getAsBoolean() ? " · kesin eşik aşıldı" : ""), 0xFF909090);
		bos(l);
		ekle(l, f, w, "Başlangıç: " + saat(v, "bas") + "   Bitiş: " + saat(v, "bit") + "   Süre: " + sure(v), 0xFFFFFFFF);
		ekle(l, f, w, "Konum: " + str(v, "dunya") + " " + str(v, "konum") + "   Oyun modu: " + str(v, "mod") + "   İstemci: " + str(v, "istemci"), 0xFFD0D0D0);
		ekle(l, f, w, "Ping: ort " + str(v, "pingOrt") + " ms (" + str(v, "pingMin") + "-" + str(v, "pingMax") + ")   Sunucu TPS: " + str(v, "tps")
			+ "   IP: " + str(v, "ip"), 0xFFD0D0D0);
		bos(l);
		if (!str(v, "tur").isEmpty()) {
			ekle(l, f, w, "Hile türü: " + str(v, "tur"), 0xFFFF7777);
		}
		if (!str(v, "ozet").isEmpty()) {
			ekle(l, f, w, "LLM özeti: " + str(v, "ozet"), 0xFFFFFFFF);
		}
		if (!str(v, "gerekce").isEmpty()) {
			ekle(l, f, w, "Gerekçe: " + str(v, "gerekce"), 0xFFB0B0B0);
		}
		if (!str(v, "llmHata").isEmpty()) {
			ekle(l, f, w, "LLM değerlendiremedi: " + str(v, "llmHata") + " — lütfen kendin incele.", 0xFFFFFF55);
		}
		if (v.has("llmMs") && v.get("llmMs").getAsLong() > 0) {
			ekle(l, f, w, "LLM karar süresi: " + (v.get("llmMs").getAsLong() / 1000) + " sn", 0xFF707070);
		}
		bos(l);
		ekle(l, f, w, "Ne yaptı:", 0xFFFFAA00);
		if (v.has("kontroller")) {
			for (JsonElement x : v.getAsJsonArray("kontroller")) {
				JsonObject c = x.getAsJsonObject();
				ekle(l, f, w, " • " + str(c, "etiket") + " ×" + str(c, "sayi") + " — en yüksek: " + str(c, "enYuksek"), 0xFFE0E0E0);
			}
		}
		bos(l);
		ekle(l, f, w, "Nasıl yaptı (zaman çizelgesi):", 0xFFFFAA00);
		if (v.has("satirlar")) {
			for (JsonElement x : v.getAsJsonArray("satirlar")) {
				ekle(l, f, w, " " + x.getAsString(), 0xFFB8B8B8);
			}
		}
		detay = l;
	}

	private static void ekle(List<Satir> l, Font f, int w, String s, int renk) {
		for (FormattedCharSequence p : f.split(Component.literal(s), w)) {
			l.add(new Satir(p, renk));
		}
	}

	private static void bos(List<Satir> l) {
		l.add(new Satir(FormattedCharSequence.EMPTY, 0));
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		Font f = font;
		g.drawString(f, "⚠ Hile vakaları", 10, 10, 0xFFFFFFFF, true);
		g.drawString(f, vakalar.size() + " kayıt · yalnızca OP'lar görür", 10 + f.width("⚠ Hile vakaları  "), 10, 0xFF808080, true);

		int ust = 26, alt = height - 34;
		// Sol: liste
		g.fill(10, ust, 10 + LISTE_W, alt, 0x90000000);
		if (vakalar.isEmpty()) {
			g.drawString(f, "Henüz vaka yok.", 16, ust + 8, 0xFF909090, true);
		}
		g.enableScissor(10, ust, 10 + LISTE_W, alt);
		for (int i = 0; i < vakalar.size(); i++) {
			int y = ust + i * SATIR_H - listeKaydir;
			if (y + SATIR_H < ust || y > alt) {
				continue;
			}
			JsonObject v = vakalar.get(i);
			boolean ust_ = mouseX >= 10 && mouseX < 10 + LISTE_W && mouseY >= y && mouseY < y + SATIR_H && mouseY >= ust && mouseY < alt;
			if (i == secili) {
				g.fill(10, y, 10 + LISTE_W, y + SATIR_H, 0x60FFFFFF);
			} else if (ust_) {
				g.fill(10, y, 10 + LISTE_W, y + SATIR_H, 0x30FFFFFF);
			}
			String durum = str(v, "durum");
			g.fill(10, y, 12, y + SATIR_H, VakaMerkezi.renk(durum));
			g.drawString(f, VakaMerkezi.sigdir(f, str(v, "ad"), 90), 16, y + 2, 0xFFFFFFFF, true);
			g.drawString(f, VakaMerkezi.sigdir(f, durum, 80), 10 + LISTE_W - 4 - Math.min(80, f.width(durum)), y + 2, VakaMerkezi.renk(durum), true);
			g.drawString(f, saat(v, "bas") + " · " + sure(v), 16, y + 12, 0xFF808080, false);
		}
		g.disableScissor();

		// Sağ: detay
		int dx = 20 + LISTE_W;
		g.fill(dx, ust, width - 10, alt, 0x90000000);
		g.enableScissor(dx, ust, width - 10, alt);
		int satirH = f.lineHeight + 1;
		for (int i = 0; i < detay.size(); i++) {
			int y = ust + 6 + i * satirH - detayKaydir;
			if (y + satirH < ust || y > alt) {
				continue;
			}
			g.drawString(f, detay.get(i).metin(), dx + 6, y, detay.get(i).renk(), true);
		}
		g.disableScissor();
		g.drawString(f, "Tekerlek: kaydır · ESC: kapat", 10, height - 22, 0xFF707070, false);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		int ust = 26, alt = height - 34;
		double x = event.x(), y = event.y();
		if (event.button() == 0 && x >= 10 && x < 10 + LISTE_W && y >= ust && y < alt) {
			int i = (int) ((y - ust + listeKaydir) / SATIR_H);
			if (i >= 0 && i < vakalar.size()) {
				secili = i;
				detayKaydir = 0;
				detayHazirla();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
		int ust = 26, alt = height - 34;
		int adim = (int) (-vertical * 18);
		if (x < 10 + LISTE_W) {
			int max = Math.max(0, vakalar.size() * SATIR_H - (alt - ust));
			listeKaydir = Math.max(0, Math.min(max, listeKaydir + adim));
		} else {
			int max = Math.max(0, detay.size() * (font.lineHeight + 1) + 12 - (alt - ust));
			detayKaydir = Math.max(0, Math.min(max, detayKaydir + adim));
		}
		return true;
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
