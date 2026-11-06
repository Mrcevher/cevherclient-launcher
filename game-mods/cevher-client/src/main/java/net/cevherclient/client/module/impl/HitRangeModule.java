package net.cevherclient.client.module.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.cevherclient.client.module.Module;
import net.cevherclient.client.module.ModuleManager;
import net.cevherclient.client.module.Option;
import net.cevherclient.client.module.Palette;
import net.cevherclient.client.ui.WorldLines;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Diğer oyuncuların ayaklarının çevresine vuruş menzili kadar bir halka çizer. Sen o halkanın içindeyken
 * (yani sana vurabilecek kadar yakınken) halka başka renge döner.
 */
public final class HitRangeModule extends Module {
	private static final int SEGMENTS = 48;

	private final Option.Range radius = add(new Option.Range("radius", "Yarıçap (blok)", 3f, 1f, 6f, 0.1f));
	private final Option.Choice color = add(Palette.option("color", "Halka rengi", 0));
	private final Option.Bool inRange = add(new Option.Bool("in_range", "Menzilde renk değiştir", true));
	private final Option.Choice inRangeColor = add(Palette.option("in_range_color", "Menzil rengi", 1));
	private final Option.Bool nearestOnly = add(new Option.Bool("nearest", "Yalnızca en yakın", false));
	private final Option.Bool self = add(new Option.Bool("self", "Kendi çevrende", false));
	private final Option.Range distance = add(new Option.Range("distance", "Mesafe", 16f, 4f, 64f, 2f));
	private final Option.Range width = add(new Option.Range("width", "Kalınlık", 2f, 1f, 6f, 0.5f));

	public HitRangeModule() {
		super("hit_range", "Erişim Halkası", "EH", Category.VISUAL, false);
	}

	public static void render(WorldRenderContext context) {
		HitRangeModule module = ModuleManager.get(HitRangeModule.class);
		if (module != null && module.isEnabled()) {
			module.draw(context);
		}
	}

	private void draw(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.worldState().cameraRenderState.pos;
		PoseStack.Pose pose = context.matrices().last();
		MultiBufferSource consumers = context.consumers();
		VertexConsumer lines = consumers.getBuffer(RenderTypes.lines());
		float lineWidth = mc.getWindow().getAppropriateLineWidth() * width.value / 2f;
		double maxSq = distance.value * distance.value;

		Player nearest = null;
		double nearestSq = Double.MAX_VALUE;
		for (Player player : mc.level.players()) {
			if (player == mc.player || player.isInvisibleTo(mc.player) || player.isSpectator()) {
				continue;
			}
			double sq = player.distanceToSqr(mc.player);
			if (sq > maxSq) {
				continue;
			}
			if (nearestOnly.value) {
				if (sq < nearestSq) {
					nearest = player;
					nearestSq = sq;
				}
			} else {
				ring(mc, pose, lines, player, partial, camera, lineWidth);
			}
		}
		if (nearest != null) {
			ring(mc, pose, lines, nearest, partial, camera, lineWidth);
		}
		if (self.value && !mc.options.getCameraType().isFirstPerson()) {
			ring(mc, pose, lines, mc.player, partial, camera, lineWidth);
		}
		WorldLines.flush(consumers, RenderTypes.lines());
	}

	private void ring(Minecraft mc, PoseStack.Pose pose, VertexConsumer lines, Player player, float partial, Vec3 camera, float lineWidth) {
		Vec3 pos = player.getPosition(partial);
		boolean close = player != mc.player && inRange.value && pos.distanceTo(mc.player.getPosition(partial)) <= radius.value;
		int argb = Palette.color(close ? inRangeColor : color);
		float cx = (float) (pos.x - camera.x);
		float cy = (float) (pos.y - camera.y) + 0.02f;
		float cz = (float) (pos.z - camera.z);
		float r = radius.value;
		for (int i = 0; i < SEGMENTS; i++) {
			double a1 = Math.PI * 2 * i / SEGMENTS;
			double a2 = Math.PI * 2 * (i + 1) / SEGMENTS;
			WorldLines.line(pose, lines,
				cx + (float) Math.cos(a1) * r, cy, cz + (float) Math.sin(a1) * r,
				cx + (float) Math.cos(a2) * r, cy, cz + (float) Math.sin(a2) * r, argb, lineWidth);
		}
	}
}
