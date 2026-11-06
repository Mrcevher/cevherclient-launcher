package net.cevherclient.client.module.impl;

import net.cevherclient.client.module.Module;
import net.cevherclient.client.module.ModuleManager;
import net.cevherclient.client.module.Option;
import net.cevherclient.client.module.Palette;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;

/**
 * Oyunun nişangahının yerine çizilen özel nişangah: biçim, renk, boyut, kalınlık, boşluk ve kenarlık seçilebilir.
 * Oyunun nişangah HUD öğesi CevherClientMod'da bununla değiştirilir; modül kapalıyken oyunun kendisi çizilir.
 */
public final class CrosshairModule extends Module {
	private static final Identifier INDICATOR_FULL = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_full");
	private static final Identifier INDICATOR_BACKGROUND = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_background");
	private static final Identifier INDICATOR_PROGRESS = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_progress");

	private final Option.Choice style = add(new Option.Choice("style", "Biçim", 0,
		"Artı", "Boşluklu artı", "Nokta", "Daire", "Daire + nokta", "Kare", "X", "T", "Köşeler"));
	private final Option.Choice color = add(Palette.option("color", "Renk", 0));
	private final Option.Range size = add(new Option.Range("size", "Boyut", 5f, 1f, 12f, 1f));
	private final Option.Range thickness = add(new Option.Range("thickness", "Kalınlık", 1f, 1f, 4f, 1f));
	private final Option.Range gap = add(new Option.Range("gap", "Boşluk", 2f, 0f, 8f, 1f));
	private final Option.Range opacity = add(new Option.Range("opacity", "Opaklık", 1f, 0.1f, 1f, 0.05f));
	private final Option.Bool outline = add(new Option.Bool("outline", "Kenarlık", true));
	private final Option.Bool dot = add(new Option.Bool("dot", "Orta nokta", false));

	public CrosshairModule() {
		super("crosshair", "Özel Nişangah", "NŞ", Category.VISUAL, false);
	}

	public static boolean replacesVanilla() {
		CrosshairModule module = ModuleManager.get(CrosshairModule.class);
		return module != null && module.isEnabled();
	}

	public static void render(GuiGraphics g, Minecraft mc) {
		CrosshairModule module = ModuleManager.get(CrosshairModule.class);
		if (module == null || mc.player == null || !mc.options.getCameraType().isFirstPerson()) {
			return;
		}
		if (mc.gameMode != null && mc.gameMode.getPlayerMode() == GameType.SPECTATOR) {
			return;
		}
		g.nextStratum();
		module.draw(g, g.guiWidth() / 2, g.guiHeight() / 2);
		module.attackIndicator(g, mc);
	}

	private void draw(GuiGraphics g, int cx, int cy) {
		int argb = Palette.withAlpha(Palette.color(color), opacity.value);
		int border = Palette.withAlpha(0xFF000000, opacity.value * 0.8f);
		int s = Math.round(size.value);
		int t = Math.round(thickness.value);
		int gp = Math.round(gap.value);
		int lo = -(t / 2);
		int hi = lo + t;
		// İki geçiş: önce bir piksel taşan siyah kenarlık, sonra asıl renk.
		for (int pass = outline.value ? 0 : 1; pass < 2; pass++) {
			int c = pass == 0 ? border : argb;
			int e = pass == 0 ? 1 : 0;
			switch (style.index) {
				case 0 -> plus(g, cx, cy, 0, s, lo, hi, e, c, true);
				case 1 -> plus(g, cx, cy, gp, s, lo, hi, e, c, true);
				case 2 -> box(g, cx + lo - e, cy + lo - e, cx + hi + e, cy + hi + e, c);
				case 3 -> ring(g, cx, cy, s, t, e, c);
				case 4 -> {
					ring(g, cx, cy, s, t, e, c);
					box(g, cx + lo - e, cy + lo - e, cx + hi + e, cy + hi + e, c);
				}
				case 5 -> square(g, cx, cy, s, t, e, c);
				case 6 -> cross(g, cx, cy, gp, s, t, e, c);
				case 7 -> plus(g, cx, cy, gp, s, lo, hi, e, c, false);
				default -> corners(g, cx, cy, s, t, e, c);
			}
			if (dot.value && style.index != 2) {
				box(g, cx + lo - e, cy + lo - e, cx + hi + e, cy + hi + e, c);
			}
		}
	}

	private static void box(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
		g.fill(x1, y1, x2, y2, color);
	}

