package net.cevherclient.client;

import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/** Son bağlanılan sunucu; bağlantı kopma ekranındaki "Yeniden Bağlan" düğmesi bunu kullanır. */
public final class LastServer {
	private static ServerAddress address;
	private static ServerData data;

	private LastServer() {
	}

	public static void remember(ServerAddress newAddress, ServerData newData) {
		address = newAddress;
		data = newData;
	}

	public static void clear() {
		address = null;
		data = null;
	}

	public static boolean isKnown() {
		return address != null;
	}

	public static ServerAddress address() {
		return address;
	}

	public static ServerData data() {
		return data;
	}
}
