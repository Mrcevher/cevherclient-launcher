package net.cevherclient.client.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.AABB;

/**
 * Dünya içinde çizgi ve yarı saydam yüz çizimi. Koordinatlar kameraya göredir (dünya konumu - kamera konumu);
 * çizgiler {@code RenderTypes.lines()}, yüzler {@code RenderTypes.debugQuads()} tamponuna yazılır.
 */
public final class WorldLines {
	private WorldLines() {
	}

	public static void line(PoseStack.Pose pose, VertexConsumer lines, float x1, float y1, float z1, float x2, float y2, float z2,
		int color, float width) {
		float dx = x2 - x1;
		float dy = y2 - y1;
		float dz = z2 - z1;
		float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (length < 1.0E-6f) {
			return;
		}
		dx /= length;
		dy /= length;
		dz /= length;
		lines.addVertex(pose, x1, y1, z1).setColor(color).setNormal(pose, dx, dy, dz).setLineWidth(width);
		lines.addVertex(pose, x2, y2, z2).setColor(color).setNormal(pose, dx, dy, dz).setLineWidth(width);
	}

	/** Kutunun 12 kenarı. */
	public static void box(PoseStack.Pose pose, VertexConsumer lines, AABB box, int color, float width) {
		float x1 = (float) box.minX;
		float y1 = (float) box.minY;
		float z1 = (float) box.minZ;
		float x2 = (float) box.maxX;
		float y2 = (float) box.maxY;
		float z2 = (float) box.maxZ;
		line(pose, lines, x1, y1, z1, x2, y1, z1, color, width);
		line(pose, lines, x1, y1, z2, x2, y1, z2, color, width);
		line(pose, lines, x1, y2, z1, x2, y2, z1, color, width);
		line(pose, lines, x1, y2, z2, x2, y2, z2, color, width);
		line(pose, lines, x1, y1, z1, x1, y2, z1, color, width);
		line(pose, lines, x2, y1, z1, x2, y2, z1, color, width);
		line(pose, lines, x1, y1, z2, x1, y2, z2, color, width);
		line(pose, lines, x2, y1, z2, x2, y2, z2, color, width);
		line(pose, lines, x1, y1, z1, x1, y1, z2, color, width);
		line(pose, lines, x2, y1, z1, x2, y1, z2, color, width);
		line(pose, lines, x1, y2, z1, x1, y2, z2, color, width);
		line(pose, lines, x2, y2, z1, x2, y2, z2, color, width);
	}

	/** Kutunun altı yüzü (dörtgen tamponu). */
	public static void filledBox(PoseStack.Pose pose, VertexConsumer quads, AABB box, int color) {
		float x1 = (float) box.minX;
		float y1 = (float) box.minY;
		float z1 = (float) box.minZ;
		float x2 = (float) box.maxX;
		float y2 = (float) box.maxY;
		float z2 = (float) box.maxZ;
		quad(pose, quads, color, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2);
		quad(pose, quads, color, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1);
		quad(pose, quads, color, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1);
		quad(pose, quads, color, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2);
		quad(pose, quads, color, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1);
		quad(pose, quads, color, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2);
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer quads, int color, float... xyz) {
		for (int i = 0; i < 12; i += 3) {
			quads.addVertex(pose, xyz[i], xyz[i + 1], xyz[i + 2]).setColor(color);
		}
	}

	/** Bu karede yazılan çizimi hemen gönderir; sonraki çizimlerle sıralama karışmasın. */
	public static void flush(MultiBufferSource consumers, RenderType... types) {
		if (consumers instanceof MultiBufferSource.BufferSource source) {
			for (RenderType type : types) {
				source.endBatch(type);
			}
		}
	}
}
