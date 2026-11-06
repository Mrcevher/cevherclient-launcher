package net.cevherclient.client.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.cevherclient.client.config.ClientConfig;
import net.minecraft.client.Minecraft;

/** Mod menüsündeki bir modül: açılıp kapanabilir, kendi ayarları vardır. */
public abstract class Module {
	public enum Category {
		HUD("HUD"),
		MOVEMENT("Hareket"),
		VISUAL("Görsel"),
		MISC("Diğer");

		public final String label;

		Category(String label) {
			this.label = label;
		}
	}

	public final String id;
	public final String name;
	/** Kartta simge yerine görünen kısa harf grubu. */
	public final String badge;
	public final Category category;

	private final List<Option> options = new ArrayList<>();
	private boolean enabled;
	/** Menüdeki aç/kapa düğmesinin animasyon konumu (0 = kapalı, 1 = açık). Yalnızca arayüz kullanır. */
	public float toggleAnim;

	protected Module(String id, String name, String badge, Category category, boolean enabledByDefault) {
		this.id = id;
		this.name = name;
		this.badge = badge;
		this.category = category;
		this.enabled = enabledByDefault;
		this.toggleAnim = enabledByDefault ? 1f : 0f;
	}

	protected <T extends Option> T add(T option) {
		options.add(option);
		return option;
	}

	public List<Option> options() {
		return Collections.unmodifiableList(options);
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean value) {
		if (enabled == value) {
			return;
		}
		enabled = value;
		if (value) {
			onEnable(Minecraft.getInstance());
		} else {
			onDisable(Minecraft.getInstance());
		}
		ClientConfig.markDirty();
	}

	public void toggle() {
		setEnabled(!enabled);
	}

	/** Dosyadan yüklenirken olay tetiklemeden durumu ayarlar. */
	public void restoreEnabledFromConfig(boolean value) {
		enabled = value;
		toggleAnim = value ? 1f : 0f;
	}

	public boolean isHud() {
		return false;
	}

	/** Oyuncu oyundayken her tikte, yalnızca modül açıkken çağrılır. */
	public void onTick(Minecraft mc) {
	}

	protected void onEnable(Minecraft mc) {
	}

	protected void onDisable(Minecraft mc) {
	}
}
