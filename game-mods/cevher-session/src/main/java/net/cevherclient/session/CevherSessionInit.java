package net.cevherclient.session;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Açılışta launcher'ın bıraktığı bilet sırrını ve önceden kurulan sunucu paketinin kaydını okur
 * (yalnızca Cevherclient ile açılan oyunda).
 */
public final class CevherSessionInit implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		if (CevherSession.EXIT_ON_LEAVE) {
			JoinProof.load(FabricLoader.getInstance().getGameDir());
			ServerPack.load(FabricLoader.getInstance().getGameDir());
		}
	}
}
