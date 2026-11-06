package net.cevherclient.client.screen;

import net.cevherclient.client.config.ClientConfig;
import net.cevherclient.client.module.Module;
import net.cevherclient.client.sao.Sao;
import net.cevherclient.client.sao.SaoGlyph;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

/**
 * Cevherclient mod menüsü, Sword Art Online biçiminde: solda dikey bir daire düğme sütunu, seçilen düğmenin
 * yanında açılan gri pencere. Düğmeler tek tek belirir, üzerine gelince turuncu yanar ve dalga yayar; pencere
 * önce yatayda, sonra dikeyde açılır, içindeki hücreler satır satır büyür.
 *
 * <p>İçerik "tasarım biriminde" (ekranın ortası 0,0) çizilir ve ekrana sığacak şekilde küçültülür.
 */
public final class SaoMenuScreen extends Screen {
	// Pencere (tasarım birimi).
	static final float WX = -170;
	static final float WY = -140;
	static final float WW = 440;
	static final float WH = 280;
	static final float HEADER = 26;

	private static final float DESIGN_W = 560;
	private static final float DESIGN_H = 300;
	private static final float COL_X = -222;
	private static final float BUTTON = 40;
	private static final float PITCH = 52;
	private static final long BLUR_DELAY = 110;
	private static final long CLOSE_MS = 170;
	private static final long RIPPLE_MS = 300;

	private enum Entry {
		MODULES("Modüller", SaoGlyph.MODULES),
		COSMETICS("Kozmetikler", SaoGlyph.COSMETICS),
		EDITOR("HUD Düzenleyici", SaoGlyph.EDITOR),
		CLOSE("Kapat", SaoGlyph.CLOSE);

		final String label;
		final SaoGlyph glyph;

		Entry(String label, SaoGlyph glyph) {
			this.label = label;
			this.glyph = glyph;
		}
	}

	private final Screen parent;
	private final long openedAt = Util.getMillis();
	private final float[] hover = new float[Entry.values().length];
	private final long[] hoverSince = {-1, -1, -1, -1};
	private long lastFrame = openedAt;

	private ModulesPage modulesPage;
	private CosmeticsPage cosmeticsPage;
	private Entry openEntry;
	private SaoPage page;
	private long pageOpenedAt;

	private boolean closing;
	private long closingAt;
	private float scale = 1f;

	public SaoMenuScreen(Screen parent) {
		super(Component.literal("Cevherclient"));
		this.parent = parent;
	}

	/** Menüyü Modüller penceresi açık ve {@code module} seçili olarak açar (HUD düzenleyiciden sağ tıkla). */
	public SaoMenuScreen(Screen parent, Module module) {
		this(parent);
		modulesPage = new ModulesPage(this, module);
		open(Entry.MODULES, false);
	}

	@Override
	protected void init() {
		play(1.7f, 0.45f);
	}

	// --- Dönüşümler (tasarım birimi <-> ekran) ---

	Minecraft minecraft() {
		return minecraft;
	}

	float scale() {
		return scale;
	}

	float screenX(float designX) {
		return width / 2f + designX * scale;
	}

	float screenY(float designY) {
		return height / 2f + designY * scale;
	}

	private float toDesignX(double screenX) {
		return (float) ((screenX - width / 2.0) / scale);
	}

	private float toDesignY(double screenY) {
		return (float) ((screenY - height / 2.0) / scale);
	}

	private void updateScale() {
		float fit = Math.min((width - 24f) / DESIGN_W, (height - 24f) / DESIGN_H);
		scale = Math.max(0.4f, Math.min(1.25f, fit));
	}

	private static float buttonY(int index) {
		return (index - (Entry.values().length - 1) / 2f) * PITCH;
	}

	// --- Durum ---

	private void open(Entry entry, boolean sound) {
		openEntry = entry;
		pageOpenedAt = Util.getMillis();
		if (entry == Entry.MODULES) {
			if (modulesPage == null) {
				modulesPage = new ModulesPage(this, null);
			}
			page = modulesPage;
		} else {
			if (cosmeticsPage == null) {
				cosmeticsPage = new CosmeticsPage(this);
			}
			page = cosmeticsPage;
		}
		if (sound) {
			play(1.3f, 0.35f);
		}
	}

	void openHudEditor() {
		minecraft.setScreen(new HudEditorScreen(this));
	}

	private void startClose() {
		if (!closing) {
			closing = true;
			closingAt = Util.getMillis();
			play(1.0f, 0.3f);
		}
	}

