package net.cevherclient.client.cosmetic;

import java.util.EnumMap;
import java.util.Map;
import net.cevherclient.client.config.ClientConfig;

/**
 * Oyuncunun kozmetik ayarları: her yuva (baş, sırt, pelerin) için seçtiği kozmetik ve diğer oyuncuların
 * kozmetiklerini görüp görmeyeceği. Her şey {@code client.json}'a yazılır.
 */
public final class CosmeticSelection {
	private static final Map<Cosmetic.Slot, String> SELECTED = new EnumMap<>(Cosmetic.Slot.class);
	private static boolean showOthers = true;

	private CosmeticSelection() {
	}

	/** Yuvadaki seçili kozmetik; seçim yoksa ya da kozmetik artık yoksa null. */
	public static Cosmetic get(Cosmetic.Slot slot) {
		return CosmeticRegistry.find(SELECTED.get(slot));
	}

	public static String id(Cosmetic.Slot slot) {
		return SELECTED.get(slot);
	}

	/** Kullanıcı seçimi: kaydedilir ve sunucuya bildirilir. */
	public static void set(Cosmetic.Slot slot, String id) {
		put(slot, id);
		ClientConfig.markDirty();
		CosmeticNet.sendSelection(slot);
	}

	/** Sunucu seçimimizi reddedip kendi değerini bildirdiğinde: kaydedilir, sunucuya geri bildirilmez. */
	static void applyServer(Cosmetic.Slot slot, String id) {
		put(slot, id);
		ClientConfig.markDirty();
	}

	/** Dosyadan yüklerken kullanılır; kayıt yazılmasını tetiklemez. */
	public static void restore(Cosmetic.Slot slot, String id) {
		put(slot, id);
	}

	private static void put(Cosmetic.Slot slot, String id) {
		if (id == null || id.isEmpty()) {
			SELECTED.remove(slot);
		} else {
			SELECTED.put(slot, id);
		}
	}

	public static boolean showOthers() {
		return showOthers;
	}

	public static void setShowOthers(boolean value) {
		showOthers = value;
		ClientConfig.markDirty();
	}

	/** Dosyadan yüklerken kullanılır; kayıt yazılmasını tetiklemez. */
	public static void restoreShowOthers(boolean value) {
		showOthers = value;
	}
}
