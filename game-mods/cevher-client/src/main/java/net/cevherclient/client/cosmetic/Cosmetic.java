package net.cevherclient.client.cosmetic;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;

/**
 * Yüklenmiş bir kozmetik. Üç türü var: Blockbench modeli (dokulu), renkli dörtgen ağı (OdinMC'den gelen
 * şapka ve kanatlar) ve pelerin (oyuncunun pelerin dokusunun yerine geçer, hareketli olabilir).
 * {@code icon} menüdeki küçük resimdir; yoksa menü bir simge çizer.
 */
public sealed interface Cosmetic permits Cosmetic.Model, Cosmetic.Mesh, Cosmetic.Cape {
	String id();

	String name();

	Slot slot();

	Identifier icon();

	enum Slot {
		HEAD("head", "Baş"),
		BACK("back", "Sırt"),
		CAPE("cape", "Pelerin");

		/** Ayar dosyasında ve sunucu mesajlarında kullanılan ad. */
		public final String key;
		public final String label;

		Slot(String key, String label) {
			this.key = key;
			this.label = label;
		}

		public static Slot parse(String value) {
			for (Slot slot : values()) {
				if (slot.key.equals(value)) {
					return slot;
				}
			}
			throw new IllegalArgumentException("Bilinmeyen yuva: " + value);
		}
	}

	/** {@code root}, yuvanın bağlandığı kemiğin (baş için oyuncunun başı) pivotunu orijin sayan model ağacıdır. */
	record Model(String id, String name, Slot slot, Identifier icon, ModelPart root, Identifier texture) implements Cosmetic {
	}

	/** {@code flaps}: kanatlar zamanla hafifçe çırpılır. */
	record Mesh(String id, String name, Slot slot, Identifier icon, CosmeticMesh mesh, boolean flaps) implements Cosmetic {
	}

	/** Pelerin; birden çok karesi varsa {@code frameMs} aralıkla döner. */
	record Cape(String id, String name, Identifier icon, ClientAsset.Texture[] frames, int frameMs) implements Cosmetic {
		@Override
		public Slot slot() {
			return Slot.CAPE;
		}

		public ClientAsset.Texture frame(long millis) {
			return frames.length == 1 ? frames[0] : frames[(int) ((millis / frameMs) % frames.length)];
		}
	}
}
