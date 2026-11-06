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
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.BlockOutlineRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Bakılan bloğun çerçevesini renklendirir, kalınlaştırır ve isteğe bağlı olarak yüzlerini yarı saydam boyar.
 * Fabric'in blok çerçevesi olayında oyunun çerçevesi iptal edilip bu çizilir.
 */
public final class BlockHighlightModule extends Module {
	private final Option.Choice color = add(Palette.option("color", "Çerçeve rengi", 5));
	private final Option.Range width = add(new Option.Range("width", "Kalınlık", 2f, 1f, 6f, 0.5f));
	private final Option.Range alpha = add(new Option.Range("alpha", "Çerçeve opaklığı", 0.9f, 0.1f, 1f, 0.05f));
	private final Option.Bool fill = add(new Option.Bool("fill", "Yüzleri boya", true));
	private final Option.Choice fillColor = add(Palette.option("fill_color", "Dolgu rengi", 5));
	private final Option.Range fillAlpha = add(new Option.Range("fill_alpha", "Dolgu opaklığı", 0.2f, 0.05f, 0.8f, 0.05f));

	public BlockHighlightModule() {
		super("block_highlight", "Blok Vurgusu", "BV", Category.VISUAL, false);
	}

	/** Fabric olayından çağrılır; true dönerse oyunun kendi çerçevesi de çizilir. */
	public static boolean render(WorldRenderContext context, BlockOutlineRenderState state) {
		BlockHighlightModule module = ModuleManager.get(BlockHighlightModule.class);
		if (module == null || !module.isEnabled() || state == null) {
			return true;
		}
		module.draw(context, state);
		return false;
	}

	private void draw(WorldRenderContext context, BlockOutlineRenderState state) {
		Vec3 camera = context.worldState().cameraRenderState.pos;
		BlockPos pos = state.pos();
		double dx = pos.getX() - camera.x;
		double dy = pos.getY() - camera.y;
		double dz = pos.getZ() - camera.z;
		PoseStack poseStack = context.matrices();
		MultiBufferSource consumers = context.consumers();
		if (fill.value) {
			VertexConsumer quads = consumers.getBuffer(RenderTypes.debugQuads());
			int fillArgb = Palette.withAlpha(Palette.color(fillColor), fillAlpha.value);
			for (AABB box : state.shape().toAabbs()) {
				WorldLines.filledBox(poseStack.last(), quads, box.move(dx, dy, dz).inflate(0.002), fillArgb);
			}
			WorldLines.flush(consumers, RenderTypes.debugQuads());
		}
		VertexConsumer lines = consumers.getBuffer(RenderTypes.lines());
		float lineWidth = Minecraft.getInstance().getWindow().getAppropriateLineWidth() * width.value / 2f;
		ShapeRenderer.renderShape(poseStack, lines, state.shape(), dx, dy, dz, Palette.withAlpha(Palette.color(color), alpha.value), lineWidth);
		WorldLines.flush(consumers, RenderTypes.lines());
	}
}
