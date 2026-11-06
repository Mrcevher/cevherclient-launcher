package net.cevherclient.client.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import net.cevherclient.client.CevherClientMod;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Blockbench "Modlu Varlık" (.bbmodel) dosyasını oyunun {@link ModelPart} ağacına çevirir.
 *
 * <p>Blockbench koordinatları y yukarı ve boyun pivotu y=24'tür; oyun modelinde y aşağı ve pivot (0,0,0).
 * Dönüşüm: {@code java = (x, 24 - y, z)}. Dönüş açıları için {@code (rx, -ry, rz)} kullanılır; yalnızca
 * X ekseni oyunda doğrulandı. Desteklenenler: kutu UV'li küpler, iç içe gruplar, grup dönüşü.
 * Küp dönüşü ve kutu UV'siz küpler desteklenmez (uyarı yazılır, o küp atlanır).
 */
final class BbModelLoader {
	/** Blockbench'te boyun pivotunun yüksekliği. */
	private static final float NECK_Y = 24f;

	private BbModelLoader() {
	}

	static ModelPart load(JsonObject model, String debugName) {
		JsonObject resolution = model.getAsJsonObject("resolution");
		int texW = resolution.get("width").getAsInt();
		int texH = resolution.get("height").getAsInt();

		Map<String, JsonObject> byUuid = new HashMap<>();
		for (JsonElement e : model.getAsJsonArray("elements")) {
			byUuid.put(e.getAsJsonObject().get("uuid").getAsString(), e.getAsJsonObject());
		}
		for (JsonElement g : model.getAsJsonArray("groups")) {
			byUuid.put(g.getAsJsonObject().get("uuid").getAsString(), g.getAsJsonObject());
		}

		MeshDefinition mesh = new MeshDefinition();
		Context ctx = new Context(byUuid, debugName);
		ctx.addNodes(model.getAsJsonArray("outliner"), mesh.getRoot(), new float[] {0f, NECK_Y, 0f});
		return LayerDefinition.create(mesh, texW, texH).bakeRoot();
	}

	private record Context(Map<String, JsonObject> byUuid, String debugName) {
		/** Bir outliner düğüm listesini (uuid ya da {uuid, children}) {@code parent} altına ekler. */
		void addNodes(JsonArray nodes, PartDefinition parent, float[] parentOrigin) {
			// Gruba bağlı olmayan küpler üst düğümün kendi parçasına eklenir.
			CubeListBuilder loose = CubeListBuilder.create();
			boolean hasLoose = false;
			for (JsonElement node : nodes) {
				if (node.isJsonPrimitive()) {
					JsonObject cube = byUuid.get(node.getAsString());
					if (cube != null && addCube(loose, cube, parentOrigin)) {
						hasLoose = true;
					}
				}
			}
			if (hasLoose) {
				parent.addOrReplaceChild("loose_cubes", loose, PartPose.ZERO);
			}
			for (JsonElement node : nodes) {
				if (node.isJsonObject()) {
					addGroup(node.getAsJsonObject(), parent, parentOrigin);
				}
			}
		}

		private void addGroup(JsonObject node, PartDefinition parent, float[] parentOrigin) {
			JsonObject group = byUuid.get(node.get("uuid").getAsString());
			if (group == null) {
				return;
			}
			float[] origin = floats(group, "origin", 0f, 0f, 0f);
			float[] rotation = floats(group, "rotation", 0f, 0f, 0f);
			PartPose pose = PartPose.offsetAndRotation(
				origin[0] - parentOrigin[0],
				parentOrigin[1] - origin[1],
				origin[2] - parentOrigin[2],
				(float) Math.toRadians(rotation[0]),
				(float) Math.toRadians(-rotation[1]),
				(float) Math.toRadians(rotation[2]));

			CubeListBuilder cubes = CubeListBuilder.create();
			JsonArray children = node.getAsJsonArray("children");
			for (JsonElement child : children) {
				if (child.isJsonPrimitive()) {
					JsonObject cube = byUuid.get(child.getAsString());
					if (cube != null) {
						addCube(cubes, cube, origin);
					}
				}
			}
			PartDefinition part = parent.addOrReplaceChild(group.get("name").getAsString(), cubes, pose);
			JsonArray groups = new JsonArray();
			for (JsonElement child : children) {
				if (child.isJsonObject()) {
					groups.add(child);
				}
			}
			addNodes(groups, part, origin);
		}

		private boolean addCube(CubeListBuilder builder, JsonObject cube, float[] origin) {
			String name = cube.has("name") ? cube.get("name").getAsString() : "?";
			boolean boxUv = !cube.has("box_uv") || cube.get("box_uv").getAsBoolean();
			float[] rotation = floats(cube, "rotation", 0f, 0f, 0f);
			if (!boxUv || rotation[0] != 0f || rotation[1] != 0f || rotation[2] != 0f) {
				CevherClientMod.LOGGER.warn("[{}] '{}' küpü desteklenmiyor (kutu UV'siz ya da dönük), atlandı", debugName, name);
				return false;
			}
			float[] from = floats(cube, "from", 0f, 0f, 0f);
			float[] to = floats(cube, "to", 0f, 0f, 0f);
			float[] uv = floats(cube, "uv_offset", 0f, 0f);
			builder.texOffs(Math.round(uv[0]), Math.round(uv[1])).addBox(
				from[0] - origin[0],
				origin[1] - to[1],
				from[2] - origin[2],
				to[0] - from[0],
				to[1] - from[1],
				to[2] - from[2]);
			return true;
		}
	}

	private static float[] floats(JsonObject object, String key, float... fallback) {
		if (!object.has(key) || !object.get(key).isJsonArray()) {
			return fallback;
		}
		JsonArray array = object.getAsJsonArray(key);
		float[] result = new float[array.size()];
		for (int i = 0; i < result.length; i++) {
			result[i] = array.get(i).getAsFloat();
		}
		return result.length >= fallback.length ? result : fallback;
	}
}
