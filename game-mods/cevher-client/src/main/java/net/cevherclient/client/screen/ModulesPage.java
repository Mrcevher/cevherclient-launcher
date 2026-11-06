package net.cevherclient.client.screen;

import static net.cevherclient.client.screen.SaoMenuScreen.HEADER;
import static net.cevherclient.client.screen.SaoMenuScreen.WH;
import static net.cevherclient.client.screen.SaoMenuScreen.WW;
import static net.cevherclient.client.screen.SaoMenuScreen.WX;
import static net.cevherclient.client.screen.SaoMenuScreen.WY;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.cevherclient.client.config.ClientConfig;
import net.cevherclient.client.module.HudModule;
import net.cevherclient.client.module.Module;
import net.cevherclient.client.module.ModuleManager;
import net.cevherclient.client.module.Option;
import net.cevherclient.client.sao.Sao;
import net.cevherclient.client.sao.SaoGlyph;
import net.cevherclient.client.sao.SaoModuleInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

/**
 * Modüller penceresi: solda sekmeli, aramalı ikon ızgarası, sağda seçili modülün ayrıntı paneli
 * (açıklama, aç/kapa düğmesi ve ayarları). Envanter ızgarasının animasyonu: hücreler satır satır büyür.
 */
final class ModulesPage extends SaoPage {
	private static final Locale TR = Locale.forLanguageTag("tr");
	private static final float CELL = 38;
	private static final float GAP = 5;
	private static final int COLS = 5;
	private static final float GX = WX + 12;
	private static final float TAB_Y = WY + HEADER + 9;
	private static final float GY = WY + HEADER + 28;
	private static final float VIEW_TOP = GY - 3;
	private static final float VIEW_BOTTOM = WY + WH - 8;
	private static final float GRID_W = COLS * CELL + (COLS - 1) * GAP;
	private static final float DIV_X = GX + GRID_W + 9;
	private static final float DX = DIV_X + 10;
	private static final float DW = WX + WW - 12 - DX;
	private static final float OPTION_ROW = 19;
	private static final float TRACK_W = 78;

	private static final float SEARCH_W = 120;
	private static final float SEARCH_X = WX + WW - 12 - SEARCH_W;
	private static final float SEARCH_Y = WY + 6;

	private final List<String> tabs = new ArrayList<>();
	private int tab;
	private String query = "";
	private float scroll;
	private float scrollTarget;
	private Module selected;
	private Option.Range dragging;
	/** Sağdaki ayar listesinin kaydırması (çok ayarlı modüllerde). */
	private float detailScroll;
	private float detailScrollTarget;

	ModulesPage(SaoMenuScreen host, Module selected) {
		super(host);
		this.selected = selected;
		tabs.add("Tümü");
		for (Module.Category category : Module.Category.values()) {
			tabs.add(category.label);
		}
	}

	@Override
	String title() {
		return "MODÜLLER";
	}

	@Override
	SaoGlyph glyph() {
		return SaoGlyph.MODULES;
	}

	private List<Module> visible() {
		List<Module> result = new ArrayList<>();
		String needle = query.toLowerCase(TR);
		for (Module module : ModuleManager.all()) {
			boolean inTab = tab == 0 || module.category == Module.Category.values()[tab - 1];
			if (inTab && (needle.isEmpty() || module.name.toLowerCase(TR).contains(needle))) {
				result.add(module);
			}
		}
		return result;
	}

	private float maxScroll(int count) {
		int rows = (count + COLS - 1) / COLS;
		return Math.max(0f, rows * (CELL + GAP) - GAP + 6 - (VIEW_BOTTOM - VIEW_TOP));
	}

	private static float cellX(int col) {
		return GX + col * (CELL + GAP);
	}

	private float cellY(int row) {
		return GY + row * (CELL + GAP) - scroll;
	}

	private float tabX(int index) {
		float x = GX;
		for (int i = 0; i < index; i++) {
			x += Sao.textWidth(tabs.get(i), 6.5f) + 18;
		}
		return x;
	}

	// --- Çizim ---