	/** Dört kollu artı; {@code top} false ise üst kol çizilmez (T biçimi). */
	private static void plus(GuiGraphics g, int cx, int cy, int gap, int len, int lo, int hi, int e, int c, boolean top) {
		if (gap == 0) {
			box(g, cx - len - e, cy + lo - e, cx + len + hi + e, cy + hi + e, c);
			box(g, cx + lo - e, (top ? cy - len : cy) - e, cx + hi + e, cy + len + hi + e, c);
			return;
		}
		box(g, cx - gap - len - e, cy + lo - e, cx - gap + e, cy + hi + e, c);
		box(g, cx + gap + hi - e, cy + lo - e, cx + gap + hi + len + e, cy + hi + e, c);
		if (top) {
			box(g, cx + lo - e, cy - gap - len - e, cx + hi + e, cy - gap + e, c);
		}
		box(g, cx + lo - e, cy + gap + hi - e, cx + hi + e, cy + gap + hi + len + e, c);
	}

	private static void ring(GuiGraphics g, int cx, int cy, int radius, int t, int e, int c) {
		int r = Math.max(2, radius);
		float inner = r - t - e + 0.5f;
		float outer = r + e + 0.5f;
		for (int y = -r - e; y <= r + e; y++) {
			for (int x = -r - e; x <= r + e; x++) {
				float d = (float) Math.sqrt(x * x + y * y);
				if (d >= inner && d < outer) {
					box(g, cx + x, cy + y, cx + x + 1, cy + y + 1, c);
				}
			}
		}
	}

	private static void square(GuiGraphics g, int cx, int cy, int r, int t, int e, int c) {
		box(g, cx - r - e, cy - r - e, cx + r + 1 + e, cy - r + t + e, c);
		box(g, cx - r - e, cy + r + 1 - t - e, cx + r + 1 + e, cy + r + 1 + e, c);
		box(g, cx - r - e, cy - r - e, cx - r + t + e, cy + r + 1 + e, c);
		box(g, cx + r + 1 - t - e, cy - r - e, cx + r + 1 + e, cy + r + 1 + e, c);
	}

	private static void cross(GuiGraphics g, int cx, int cy, int gap, int len, int t, int e, int c) {
		for (int i = gap; i <= gap + len; i++) {
			for (int sx = -1; sx <= 1; sx += 2) {
				for (int sy = -1; sy <= 1; sy += 2) {
					int x = cx + sx * i;
					int y = cy + sy * i;
					box(g, x - e, y - e, x + t + e, y + t + e, c);
				}
			}
		}
	}

	private static void corners(GuiGraphics g, int cx, int cy, int r, int t, int e, int c) {
		int arm = Math.max(2, r / 2);
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sy = -1; sy <= 1; sy += 2) {
				int x = sx < 0 ? cx - r : cx + r + 1 - t;
				int y = sy < 0 ? cy - r : cy + r + 1 - t;
				int hx = sx < 0 ? x : x + t - arm;
				int vy = sy < 0 ? y : y + t - arm;
				box(g, hx - e, y - e, hx + arm + e, y + t + e, c);
				box(g, x - e, vy - e, x + t + e, vy + arm + e, c);
			}
		}
	}

	/** Oyunun "nişangahta saldırı göstergesi" ayarı açıksa, oyundaki gibi altta dolum çubuğu. */
	private void attackIndicator(GuiGraphics g, Minecraft mc) {
		if (mc.options.attackIndicator().get() != AttackIndicatorStatus.CROSSHAIR) {
			return;
		}
		float strength = mc.player.getAttackStrengthScale(0f);
		boolean full = mc.crosshairPickEntity instanceof LivingEntity target && target.isAlive() && strength >= 1f
			&& mc.player.getCurrentItemAttackStrengthDelay() > 5f;
		int y = g.guiHeight() / 2 - 7 + 16;
		int x = g.guiWidth() / 2 - 8;
		if (full) {
			g.blitSprite(RenderPipelines.CROSSHAIR, INDICATOR_FULL, x, y, 16, 16);
		} else if (strength < 1f) {
			int progress = (int) (strength * 17f);
			g.blitSprite(RenderPipelines.CROSSHAIR, INDICATOR_BACKGROUND, x, y, 16, 4);
			g.blitSprite(RenderPipelines.CROSSHAIR, INDICATOR_PROGRESS, 16, 4, 0, 0, x, y, progress, 4);
		}
	}
}
