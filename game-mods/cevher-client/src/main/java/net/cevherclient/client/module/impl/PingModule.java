package net.cevherclient.client.module.impl;

import net.cevherclient.client.module.TextHudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

public final class PingModule extends TextHudModule {
	public PingModule() {
		super("ping", "Ping Görüntüsü", "MS", false, 0.005f, 0.07f);
	}

	@Override
	protected String text(Minecraft mc, boolean preview) {
		if (mc.player == null || mc.getConnection() == null) {
			return "Ping: 42 ms";
		}
		PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
		return info == null ? "Ping: - ms" : "Ping: " + info.getLatency() + " ms";
	}
}