	@Override
	void render(GuiGraphics g, float mx, float my, long t, float dt) {
		renderSearch(g, t);
		renderTabs(g, mx, my, t);
		Sao.rect(g, DIV_X, VIEW_TOP, 1, VIEW_BOTTOM - VIEW_TOP, 0x55FFFFFF);

		List<Module> modules = visible();
		scrollTarget = Math.max(0f, Math.min(scrollTarget, maxScroll(modules.size())));
		scroll += (scrollTarget - scroll) * (1f - (float) Math.exp(-dt * 18f));

		Module hoveredModule = null;
		if (modules.isEmpty()) {
			Sao.centered(g, "Hiçbir modül bulunamadı.", GX + GRID_W / 2f, GY + 40, 6.5f, Sao.MUTED);
		} else {
			g.enableScissor(Math.round(GX - 3), Math.round(VIEW_TOP), Math.round(GX + GRID_W + 3), Math.round(VIEW_BOTTOM));
			for (int i = 0; i < modules.size(); i++) {
				Module module = modules.get(i);
				int row = i / COLS;
				int col = i % COLS;
				float x = cellX(col);
				float y = cellY(row);
				float p = Sao.progress(t, 140 + row * 45L + col * 12L, 90);
				if (p <= 0) {
					continue;
				}
				boolean hover = my >= VIEW_TOP && my < VIEW_BOTTOM && Sao.inside(mx, my, x, y, CELL, CELL);
				if (hover && p >= 1) {
					hoveredModule = module;
				}
				cell(g, module, x, y, p, hover, dt);
			}
			g.disableScissor();
		}
		renderScrollbar(g, modules.size());
		renderDetail(g, mx, my, t, dt);

		if (hoveredModule != null) {
			Sao.label(g, hoveredModule.name, mx + 8, my + 10);
		}
	}

	private void cell(GuiGraphics g, Module module, float x, float y, float p, boolean hover, float dt) {
		float h = Math.max(1, Math.round(CELL * Sao.easeOut(p)));
		Sao.rect(g, x, y, CELL, h, hover ? Sao.CELL_HOVER : Sao.CELL_BG);
		if (p < 1) {
			return;
		}
		module.toggleAnim += ((module.isEnabled() ? 1f : 0f) - module.toggleAnim) * (1f - (float) Math.exp(-dt * 20f));
		if (module == selected) {
			Sao.outline(g, x - 1, y - 1, CELL + 2, CELL + 2, Sao.ACCENT);
			Sao.outline(g, x, y, CELL, CELL, Sao.ACCENT);
		}
		int tint = Sao.mix(Sao.ICON_DIM, Sao.ICON, module.toggleAnim);
		SaoModuleInfo.glyph(module).drawCentered(g, x + CELL / 2f, y + CELL / 2f, 20, tint);
		if (module.toggleAnim > 0.05f) {
			Sao.disk(g, x + CELL - 7, y + 7, 6, Sao.withAlpha(Sao.GREEN, module.toggleAnim));
		}
	}

	private void renderSearch(GuiGraphics g, long t) {
		float a = Sao.progress(t, 150, 100);
		float old = Sao.alpha;
		Sao.alpha = old * a;
		Sao.rect(g, SEARCH_X, SEARCH_Y, SEARCH_W, 14, Sao.FIELD_BG);
		SaoGlyph.SEARCH.draw(g, SEARCH_X + 4, SEARCH_Y + 3, 8, Sao.MUTED);
		boolean cursor = (Util.getMillis() / 500) % 2 == 0;
		if (query.isEmpty()) {
			Sao.text(g, "Ara", SEARCH_X + 17, SEARCH_Y + 4, 5.8f, Sao.MUTED);
			if (cursor) {
				Sao.rect(g, SEARCH_X + 14, SEARCH_Y + 3, 1, 8, Sao.TEXT);
			}
		} else {
			String shown = Sao.fit(query, SEARCH_W - 28, 5.8f);
			float end = Sao.textWidth(shown, 5.8f);
			Sao.text(g, shown, SEARCH_X + 17, SEARCH_Y + 4, 5.8f, Sao.TEXT);
			if (cursor) {
				Sao.rect(g, SEARCH_X + 18 + end, SEARCH_Y + 3, 1, 8, Sao.TEXT);
			}
		}
		Sao.alpha = old;
	}

