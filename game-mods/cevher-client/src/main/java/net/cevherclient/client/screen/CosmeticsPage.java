package net.cevherclient.client.screen;

import static net.cevherclient.client.screen.SaoMenuScreen.HEADER;
import static net.cevherclient.client.screen.SaoMenuScreen.WH;
import static net.cevherclient.client.screen.SaoMenuScreen.WW;
import static net.cevherclient.client.screen.SaoMenuScreen.WX;
import static net.cevherclient.client.screen.SaoMenuScreen.WY;

import java.util.List;
import net.cevherclient.client.cosmetic.Cosmetic;
import net.cevherclient.client.cosmetic.CosmeticRegistry;
import net.cevherclient.client.cosmetic.CosmeticSelection;
import net.cevherclient.client.sao.Sao;
import net.cevherclient.client.sao.SaoGlyph;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Kozmetikler penceresi: solda Baş / Sırt / Pelerin sekmeli, kaydırılabilir ızgara, sağda fareyi izleyen canlı
 * önizleme ve altta diğer oyuncuların kozmetiklerini görmeyi açıp kapatan anahtar. Izgaradaki ilk hücre
 * "kozmetik yok" demektir.
 */
final class CosmeticsPage extends SaoPage {
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
	private static final float FOOTER_Y = WY + WH - 26;
	private static final String SHOW_OTHERS = "Diğer oyuncuların kozmetikleri";

	private Cosmetic.Slot slot = Cosmetic.Slot.HEAD;
	private List<Cosmetic> items = CosmeticRegistry.forSlot(slot);
	private float scroll;
	private float scrollTarget;
	private float showAnim = CosmeticSelection.showOthers() ? 1f : 0f;

	CosmeticsPage(SaoMenuScreen host) {
		super(host);
	}

	@Override
	String title() {
		return "KOZMETİKLER";
	}

	@Override
	SaoGlyph glyph() {
		return SaoGlyph.COSMETICS;
	}

	/** İndeks 0 "yok", sonrakiler o sekmenin kozmetikleri. */
	private Cosmetic itemAt(int index) {
		return index == 0 ? null : items.get(index - 1);
	}

	private static float cellX(int index) {
		return GX + (index % COLS) * (CELL + GAP);
	}

	private float cellY(int index) {
		return GY + (index / COLS) * (CELL + GAP) - scroll;
	}

	private float maxScroll() {
		int rows = (items.size() + 1 + COLS - 1) / COLS;
		return Math.max(0f, rows * (CELL + GAP) - GAP + 6 - (VIEW_BOTTOM - VIEW_TOP));
	}

	private static float tabX(int index) {
		float x = GX;
		for (int i = 0; i < index; i++) {
			x += Sao.textWidth(Cosmetic.Slot.values()[i].label, 6.5f) + 18;
		}
		return x;
	}

	@Override
	void render(GuiGraphics g, float mx, float my, long t, float dt) {
		renderTabs(g, mx, my, t);
		scrollTarget = Math.max(0f, Math.min(scrollTarget, maxScroll()));
		scroll += (scrollTarget - scroll) * (1f - (float) Math.exp(-dt * 18f));

		String hoverName = null;
		String selectedId = CosmeticSelection.id(slot);
		g.enableScissor(Math.round(GX - 3), Math.round(VIEW_TOP), Math.round(GX + GRID_W + 3), Math.round(VIEW_BOTTOM));
		for (int i = 0; i <= items.size(); i++) {
			int row = i / COLS;
			int col = i % COLS;
			float p = Sao.progress(t, 140 + row * 45L + col * 12L, 90);
			if (p <= 0) {
				continue;
			}
			float x = cellX(i);
			float y = cellY(i);
			Cosmetic cosmetic = itemAt(i);
			boolean selected = cosmetic == null ? selectedId == null : cosmetic.id().equals(selectedId);
			boolean hover = my >= VIEW_TOP && my < VIEW_BOTTOM && Sao.inside(mx, my, x, y, CELL, CELL);
			Sao.rect(g, x, y, CELL, Math.max(1, Math.round(CELL * Sao.easeOut(p))), hover ? Sao.CELL_HOVER : Sao.CELL_BG);
			if (p < 1) {
				continue;
			}
			if (selected) {
				Sao.outline(g, x - 1, y - 1, CELL + 2, CELL + 2, Sao.ACCENT);
				Sao.outline(g, x, y, CELL, CELL, Sao.ACCENT);
			}
			if (cosmetic == null) {
				SaoGlyph.CROSS.drawCentered(g, x + CELL / 2f, y + CELL / 2f, 16, Sao.ICON_DIM);
			} else {
				icon(g, cosmetic, x, y);
				if (hover) {
					hoverName = cosmetic.name();
				}
			}
		}
		g.disableScissor();
		renderScrollbar(g);

		Sao.rect(g, DIV_X, WY + HEADER + 8, 1, WH - HEADER - 16, 0x55FFFFFF);
		renderPreview(g, mx, my);
		renderFooter(g, mx, my, dt);

		if (hoverName != null) {
			Sao.label(g, hoverName, mx + 8, my + 10);
		}
	}