	private void play(float pitch, float volume) {
		if (minecraft != null) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, pitch, volume));
		}
	}

	private void activate(Entry entry) {
		switch (entry) {
			case MODULES, COSMETICS -> {
				if (openEntry == entry) {
					page = null;
					openEntry = null;
				} else {
					open(entry, true);
				}
			}
			case EDITOR -> openHudEditor();
			case CLOSE -> startClose();
		}
	}

	// --- Çizim ---

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// Açılışta bulanıklık bir an sonra gelir; kapanırken hemen kalkar.
		if (!closing && Util.getMillis() - openedAt >= BLUR_DELAY) {
			super.renderBackground(graphics, mouseX, mouseY, partialTick);
		}
	}

	@Override
	public void render(GuiGraphics g, int screenMouseX, int screenMouseY, float partialTick) {
		super.render(g, screenMouseX, screenMouseY, partialTick);
		updateScale();
		long now = Util.getMillis();
		float dt = Math.min(0.1f, (now - lastFrame) / 1000f);
		lastFrame = now;
		long t = now - openedAt;

		float closeP = closing ? Sao.progress(now, closingAt, CLOSE_MS) : 0f;
		if (closing && closeP >= 1f) {
			minecraft.setScreen(parent);
			return;
		}
		float base = (1f - closeP) * Sao.easeOut(Sao.progress(t, 0, 120));
		Sao.alpha = base;

		float mx = toDesignX(screenMouseX);
		float my = toDesignY(screenMouseY);
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(width / 2f - closeP * 18f * scale, height / 2f);
		pose.scale(scale, scale);

		int hoveredButton = closing ? -1 : buttonAt(mx, my);
		renderColumn(g, t, now, dt, hoveredButton, base);

		if (page != null) {
			renderWindow(g, mx, my, now, dt);
		}
		if (hoveredButton >= 0 && Sao.progress(t, 40 + hoveredButton * 70L, 240) >= 1) {
			Sao.label(g, Entry.values()[hoveredButton].label, COL_X + BUTTON / 2 + 10, buttonY(hoveredButton) - 6);
		}
		pose.popMatrix();
		Sao.alpha = 1f;
	}

	private void renderColumn(GuiGraphics g, long t, long now, float dt, int hovered, float base) {
		Entry[] entries = Entry.values();
		for (int i = 0; i < entries.length; i++) {
			float a = Sao.easeOut(Sao.progress(t, 40 + i * 70L, 240));
			if (a <= 0) {
				continue;
			}
			float target = i == hovered ? 1f : 0f;
			hover[i] += (target - hover[i]) * (1f - (float) Math.exp(-dt * 18f));
			if (target == 1f && hoverSince[i] < 0) {
				hoverSince[i] = now;
			} else if (target == 0f && hover[i] < 0.05f) {
				hoverSince[i] = -1;
			}
			boolean selected = entries[i] == openEntry;
			float f = Math.max(hover[i], selected ? 1f : 0f);

			float cx = COL_X - (1f - a) * 16f;
			float cy = buttonY(i);
			Sao.alpha = base * a;

			if (selected && page != null) {
				float p = Sao.easeOut(Sao.progress(now, pageOpenedAt, 140));
				float x0 = cx + BUTTON / 2 + 3;
				Sao.rect(g, x0, cy - 0.5f, (WX - x0) * p, 1, Sao.ACCENT);
			}
			if (hoverSince[i] >= 0) {
				long e = now - hoverSince[i];
				if (e < RIPPLE_MS) {
					float fadeOut = 1f - e / (float) RIPPLE_MS;
					Sao.ring(g, cx, cy, BUTTON + 8, 1.2f, Sao.withAlpha(Sao.ACCENT_LIGHT, 0.7f * fadeOut));
					if (e > 70) {
						Sao.ring(g, cx, cy, BUTTON + 16, 1.0f, Sao.withAlpha(Sao.ACCENT_LIGHT, 0.45f * fadeOut));
					}
				}
			}
			Sao.disk(g, cx, cy, BUTTON, Sao.mix(Sao.DISC, Sao.ACCENT, f));
			Sao.ring(g, cx, cy, BUTTON + 4, 1.2f, Sao.mix(0x99FFFFFF, Sao.ACCENT_LIGHT, f));
			entries[i].glyph.drawCentered(g, cx, cy, 20, Sao.mix(Sao.ICON, Sao.WHITE, f));
		}
		Sao.alpha = base;
	}

	private void renderWindow(GuiGraphics g, float mx, float my, long now, float dt) {
		long tp = now - pageOpenedAt;
		Sao.window(g, WX, WY, WW, WH, HEADER, tp);
		float headerText = Sao.progress(tp, 60, 100);
		float old = Sao.alpha;
		Sao.alpha = old * headerText;
		page.glyph().draw(g, WX + 10, WY + 7, 12, Sao.TITLE);
		Sao.text(g, page.title(), WX + 28, WY + 9, 7f, Sao.TITLE);
		Sao.alpha = old;
		if (Sao.progress(tp, 100, 120) > 0.2f) {
			page.render(g, mx, my, tp, dt);
		}
	}

	private int buttonAt(float mx, float my) {
		for (int i = 0; i < Entry.values().length; i++) {
			float dx = mx - COL_X;
			float dy = my - buttonY(i);
			if (dx * dx + dy * dy <= (BUTTON / 2 + 2) * (BUTTON / 2 + 2)) {
				return i;
			}
		}
		return -1;
	}

	// --- Girdi ---

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (closing) {
			return true;
		}
		float mx = toDesignX(event.x());
		float my = toDesignY(event.y());
		int button = buttonAt(mx, my);
		if (button >= 0 && event.button() == 0) {
			activate(Entry.values()[button]);
			return true;
		}
		if (page != null && Sao.inside(mx, my, WX, WY, WW, WH) && page.click(mx, my, event.button())) {
			return true;
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (page != null) {
			page.release(toDesignX(event.x()), toDesignY(event.y()), event.button());
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (page != null) {
			page.drag(toDesignX(event.x()), toDesignY(event.y()), event.button());
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
		if (page != null) {
			page.scroll(toDesignX(x), toDesignY(y), vertical);
		}
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return page != null && page.charTyped(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (page != null && page.keyPressed(event)) {
			return true;
		}
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
			if (page != null) {
				page = null;
				openEntry = null;
			} else {
				startClose();
			}
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		startClose();
	}

	@Override
	public void removed() {
		ClientConfig.saveIfDirty();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}
}
