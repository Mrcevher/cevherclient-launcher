package net.cevherclient.client.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.cevherclient.client.module.impl.ActionBarModule;
import net.cevherclient.client.module.impl.ArmorModule;
import net.cevherclient.client.module.impl.ArrayListModule;
import net.cevherclient.client.module.impl.AutoGgModule;
import net.cevherclient.client.module.impl.BiomeModule;
import net.cevherclient.client.module.impl.BlockHighlightModule;
import net.cevherclient.client.module.impl.BossBarModule;
import net.cevherclient.client.module.impl.BowChargeModule;
import net.cevherclient.client.module.impl.CartCounterModule;
import net.cevherclient.client.module.impl.ChatTimestampsModule;
import net.cevherclient.client.module.impl.ClockModule;
import net.cevherclient.client.module.impl.ComboModule;
import net.cevherclient.client.module.impl.CoordinatesModule;
import net.cevherclient.client.module.impl.CpsModule;
import net.cevherclient.client.module.impl.CrosshairModule;
import net.cevherclient.client.module.impl.CustomCrystalModule;
import net.cevherclient.client.module.impl.DayCounterModule;
import net.cevherclient.client.module.impl.DirectionModule;
import net.cevherclient.client.module.impl.FpsModule;
import net.cevherclient.client.module.impl.FreeLookModule;
import net.cevherclient.client.module.impl.FullbrightModule;
import net.cevherclient.client.module.impl.HeldItemModule;
import net.cevherclient.client.module.impl.HideArmorModule;
import net.cevherclient.client.module.impl.HitColorModule;
import net.cevherclient.client.module.impl.HitRangeModule;
import net.cevherclient.client.module.impl.HitboxesModule;
import net.cevherclient.client.module.impl.ItemPhysicModule;
import net.cevherclient.client.module.impl.KeystrokesModule;
import net.cevherclient.client.module.impl.MemoryModule;
import net.cevherclient.client.module.impl.NoHurtCamModule;
import net.cevherclient.client.module.impl.PackDisplayModule;
import net.cevherclient.client.module.impl.ParticlesModule;
import net.cevherclient.client.module.impl.PingModule;
import net.cevherclient.client.module.impl.PlayerModelModule;
import net.cevherclient.client.module.impl.PotionModule;
import net.cevherclient.client.module.impl.ReachModule;
import net.cevherclient.client.module.impl.SaturationModule;
import net.cevherclient.client.module.impl.ScoreboardModule;
import net.cevherclient.client.module.impl.ServerInfoModule;
import net.cevherclient.client.module.impl.SpeedModule;
import net.cevherclient.client.module.impl.TimeChangerModule;
import net.cevherclient.client.module.impl.ToggleSprintModule;
import net.cevherclient.client.module.impl.TotemCounterModule;
import net.cevherclient.client.module.impl.TpsModule;
import net.cevherclient.client.module.impl.VisionModule;
import net.cevherclient.client.module.impl.WatermarkModule;
import net.cevherclient.client.module.impl.WorldTimeModule;
import net.cevherclient.client.module.impl.ZoomModule;

/** Tüm modüllerin kayıt defteri. Sıra, mod menüsündeki kart sırasıdır. */
public final class ModuleManager {
	private static final List<Module> MODULES = new ArrayList<>();

	private ModuleManager() {
	}

	public static void init() {
		MODULES.clear();
		// HUD
		MODULES.add(new WatermarkModule());
		MODULES.add(new FpsModule());
		MODULES.add(new PingModule());
		MODULES.add(new CpsModule());
		MODULES.add(new CoordinatesModule());
		MODULES.add(new ClockModule());
		MODULES.add(new DirectionModule());
		MODULES.add(new MemoryModule());
		MODULES.add(new KeystrokesModule());
		MODULES.add(new ArmorModule());
		MODULES.add(new PotionModule());
		MODULES.add(new TpsModule());
		MODULES.add(new SpeedModule());
		MODULES.add(new BiomeModule());
		MODULES.add(new DayCounterModule());
		MODULES.add(new WorldTimeModule());
		MODULES.add(new SaturationModule());
		MODULES.add(new ServerInfoModule());
		MODULES.add(new ComboModule());
		MODULES.add(new ReachModule());
		MODULES.add(new BowChargeModule());
		MODULES.add(new TotemCounterModule());
		MODULES.add(new CartCounterModule());
		MODULES.add(new ArrayListModule());
		MODULES.add(new PackDisplayModule());
		MODULES.add(new ActionBarModule());
		MODULES.add(new BossBarModule());
		MODULES.add(new ScoreboardModule());
		MODULES.add(new PlayerModelModule());
		// Hareket
		MODULES.add(new ToggleSprintModule());
		// Görsel
		MODULES.add(new ZoomModule());
		MODULES.add(new FreeLookModule());
		MODULES.add(new FullbrightModule());
		MODULES.add(new NoHurtCamModule());
		MODULES.add(new CrosshairModule());
		MODULES.add(new BlockHighlightModule());
		MODULES.add(new HitboxesModule());
		MODULES.add(new HitRangeModule());
		MODULES.add(new HitColorModule());
		MODULES.add(new TimeChangerModule());
		MODULES.add(new ItemPhysicModule());
		MODULES.add(new CustomCrystalModule());
		MODULES.add(new HideArmorModule());
		MODULES.add(new HeldItemModule());
		MODULES.add(new VisionModule());
		MODULES.add(new ParticlesModule());
		// Diğer
		MODULES.add(new ChatTimestampsModule());
		MODULES.add(new AutoGgModule());
	}

	public static List<Module> all() {
		return Collections.unmodifiableList(MODULES);
	}

	public static <T extends Module> T get(Class<T> type) {
		for (Module module : MODULES) {
			if (type.isInstance(module)) {
				return type.cast(module);
			}
		}
		return null;
	}

	public static List<HudModule> enabledHud() {
		List<HudModule> result = new ArrayList<>();
		for (Module module : MODULES) {
			if (module instanceof HudModule hud && hud.isEnabled()) {
				result.add(hud);
			}
		}
		return result;
	}
}
