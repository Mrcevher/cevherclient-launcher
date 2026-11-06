package net.cevherclient.client.screen;

import java.util.List;
import java.util.Locale;
import net.cevherclient.client.config.ClientConfig;
import net.cevherclient.client.module.HudModule;
import net.cevherclient.client.module.ModuleManager;
import net.cevherclient.client.sao.Sao;
import net.cevherclient.client.sao.SaoGlyph;
import net.cevherclient.client.ui.HudRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * HUD düzenleyici: açık HUD modüllerini gerçek yerlerinde gösterir; sürükleyerek taşınır,
 * tekerlek ya da +/- ile boyutlandırılır, oklarla piksel piksel itilir, sağ tık ayarlarını açar.
 * Ekran kenarlarına ve ortasına yaklaşınca konum yapışır.
 */
public final class HudEditorScreen extends Screen {
	private static final int SNAP = 4;
	private static final float BACK = 28;

	private final Screen parent;
	private HudModule selected;
	private boolean dragging;
	private double grabX;
	private double grabY;
	private boolean snapCenterX;
	private boolean snapCenterY;

	public HudEditorScreen(Screen parent) {
		super(Component.literal("HUD Düzenleyici"));
		this.parent = parent;
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0x38000000);
	}

	private float backX() {
		return 10 + BACK / 2;
	}

	private float backY() {
		return height - 10 - BACK / 2;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		Sao.alpha = 1f;

		if (dragging) {
			if (snapCenterX) {
				Sao.rect(g, width / 2f, 0, 1, height, 0x88FFFFFF);
			}
			if (snapCenterY) {
				Sao.rect(g, 0, height / 2f, width, 1, 0x88FFFFFF);
			}
		}

		List<HudModule> modules = ModuleManager.enabledHud();
		HudModule hovered = hit(mouseX, mouseY, modules);
		for (HudModule module : modules) {
			HudRenderer.renderOne(g, minecraft, module, true);
			HudRenderer.Bounds b = HudRenderer.bounds(minecraft, module, true);
			boolean active = module == selected || module == hovered;
			Sao.outline(g, b.x() - 2, b.y() - 2, b.w() + 4, b.h() + 4, active ? Sao.ACCENT : 0xAAFFFFFF);
		}

		if (selected != null) {
			HudRenderer.Bounds b = HudRenderer.bounds(minecraft, selected, true);
			String info = selected.name + "  " + b.x() + ", " + b.y() + "  " + String.format(Locale.ROOT, "%.1fx", selected.scale.value);
			float y = b.y() > 18 ? b.y() - 17 : b.y() + b.h() + 5;
			float w = Sao.textWidth(info, 5.8f) + 10;
			float x = Math.max(2, Math.min(b.x(), width - w - 2));
			Sao.rect(g, x, y, w, 12, Sao.LABEL_BG);
			Sao.text(g, info, x + 5, y + 3, 5.8f, Sao.TITLE);
		}

		if (modules.isEmpty()) {
			hint(g, "Açık HUD modülü yok. Modüller penceresinden bir HUD modülü aç.", height / 2f - 6);
		}
		hint(g, "Sürükle = taşı   |   Tekerlek veya + / - = boyutlandır", 8);
		hint(g, "Oklar = it   |   Sağ tık = ayarlar   |   ESC = geri", 24);

		boolean backHover = Math.hypot(mouseX - backX(), mouseY - backY()) <= BACK / 2;
		Sao.disk(g, backX(), backY(), BACK, backHover ? Sao.ACCENT : Sao.DISC);
		SaoGlyph.CLOSE.drawCentered(g, backX(), backY(), 12, backHover ? Sao.WHITE : Sao.ICON);
	}

	/** Üstte ortalanmış, açık gri etiket zeminli ipucu satırı. */
	private void hint(GuiGraphics g, String text, float y) {
		float w = Sao.textWidth(text, 6f) + 16;
		float x = (width - w) / 2f;
		Sao.rect(g, x, y, w, 13, Sao.LABEL_BG);
		Sao.text(g, text, x + 8, y + 3.5f, 6f, Sao.TITLE);
	}

	private HudModule hit(double mx, double my, List<HudModule> modules) {
		for (int i = modules.size() - 1; i >= 0; i--) {
			if (HudRenderer.bounds(minecraft, modules.get(i), true).contains(mx, my)) {
				return modules.get(i);
			}
		}
		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (Math.hypot(event.x() - backX(), event.y() - backY()) <= BACK / 2) {
			onClose();
			return true;
		}
		HudModule module = hit(event.x(), event.y(), ModuleManager.enabledHud());
		selected = module;
		if (module == null) {
			return true;
		}
		if (event.button() == 1) {
			minecraft.setScreen(new SaoMenuScreen(this, module));
			return true;
		}
		if (event.button() == 0) {
			HudRenderer.Bounds b = HudRenderer.bounds(minecraft, module, true);
			dragging = true;
			grabX = event.x() - b.x();
			grabY = event.y() - b.y();
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (!dragging || selected == null) {
			return false;
		}
		HudRenderer.Bounds b = HudRenderer.bounds(minecraft, selected, true);
		int x = snap((int) Math.round(event.x() - grabX), b.w(), width);
		int y = snap((int) Math.round(event.y() - grabY), b.h(), height);
		snapCenterX = x + b.w() / 2 == width / 2;
		snapCenterY = y + b.h() / 2 == height / 2;
		selected.setPosition(x / (float) width, y / (float) height);
		ClientConfig.markDirty();
		return true;
	}

	/** Kenarlara ve ekran ortasına {@link #SNAP} piksel kala konumu yapıştırır. */
	private static int snap(int pos, int size, int screen) {
		if (Math.abs(pos) <= SNAP) {
			return 0;
		}
		if (Math.abs(pos + size - screen) <= SNAP) {
			return screen - size;
		}
		int center = screen / 2 - size / 2;
		if (Math.abs(pos - center) <= SNAP) {
			return center;
		}
		return Math.max(0, Math.min(pos, screen - size));
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = false;
		snapCenterX = false;
		snapCenterY = false;
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
		HudModule target = hit(x, y, ModuleManager.enabledHud());
		if (target == null) {
			target = selected;
		}
		if (target != null) {
			resize(target, vertical > 0 ? 0.1f : -0.1f);
		}
		return true;
	}

	private static void resize(HudModule module, float delta) {
		module.scale.set(module.scale.value + delta);
		ClientConfig.markDirty();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (selected != null) {
			int step = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 ? 5 : 1;
			switch (event.key()) {
				case GLFW.GLFW_KEY_LEFT -> nudge(-step, 0);
				case GLFW.GLFW_KEY_RIGHT -> nudge(step, 0);
				case GLFW.GLFW_KEY_UP -> nudge(0, -step);
				case GLFW.GLFW_KEY_DOWN -> nudge(0, step);
				case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> resize(selected, 0.1f);
				case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> resize(selected, -0.1f);
				default -> {
					return super.keyPressed(event);
				}
			}
			return true;
		}
		return super.keyPressed(event);
	}

	private void nudge(int dx, int dy) {
		HudRenderer.Bounds b = HudRenderer.bounds(minecraft, selected, true);
		selected.setPosition((b.x() + dx) / (float) width, (b.y() + dy) / (float) height);
		ClientConfig.markDirty();
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public void removed() {
		ClientConfig.saveIfDirty();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