	private void renderTabs(GuiGraphics g, float mx, float my, long t) {
		float a = Sao.progress(t, 120, 100);
		float old = Sao.alpha;
		Sao.alpha = old * a;
		for (int i = 0; i < tabs.size(); i++) {
			float x = tabX(i);
			float w = Sao.textWidth(tabs.get(i), 6.5f);
			boolean hover = Sao.inside(mx, my, x - 6, TAB_Y - 4, w + 12, 16);
			Sao.text(g, tabs.get(i), x, TAB_Y, 6.5f, i == tab ? Sao.TITLE : hover ? Sao.TEXT : Sao.MUTED);
			if (i == tab) {
				Sao.rect(g, x, TAB_Y + 10, w, 1.5f, Sao.ACCENT);
			}
		}
		Sao.alpha = old;
	}

	private void renderScrollbar(GuiGraphics g, int count) {
		float max = maxScroll(count);
		if (max <= 0f) {
			return;
		}
		float trackH = VIEW_BOTTOM - VIEW_TOP - 6;
		float thumbH = Math.max(18, trackH * trackH / (trackH + max));
		float thumbY = VIEW_TOP + 3 + (trackH - thumbH) * (scroll / max);
		Sao.rect(g, DIV_X - 5, thumbY, 2, thumbH, 0x99FFFFFF);
	}

	// --- Ayrıntı paneli ---