	/** Hücre resmi: varsa kozmetiğin ikonu, pelerinde dokunun ön yüzü, yoksa yuvanın simgesi. */
	private static void icon(GuiGraphics g, Cosmetic cosmetic, float x, float y) {
		int color = Sao.fade(Sao.WHITE);
		if (cosmetic instanceof Cosmetic.Cape cape) {
			// Pelerin dokusu 64×32 düzenindedir; ön yüz (1,1)'den başlayan 10×16'lık bölge.
			Identifier texture = cape.frame(0).texturePath();
			int w = 18;
			int h = 29;
			g.blit(RenderPipelines.GUI_TEXTURED, texture, Math.round(x + (CELL - w) / 2f), Math.round(y + (CELL - h) / 2f),
				1f, 1f, w, h, 10, 16, 64, 32, color);
			return;
		}
		if (cosmetic.icon() != null) {
			int size = 30;
			g.blit(RenderPipelines.GUI_TEXTURED, cosmetic.icon(), Math.round(x + (CELL - size) / 2f), Math.round(y + (CELL - size) / 2f),
				0f, 0f, size, size, 1, 1, 1, 1, color);
			return;
		}
		SaoGlyph glyph = cosmetic.slot() == Cosmetic.Slot.HEAD ? SaoGlyph.HAT : SaoGlyph.WINGS;
		glyph.drawCentered(g, x + CELL / 2f, y + CELL / 2f, 20, Sao.ICON);
	}

	private void renderTabs(GuiGraphics g, float mx, float my, long t) {
		float a = Sao.progress(t, 120, 100);
		float old = Sao.alpha;
		Sao.alpha = old * a;
		Cosmetic.Slot[] slots = Cosmetic.Slot.values();
		for (int i = 0; i < slots.length; i++) {
			float x = tabX(i);
			String label = slots[i].label;
			float w = Sao.textWidth(label, 6.5f);
			boolean hover = Sao.inside(mx, my, x - 6, TAB_Y - 4, w + 12, 16);
			Sao.text(g, label, x, TAB_Y, 6.5f, slots[i] == slot ? Sao.TITLE : hover ? Sao.TEXT : Sao.MUTED);
			if (slots[i] == slot) {
				Sao.rect(g, x, TAB_Y + 10, w, 1.5f, Sao.ACCENT);
			}
		}
		Sao.alpha = old;
	}

	private void renderScrollbar(GuiGraphics g) {
		float max = maxScroll();
		if (max <= 0f) {
			return;
		}
		float trackH = VIEW_BOTTOM - VIEW_TOP - 6;
		float thumbH = Math.max(18, trackH * trackH / (trackH + max));
		float thumbY = VIEW_TOP + 3 + (trackH - thumbH) * (scroll / max);
		Sao.rect(g, DIV_X - 5, thumbY, 2, thumbH, 0x99FFFFFF);
	}

