package net.cevherclient.client.module.impl;

import net.cevherclient.client.Keys;
import net.cevherclient.client.module.Module;
import net.cevherclient.client.module.ModuleManager;
import net.cevherclient.client.module.Option;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Serbest bakış: tuş basılıyken fare karakteri değil yalnızca kamerayı döndürür; karakter aynı yöne bakmaya ve
 * yürümeye devam eder. Fare hareketi MouseHandlerMixin'de, kamera yönü CameraMixin'de buraya yönlendirilir.
 */
public final class FreeLookModule extends Module {
	private static final CameraType[] PERSPECTIVES = {CameraType.THIRD_PERSON_BACK, CameraType.THIRD_PERSON_FRONT, CameraType.FIRST_PERSON};

	private final Option.Choice activation = add(new Option.Choice("activation", "Etkinleştirme", 0, "Basılı tut", "Aç / kapa"));
	private final Option.Choice perspective = add(new Option.Choice("perspective", "Görünüm", 0, "Arkadan", "Önden", "Birinci şahıs"));

	private boolean active;
	private float yaw;
	private float pitch;
	private CameraType previous;

	public FreeLookModule() {
		super("freelook", "Serbest Bakış", "SB", Category.VISUAL, false);
	}

	private static FreeLookModule activeModule() {
		FreeLookModule module = ModuleManager.get(FreeLookModule.class);
		return module != null && module.isEnabled() && module.active ? module : null;
	}

	public static boolean isActive() {
		return activeModule() != null;
	}

	public static float yaw() {
		return activeModule().yaw;
	}

	public static float pitch() {
		return activeModule().pitch;
	}

	/** Fare hareketini kameraya uygular; serbest bakış açık değilse false (karakter normal döner). */
	public static boolean turn(double dx, double dy) {
		FreeLookModule module = activeModule();
		if (module == null) {
			return false;
		}
		// Entity.turn ile aynı ölçek: piksel başına 0,15 derece.
		module.yaw += (float) dx * 0.15f;
		module.pitch = Mth.clamp(module.pitch + (float) dy * 0.15f, -90f, 90f);
		return true;
	}

	@Override
	public void onTick(Minecraft mc) {
		boolean wanted;
		if (activation.index == 0) {
			wanted = Keys.FREELOOK.isDown() && mc.screen == null;
			while (Keys.FREELOOK.consumeClick()) {
				// Basılı tutma kipinde tıklamalar biriktirilmez.
			}
		} else {
			wanted = active;
			while (Keys.FREELOOK.consumeClick()) {
				wanted = !wanted;
			}
		}
		if (wanted != active) {
			setActive(mc, wanted);
		}
	}

	@Override
	protected void onDisable(Minecraft mc) {
		setActive(mc, false);
	}

	private void setActive(Minecraft mc, boolean value) {
		if (value && mc.player != null) {
			yaw = mc.player.getYRot();
			pitch = mc.player.getXRot();
			previous = mc.options.getCameraType();
			mc.options.setCameraType(PERSPECTIVES[perspective.index]);
		} else if (!value && previous != null) {
			mc.options.setCameraType(previous);
			previous = null;
		}
		active = value && mc.player != null;
	}
}
