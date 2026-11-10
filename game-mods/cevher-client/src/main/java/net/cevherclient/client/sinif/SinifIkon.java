package net.cevherclient.client.sinif;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Yetenek ikonları. Dokular sunucunun kaynak paketinden (Nexo) okunur; kimlik → doku eşlemesi burada. Paket henüz
 * yüklenmediyse ya da doku yoksa {@code null} döner (çizim boş kutuya düşer) ve sonraki karede yeniden denenir.
 */
public final class SinifIkon {
	private static final Map<String, String> YOL = new HashMap<>();
	private static final Map<String, BufferedImage> ONBELLEK = new HashMap<>();

	static {
		String w = "minecraft:textures/warrior_texture/icon_warrior_";
		YOL.put("WARRIOR_RAGE", w + "rage.png");
		YOL.put("WARRIOR_FURIOUSSLASH", w + "furious_slash.png");
		YOL.put("WARRIOR_FURIOUSSTRIKE", w + "furious_strike.png");
		YOL.put("WARRIOR_LEAPINGSLASH", w + "leaping_slash.png");
		YOL.put("WARRIOR_WHIRLWIND", w + "whirlwind.png");
		YOL.put("WARRIOR_HELLBLADE", w + "hellblade.png");
		String a = "minecraft:textures/archer_texture/icon_archer_";
		YOL.put("ARCHER_AEROGLAIVE", a + "aeroglaive.png");
		YOL.put("ARCHER_CYCLONE_SHOT", a + "cycloneshot.png");
		YOL.put("ARCHER_QUICKSHOT", a + "quickshot.png");
		YOL.put("ARCHER_RAINOFARROWS", a + "rainofarrows.png");
		YOL.put("ARCHER_VOLLEYSTEP", a + "volleystep.png");
		YOL.put("ARCHER_VOLLEYSTEP_STACKS", a + "volleystep_stacks.png");
		YOL.put("ARCHER_WINDWALKER", a + "windwalker.png");
		String s = "assassin:textures/assassin_texture/icon_";
		YOL.put("BLADE_SLASH", s + "blade_slash.png");
		YOL.put("DASH", s + "dash.png");
		YOL.put("DAGGER_THROW", s + "dagger_throw.png");
		YOL.put("SHADOW_WALK", s + "shadow_walk.png");
		YOL.put("BLADE_DANCE", s + "blade_dance.png");
		YOL.put("ASSASSINS_BLADE", s + "assassins_blade.png");
	}

	private SinifIkon() {
	}

	/** Ikon varsa görüntüsü, yoksa {@code null}. Bulunamayanlar da önbelleğe alınır; {@link #sifirla} ile yenilenir. */
	public static BufferedImage al(String id) {
		String yol = YOL.get(id == null ? "" : id.toUpperCase());
		if (yol == null) {
			return null;
		}
		if (ONBELLEK.containsKey(yol)) {
			return ONBELLEK.get(yol);
		}
		BufferedImage img = null;
		try {
			Identifier kimlik = Identifier.parse(yol);
			var kaynak = Minecraft.getInstance().getResourceManager().getResource(kimlik);
			if (kaynak.isPresent()) {
				try (InputStream in = kaynak.get().open()) {
					img = ImageIO.read(in);
				}
			}
		} catch (Exception e) {
			img = null;
		}
		ONBELLEK.put(yol, img);
		return img;
	}

	/** Kaynak paketi yeniden yüklenince çağrılabilir. */
	public static void sifirla() {
		ONBELLEK.clear();
	}
}