	/**
	 * Varlık çizimi ekran koordinatı ister; tasarım koordinatları ekrana burada çevrilir. Baş sekmesinde model
	 * envanterdeki gibi fareye bakar; sırt ve pelerin sekmelerinde arkasını döner ki kozmetik görünsün.
	 */
	private void renderPreview(GuiGraphics g, float mx, float my) {
		var player = host.minecraft().player;
		if (player == null) {
			return;
		}
		int x1 = Math.round(host.screenX(DX));
		int x2 = Math.round(host.screenX(DX + DW));
		int y1 = Math.round(host.screenY(WY + HEADER + 10));
		int y2 = Math.round(host.screenY(FOOTER_Y - 6));
		if (slot == Cosmetic.Slot.HEAD) {
			InventoryScreen.renderEntityInInventoryFollowsMouse(g, x1, y1, x2, y2,
				Math.round(60 * host.scale()), 0.0625f, host.screenX(mx), host.screenY(my), player);
			return;
		}
		float turn = (float) Math.atan(((x1 + x2) / 2f - host.screenX(mx)) / 40f) * 20f;
		LivingEntity entity = player;
		EntityRenderer<? super LivingEntity, ?> renderer = host.minecraft().getEntityRenderDispatcher().getRenderer(entity);
		EntityRenderState state = renderer.createRenderState(entity, 1f);
		state.lightCoords = LightTexture.FULL_BRIGHT;
		state.shadowPieces.clear();
		state.outlineColor = 0;
		if (state instanceof LivingEntityRenderState living) {
			living.bodyRot = turn;
			living.yRot = 0f;
			living.xRot = 0f;
			living.boundingBoxWidth = living.boundingBoxWidth / living.scale;
			living.boundingBoxHeight = living.boundingBoxHeight / living.scale;
			living.scale = 1f;
		}
		Vector3f offset = new Vector3f(0f, state.boundingBoxHeight / 2f + 0.0625f, 0f);
		Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
		g.submitEntityRenderState(state, Math.round(60 * host.scale()), offset, rotation, null, x1, y1, x2, y2);
	}

	private void renderFooter(GuiGraphics g, float mx, float my, float dt) {
		showAnim += ((CosmeticSelection.showOthers() ? 1f : 0f) - showAnim) * (1f - (float) Math.exp(-dt * 20f));
		boolean hover = Sao.inside(mx, my, DX - 2, FOOTER_Y - 3, DW + 4, 18);
		Sao.text(g, SHOW_OTHERS, DX, FOOTER_Y + 3, 5.8f, hover ? Sao.TEXT : Sao.MUTED);
		Sao.toggle(g, DX + DW - 26, FOOTER_Y, showAnim, hover);
	}

	@Override
	boolean click(float mx, float my, int button) {
		if (button != 0) {
			return false;
		}
		Cosmetic.Slot[] slots = Cosmetic.Slot.values();
		for (int i = 0; i < slots.length; i++) {
			float w = Sao.textWidth(slots[i].label, 6.5f);
			if (Sao.inside(mx, my, tabX(i) - 6, TAB_Y - 4, w + 12, 16)) {
				slot = slots[i];
				items = CosmeticRegistry.forSlot(slot);
				scroll = 0f;
				scrollTarget = 0f;
				return true;
			}
		}
		if (Sao.inside(mx, my, DX - 2, FOOTER_Y - 3, DW + 4, 18)) {
			CosmeticSelection.setShowOthers(!CosmeticSelection.showOthers());
			return true;
		}
		if (my < VIEW_TOP || my >= VIEW_BOTTOM) {
			return false;
		}
		for (int i = 0; i <= items.size(); i++) {
			if (Sao.inside(mx, my, cellX(i), cellY(i), CELL, CELL)) {
				Cosmetic cosmetic = itemAt(i);
				CosmeticSelection.set(slot, cosmetic == null ? null : cosmetic.id());
				return true;
			}
		}
		return false;
	}

	@Override
	boolean scroll(float mx, float my, double amount) {
		if (mx < DIV_X) {
			scrollTarget = Math.max(0f, Math.min(maxScroll(), scrollTarget - (float) amount * 30f));
			return true;
		}
		return false;
	}
}
