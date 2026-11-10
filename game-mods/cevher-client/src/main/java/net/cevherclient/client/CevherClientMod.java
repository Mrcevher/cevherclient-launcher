package net.cevherclient.client;

import net.cevherclient.client.config.ClientConfig;
import net.cevherclient.client.cosmetic.CosmeticLayer;
import net.cevherclient.client.cosmetic.CosmeticNet;
import net.cevherclient.client.module.Module;
import net.cevherclient.client.module.ModuleManager;
import net.cevherclient.client.module.impl.ActionBarModule;
import net.cevherclient.client.module.impl.AutoGgModule;
import net.cevherclient.client.module.impl.BlockHighlightModule;
import net.cevherclient.client.module.impl.BossBarModule;
import net.cevherclient.client.module.impl.CrosshairModule;
import net.cevherclient.client.module.impl.HitRangeModule;
import net.cevherclient.client.module.impl.HitboxesModule;
import net.cevherclient.client.module.impl.ScoreboardModule;
import net.cevherclient.client.screen.HudEditorScreen;
import net.cevherclient.client.screen.SaoMenuScreen;
import net.cevherclient.client.sinif.SinifNet;
import net.cevherclient.client.ui.ClickTracker;
import net.cevherclient.client.ui.CombatTracker;
import net.cevherclient.client.ui.HudRenderer;
import net.cevherclient.client.ui.ServerTps;
import net.cevherclient.client.ui.TotemPops;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cevherclient istemci modu: Sağ Shift ile açılan mod menüsü, HUD modülleri ve HUD düzenleyici.
 * Yalnızca istemci tarafında çalışır. Sunucuya yalnızca kozmetik seçimi ({@link CosmeticNet}, sunucu kanalı
 * dinliyorsa), sınıf ekranlarındaki istekler ({@link SinifNet}, SinifSistemi eklentisi varsa) ve oyuncu açarsa
 * Otomatik GG'nin sohbet mesajı gider.
 */
public final class CevherClientMod implements ClientModInitializer {
	public static final String MOD_ID = "cevher_client";
	public static final Logger LOGGER = LoggerFactory.getLogger("CevherClient");

	@Override
	public void onInitializeClient() {
		ModuleManager.init();
		ClientConfig.load();
		Keys.register();
		CosmeticNet.init();
		SinifNet.init();
		CombatTracker.init();

		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(MOD_ID, "hud"),
			(graphics, deltaTracker) -> {
				Minecraft mc = Minecraft.getInstance();
				// Düzenleyici modülleri kendisi çizer; burada çizilirse iki kez görünürdü.
				if (!(mc.screen instanceof HudEditorScreen)) {
					HudRenderer.render(graphics, mc);
				}
			});
		// Yerini değiştiren modüller açıkken oyunun kendi öğesi çizilmez.
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (graphics, deltaTracker) -> {
			if (CrosshairModule.replacesVanilla()) {
				CrosshairModule.render(graphics, Minecraft.getInstance());
			} else {
				vanilla.render(graphics, deltaTracker);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.OVERLAY_MESSAGE, vanilla -> unless(ActionBarModule::replacesVanilla, vanilla));
		HudElementRegistry.replaceElement(VanillaHudElements.BOSS_BAR, vanilla -> unless(BossBarModule::replacesVanilla, vanilla));
		HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD, vanilla -> unless(ScoreboardModule::replacesVanilla, vanilla));

		WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register(BlockHighlightModule::render);
		WorldRenderEvents.AFTER_ENTITIES.register(context -> {
			HitboxesModule.render(context);
			HitRangeModule.render(context);
		});

		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				AutoGgModule.onMessage(message);
			}
		});
		ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, timestamp) -> AutoGgModule.onMessage(message));

		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatarRenderer) {
				helper.register(new CosmeticLayer(avatarRenderer));
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(CevherClientMod::tick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ClientConfig.saveIfDirty());
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			ServerTps.reset();
			TotemPops.clear();
			// Tek oyunculu dünyaya girilince eski sunucu hedefi, kopma ekranında yanlışlıkla sunulmasın.
			if (client.isLocalServer()) {
				LastServer.clear();
			}
		});

		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof PauseScreen) {
				Screens.getButtons(screen).add(Button.builder(Component.literal("Mod Menüsü"),
					button -> client.setScreen(new SaoMenuScreen(screen))).bounds(6, height - 26, 90, 20).build());
			}
		});
	}

	/** {@code hidden} true iken hiçbir şey çizmeyen, değilse oyunun öğesini çizen HUD öğesi. */
	private static HudElement unless(java.util.function.BooleanSupplier hidden, HudElement vanilla) {
		return (graphics, deltaTracker) -> {
			if (!hidden.getAsBoolean()) {
				vanilla.render(graphics, deltaTracker);
			}
		};
	}

	private static void tick(Minecraft mc) {
		TotemPops.tick(mc);
		if (mc.player == null) {
			return;
		}
		ClickTracker.poll(mc);
		CombatTracker.tick(mc);
		while (Keys.MENU.consumeClick()) {
			if (mc.screen == null) {
				mc.setScreen(new SaoMenuScreen(null));
			}
		}
		for (Module module : ModuleManager.all()) {
			if (module.isEnabled()) {
				module.onTick(mc);
			}
		}
	}
}
