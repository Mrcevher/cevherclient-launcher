package net.cevherclient.client.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** Bir modülün ayar menüsünde görünen tek bir ayarı. Üç tür: açık/kapalı, seçenek listesi, aralık. */
public abstract sealed class Option permits Option.Bool, Option.Choice, Option.Range {
	public final String id;
	public final String label;

	private Option(String id, String label) {
		this.id = id;
		this.label = label;
	}

	public abstract JsonElement save();

	public abstract void load(JsonElement value);

	public static final class Bool extends Option {
		public boolean value;

		public Bool(String id, String label, boolean value) {
			super(id, label);
			this.value = value;
		}

		@Override
		public JsonElement save() {
			return new JsonPrimitive(value);
		}

		@Override
		public void load(JsonElement element) {
			if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
				value = element.getAsBoolean();
			}
		}
	}

	public static final class Choice extends Option {
		public final String[] names;
		public int index;

		public Choice(String id, String label, int index, String... names) {
			super(id, label);
			this.names = names;
			this.index = index;
		}

		public String name() {
			return names[index];
		}

		public void cycle(int step) {
			index = Math.floorMod(index + step, names.length);
		}

		@Override
		public JsonElement save() {
			return new JsonPrimitive(index);
		}

		@Override
		public void load(JsonElement element) {
			if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
				int loaded = element.getAsInt();
				if (loaded >= 0 && loaded < names.length) {
					index = loaded;
				}
			}
		}
	}

	public static final class Range extends Option {
		public final float min;
		public final float max;
		public final float step;
		public float value;

		public Range(String id, String label, float value, float min, float max, float step) {
			super(id, label);
			this.value = value;
			this.min = min;
			this.max = max;
			this.step = step;
		}

		public void set(float raw) {
			float snapped = Math.round(raw / step) * step;
			value = Math.max(min, Math.min(max, snapped));
		}

		public float fraction() {
			return (value - min) / (max - min);
		}

		@Override
		public JsonElement save() {
			return new JsonPrimitive(value);
		}

		@Override
		public void load(JsonElement element) {
			if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
				set(element.getAsFloat());
			}
		}
	}
}
