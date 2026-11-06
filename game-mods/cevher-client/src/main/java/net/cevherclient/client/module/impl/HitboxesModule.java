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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Varlıkların vuruş kutularını (F3+B'deki gibi) renkli çizer: nişan alınan ve hasar alan varlık ayrı renkte,
 * isteğe bağlı göz hizası ve bakış yönü çizgisiyle.
 */
public final class HitboxesModule extends Module {
	private final Option.Choice targets = add(new Option.Choice("targets", "Varlıklar", 0, "Oyuncular", "Canlılar", "Tümü"));
	private final Option.Choice color = add(Palette.option("color", "Kutu rengi", 0));
	private final Option.Bool targetColor = add(new Option.Bool("target_color", "Hedef sarı", true));
	private final Option.Bool hurtColor = add(new Option.Bool("hurt_color", "Hasarda kırmızı", true));
	private final Option.Bool eyeLine = add(new Option.Bool("eye_line", "Göz hizası", true));
	private final Option.Bool lookLine = add(new Option.Bool("look_line", "Bakış yönü", false));
	private final Option.Bool self = add(new Option.Bool("self", "Kendi kutun", false));
	private final Option.Range distance = add(new Option.Range("distance", "Mesafe", 32f, 8f, 128f, 4f));
	private final Option.Range width = add(new Option.Range("width", "Kalınlık", 1.5f, 1f, 5f, 0.5f));

	public HitboxesModule() {
		super("hitboxes", "Hitbox", "HB", Category.VISUAL, false);
	}

	public static void render(WorldRenderContext context) {
		HitboxesModule module = ModuleManager.get(HitboxesModule.class);
		if (module != null && module.isEnabled()) {
			module.draw(context);
		}
	}

	private boolean wanted(Minecraft mc, Entity entity) {
		if (entity == mc.player) {
			return self.value && !mc.options.getCameraType().isFirstPerson();
		}
		if (entity.isInvisibleTo(mc.player)) {
			return false;
		}
		return switch (targets.index) {
			case 0 -> entity instanceof Player;
			case 1 -> entity instanceof LivingEntity;
			default -> true;
		};
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
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (entity.distanceToSqr(mc.player) > maxSq || !wanted(mc, entity)) {
				continue;
			}
			Vec3 lerped = entity.getPosition(partial);
			Vec3 offset = lerped.subtract(entity.position()).subtract(camera);
			AABB box = entity.getBoundingBox().move(offset);
			int argb = Palette.color(color);
			if (hurtColor.value && entity instanceof LivingEntity living && living.hurtTime > 0) {
				argb = 0xFFFF4444;
			} else if (targetColor.value && entity == mc.crosshairPickEntity) {
				argb = 0xFFFFE066;
			}
			WorldLines.box(pose, lines, box, argb, lineWidth);
			double eyeY = lerped.y + entity.getEyeHeight() - camera.y;
			if (eyeLine.value && entity instanceof LivingEntity) {
				WorldLines.box(pose, lines, new AABB(box.minX, eyeY - 0.01, box.minZ, box.maxX, eyeY + 0.01, box.maxZ), 0xFFFF5555, lineWidth);
			}
			if (lookLine.value && entity instanceof LivingEntity) {
				Vec3 look = entity.getViewVector(partial).scale(2.0);
				float x = (float) (lerped.x - camera.x);
				float z = (float) (lerped.z - camera.z);
				WorldLines.line(pose, lines, x, (float) eyeY, z, x + (float) look.x, (float) (eyeY + look.y), z + (float) look.z, 0xFF5599FF, lineWidth);
			}
		}
		WorldLines.flush(consumers, RenderTypes.lines());
	}
}
