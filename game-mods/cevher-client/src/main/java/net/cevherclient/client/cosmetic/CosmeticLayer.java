package net.cevherclient.client.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.cevherclient.client.CevherClientMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Oyuncu çizimine eklenen kozmetik katmanı: baş yuvası başa, sırt yuvası gövdeye bağlı çizilir (pelerin yuvası
 * oyuncunun pelerin dokusunu değiştirir, bkz. AbstractClientPlayerMixin). Kendi karakterimizde yerel seçim,
 * diğer oyuncularda sunucunun bildirdiği kozmetik çizilir ({@link CosmeticNet}); ikincisi ayardan kapatılabilir.
 *
 * <p>OdinMC ağları y yukarı, boyun orijinli dünya biriminde tutulur. Oyuncu modeli y aşağı ve x aynalı olduğu
 * için köşeler {@code (-x, -y, z)} olarak yazılır; sırt ağları OdinMC'deki gibi gövdeden biraz geride durur.
 */
public final class CosmeticLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final RenderType MESH_TYPE = RenderTypes.entityCutoutNoCull(
		Identifier.fromNamespaceAndPath(CevherClientMod.MOD_ID, "textures/cosmetic/white.png"));
	/** OdinMC'nin sırt kozmetiklerini gövdeden uzaklaştırdığı mesafe (0,07 dünya birimi / 0,9375 model ölçeği). */
	private static final float BACK_OFFSET = 0.07f / 0.9375f;
	/** Kanatların menteşesinin gövdenin arkasındaki derinliği. */
	private static final float WING_HINGE_Z = 0.125f;

	public CosmeticLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		if (state.isInvisible || state.isSpectator) {
			return;
		}
		Cosmetic head = of(state, Cosmetic.Slot.HEAD);
		if (head != null) {
			pose.pushPose();
			getParentModel().head.translateAndRotate(pose);
			draw(head, pose, collector, light, state, 0f);
			pose.popPose();
		}
		Cosmetic back = of(state, Cosmetic.Slot.BACK);
		if (back != null) {
			pose.pushPose();
			getParentModel().body.translateAndRotate(pose);
			draw(back, pose, collector, light, state, BACK_OFFSET);
			pose.popPose();
		}
	}

	private static void draw(Cosmetic cosmetic, PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float zOffset) {
		switch (cosmetic) {
			case Cosmetic.Model model ->
				collector.submitModelPart(model.root(), pose, RenderTypes.entityCutoutNoCull(model.texture()), light, OverlayTexture.NO_OVERLAY, null);
			case Cosmetic.Mesh mesh -> {
				if (mesh.mesh().ready()) {
					float flap = mesh.flaps() ? flapAngle(state) : 0f;
					collector.submitCustomGeometry(pose, MESH_TYPE, (last, consumer) -> mesh(last, consumer, mesh.mesh(), light, zOffset, flap));
				}
			}
			case Cosmetic.Cape cape -> {
				// Pelerin dokuyla çizilir, katman çizmez.
			}
		}
	}

	/** Kanat çırpma açısı (radyan): dururken yavaş, koşarken ve süzülürken daha geniş. */
	private static float flapAngle(AvatarRenderState state) {
		float speed = state.isFallFlying ? 0.35f : 0.12f + state.walkAnimationSpeed * 0.15f;
		float amplitude = state.isFallFlying ? 0.35f : 0.12f + state.walkAnimationSpeed * 0.1f;
		return Mth.sin(state.ageInTicks * speed) * amplitude;
	}

	private static void mesh(PoseStack.Pose pose, VertexConsumer consumer, CosmeticMesh mesh, int light, float zOffset, float flap) {
		float[] q = mesh.quads();
		int[] colors = mesh.colors();
		float cos = Mth.cos(flap);
		float sin = Mth.sin(flap);
		for (int quad = 0; quad < colors.length; quad++) {
			int base = quad * 12;
			for (int corner = 0; corner < 4; corner++) {
				int i = base + corner * 3;
				float x = q[i];
				float y = q[i + 1];
				float z = q[i + 2];
				if (flap != 0f) {
					// Her kanat kendi tarafındaki menteşe (x=0, z=gövde arkası) etrafında, ters yönlerde döner.
					float side = x < 0f ? 1f : -1f;
					float dz = z - WING_HINGE_Z;
					float rx = x * cos - dz * sin * side;
					float rz = x * sin * side + dz * cos;
					x = rx;
					z = rz + WING_HINGE_Z;
				}
				// Renkler dosyada gölgelenmiş geldiği için tüm yüzlere aynı (yukarı bakan) normal verilir.
				consumer.addVertex(pose, -x, -y, z + zOffset)
					.setColor(colors[quad])
					.setUv(0.5f, 0.5f)
					.setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(light)
					.setNormal(pose, 0f, 1f, 0f);
			}
		}
	}

	/** Çizilen oyuncunun o yuvadaki kozmetiği: kendimiz için yerel seçim, başkaları için sunucunun bildirdiği. */
	static Cosmetic of(AvatarRenderState state, Cosmetic.Slot slot) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return null;
		}
		if (state.id == mc.player.getId()) {
			return CosmeticSelection.get(slot);
		}
		if (!CosmeticSelection.showOthers()) {
			return null;
		}
		return mc.level.getEntity(state.id) instanceof Player other ? RemoteCosmetics.get(other.getUUID(), slot) : null;
	}
}