	private List<String> descriptionLines() {
		List<String> lines = new ArrayList<>();
		String text = SaoModuleInfo.description(selected);
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (Sao.textWidth(candidate, 5.6f) > DW && !line.isEmpty()) {
				lines.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(candidate);
			}
		}
		if (!line.isEmpty()) {
			lines.add(line.toString());
		}
		return lines;
	}

	/** Ayar listesinin görünen alanının üstü; ayarlar bu alanda kaydırılır. */
	private float optionsTop() {
		return GY + 40 + descriptionLines().size() * 9 + 8;
	}

	private float optionY(int index) {
		return optionsTop() - detailScroll + index * OPTION_ROW;
	}

	private float editButtonY() {
		return optionY(selected.options().size()) + 4;
	}

	private float maxDetailScroll() {
		float content = selected.options().size() * OPTION_ROW + (selected instanceof HudModule ? 24 : 0);
		return Math.max(0f, content - (VIEW_BOTTOM - optionsTop()));
	}

	/** Ayar satırı görünen alanda mı (kaydırılınca üstte ya da altta kalanlara tıklanmaz). */
	private boolean inDetailView(float my) {
		return my >= optionsTop() - 3 && my < VIEW_BOTTOM;
	}

	private void renderDetail(GuiGraphics g, float mx, float my, long t, float dt) {
		if (Sao.progress(t, 200, 100) <= 0) {
			return;
		}
		float old = Sao.alpha;
		Sao.alpha = old * Sao.progress(t, 200, 100);
		if (selected == null) {
			Sao.centered(g, "Bir modül seç", DX + DW / 2f, GY + 70, 6.5f, Sao.MUTED);
			Sao.alpha = old;
			return;
		}
		selected.toggleAnim += ((selected.isEnabled() ? 1f : 0f) - selected.toggleAnim) * (1f - (float) Math.exp(-dt * 20f));
		Sao.disk(g, DX + 16, GY + 16, 32, 0xFFF0F0F0);
		SaoModuleInfo.glyph(selected).drawCentered(g, DX + 16, GY + 16, 18, Sao.ICON);
		Sao.text(g, Sao.fit(selected.name, DW - 40 - 60, 7.5f), DX + 40, GY + 5, 7.5f, Sao.TITLE);
		Sao.text(g, selected.category.label, DX + 40, GY + 19, 5.5f, Sao.MUTED);

		boolean pillHover = Sao.inside(mx, my, DX + DW - 56, GY + 8, 56, 16);
		float pillX = DX + DW - 56;
		Sao.rect(g, pillX, GY + 8, 56, 16, Sao.mix(0xFFB4B4B4, Sao.GREEN, selected.toggleAnim));
		if (pillHover) {
			Sao.outline(g, pillX - 1, GY + 7, 58, 18, Sao.WHITE);
		}
		Sao.centered(g, selected.isEnabled() ? "AÇIK" : "KAPALI", pillX + 28, GY + 13, 6f, Sao.WHITE);

		float y = GY + 40;
		for (String line : descriptionLines()) {
			Sao.text(g, line, DX, y, 5.6f, Sao.TEXT);
			y += 9;
		}

		float max = maxDetailScroll();
		detailScrollTarget = Math.max(0f, Math.min(detailScrollTarget, max));
		detailScroll += (detailScrollTarget - detailScroll) * (1f - (float) Math.exp(-dt * 18f));
		// Görünen alanın dışındaki satırlar hem çizilmez hem de üzerine gelinmiş sayılmaz.
		float hoverY = inDetailView(my) ? my : Float.NaN;
		g.enableScissor(Math.round(DX - 4), Math.round(optionsTop() - 3), Math.round(DX + DW + 4), Math.round(VIEW_BOTTOM));
		for (int i = 0; i < selected.options().size(); i++) {
			renderOption(g, selected.options().get(i), optionY(i), mx, hoverY);
		}
		if (selected instanceof HudModule) {
			float by = editButtonY();
			boolean hover = Sao.inside(mx, hoverY, DX, by, 104, 16);
			Sao.rect(g, DX, by, 104, 16, hover ? Sao.CELL_HOVER : Sao.FIELD_BG);
			SaoGlyph.EDITOR.drawCentered(g, DX + 11, by + 8, 9, Sao.ICON);
			Sao.text(g, "HUD'da düzenle", DX + 20, by + 5, 5.8f, Sao.TEXT);
		}
		g.disableScissor();
		if (max > 0f) {
			float trackTop = optionsTop();
			float trackH = VIEW_BOTTOM - trackTop - 3;
			float thumbH = Math.max(14, trackH * trackH / (trackH + max));
			float thumbY = trackTop + (trackH - thumbH) * (detailScroll / max);
			Sao.rect(g, DX + DW + 6, thumbY, 2, thumbH, 0x99FFFFFF);
		}
		Sao.alpha = old;
	}

	private void renderOption(GuiGraphics g, Option option, float y, float mx, float my) {
		Sao.text(g, option.label, DX, y + 3, 6f, Sao.TEXT);
		float right = DX + DW;
		switch (option) {
			case Option.Bool bool -> Sao.toggle(g, right - 26, y, bool.value ? 1f : 0f,
				Sao.inside(mx, my, right - 28, y - 2, 30, 16));
			case Option.Choice choice -> {
				boolean hover = Sao.inside(mx, my, right - 86, y - 2, 86, 16);
				Sao.rect(g, right - 86, y - 1, 86, 14, hover ? Sao.CELL_HOVER : Sao.FIELD_BG);
				Sao.centered(g, choice.name(), right - 43, y + 2, 5.8f, Sao.TEXT);
				Sao.text(g, "<", right - 82, y + 2, 5.8f, Sao.MUTED);
				Sao.text(g, ">", right - 8, y + 2, 5.8f, Sao.MUTED);
			}
			case Option.Range range -> {
				float trackX = right - TRACK_W;
				Sao.rect(g, trackX, y + 4, TRACK_W, 2, 0x66FFFFFF);
				float filled = TRACK_W * range.fraction();
				Sao.rect(g, trackX, y + 4, filled, 2, Sao.ACCENT);
				Sao.rect(g, trackX + filled - 2, y, 4, 10, Sao.WHITE);
				String value = String.format(Locale.ROOT, "%.1f", range.value);
				Sao.text(g, value, trackX - 8 - Sao.textWidth(value, 5.8f), y + 2, 5.8f, Sao.MUTED);
			}
		}
	}

	// --- Girdi ---

	@Override
	boolean click(float mx, float my, int button) {
		if (button == 0 && clickTabs(mx, my)) {
			return true;
		}
		if (my >= VIEW_TOP && my < VIEW_BOTTOM && mx < DIV_X && button == 0) {
			List<Module> modules = visible();
			for (int i = 0; i < modules.size(); i++) {
				if (Sao.inside(mx, my, cellX(i % COLS), cellY(i / COLS), CELL, CELL)) {
					if (selected != modules.get(i)) {
						selected = modules.get(i);
						detailScroll = 0f;
						detailScrollTarget = 0f;
					}
					return true;
				}
			}
			return true;
		}
		if (selected == null || mx < DIV_X) {
			return false;
		}
		if (button == 0 && Sao.inside(mx, my, DX + DW - 56, GY + 8, 56, 16)) {
			selected.toggle();
			return true;
		}
		if (!inDetailView(my)) {
			return false;
		}
		for (int i = 0; i < selected.options().size(); i++) {
			if (clickOption(selected.options().get(i), optionY(i), mx, my, button)) {
				return true;
			}
		}
		if (selected instanceof HudModule && button == 0 && Sao.inside(mx, my, DX, editButtonY(), 104, 16)) {
			host.openHudEditor();
			return true;
		}
		return false;
	}

	private boolean clickTabs(float mx, float my) {
		for (int i = 0; i < tabs.size(); i++) {
			float w = Sao.textWidth(tabs.get(i), 6.5f);
			if (Sao.inside(mx, my, tabX(i) - 6, TAB_Y - 4, w + 12, 16)) {
				tab = i;
				scroll = 0f;
				scrollTarget = 0f;
				return true;
			}
		}
		return false;
	}

	private boolean clickOption(Option option, float y, float mx, float my, int button) {
		float right = DX + DW;
		switch (option) {
			case Option.Bool bool -> {
				if (button == 0 && Sao.inside(mx, my, right - 28, y - 2, 30, 16)) {
					bool.value = !bool.value;
					ClientConfig.markDirty();
					return true;
				}
			}
			case Option.Choice choice -> {
				if ((button == 0 || button == 1) && Sao.inside(mx, my, right - 86, y - 2, 86, 16)) {
					choice.cycle(button == 1 ? -1 : 1);
					ClientConfig.markDirty();
					return true;
				}
			}
			case Option.Range range -> {
				if (button == 0 && Sao.inside(mx, my, right - TRACK_W - 5, y - 3, TRACK_W + 10, 16)) {
					dragging = range;
					setFromMouse(range, mx);
					return true;
				}
			}
		}
		return false;
	}

	private void setFromMouse(Option.Range range, float mx) {
		float t = (mx - (DX + DW - TRACK_W)) / TRACK_W;
		range.set(range.min + Math.max(0f, Math.min(1f, t)) * (range.max - range.min));
		ClientConfig.markDirty();
	}

	@Override
	void drag(float mx, float my, int button) {
		if (dragging != null) {
			setFromMouse(dragging, mx);
		}
	}

	@Override
	void release(float mx, float my, int button) {
		dragging = null;
	}

	@Override
	boolean scroll(float mx, float my, double amount) {
		if (mx < DIV_X) {
			scrollTarget = Math.max(0f, Math.min(maxScroll(visible().size()), scrollTarget - (float) amount * 30f));
			return true;
		}
		if (selected != null) {
			detailScrollTarget = Math.max(0f, Math.min(maxDetailScroll(), detailScrollTarget - (float) amount * 30f));
			return true;
		}
		return false;
	}

	@Override
	boolean charTyped(CharacterEvent event) {
		if (event.isAllowedChatCharacter() && query.length() < 24) {
			query += event.codepointAsString();
			scrollTarget = 0f;
			return true;
		}
		return false;
	}

	@Override
	boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
			query = query.substring(0, query.length() - 1);
			scrollTarget = 0f;
			return true;
		}
		return false;
	}
}
