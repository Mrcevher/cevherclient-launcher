package net.cevherclient.client.module.impl;

import net.cevherclient.client.module.Option;
import net.cevherclient.client.module.TextHudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/** Bağlı olunan sunucunun adresi ve çevrimiçi oyuncu sayısı. */
public final class ServerInfoModule extends TextHudModule {
	private final Option.Bool showPlayers = add(new Option.Bool("players", "Oyuncu sayısı", true));

	public ServerInfoModule() {
		super("server_info", "Sunucu Bilgisi", "SRV", false, 0.16f, 0.36f);
	}

	@Override
	protected String text(Minecraft mc, boolean preview) {
		String address;
		int players;
		if (preview || mc.getConnection() == null) {
			address = "oyna.sunucu.com";
			players = 24;
		} else {
			ServerData data = mc.getCurrentServer();
			address = mc.isLocalServer() ? "Tek oyunculu" : data != null ? data.ip : "?";
			players = mc.getConnection().getOnlinePlayers().size();
		}
		return showPlayers.value ? address + " | " + players + " oyuncu" : address;
	}
}
