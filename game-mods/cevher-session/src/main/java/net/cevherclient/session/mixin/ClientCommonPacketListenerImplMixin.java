package net.cevherclient.session.mixin;

import java.util.UUID;
import net.cevherclient.session.CevherSession;
import net.cevherclient.session.JoinProof;
import net.cevherclient.session.ServerPack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.cookie.ClientboundCookieRequestPacket;
import net.minecraft.network.protocol.cookie.ServerboundCookieResponsePacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Configuration / oyun aşamasında:
 *
 * <ul>
 *   <li>Cookie isteğine bilet kanıtıyla yanıt verir. Sunucu bir Velocity proxy'sinin arkasındaysa
 *   oyuncu proxy ile login'i çoktan bitirmiştir; CevherAuth'un arka sunucuda login sırasında
 *   istediği {@code cevherauth:proof/<nonce>} cookie'si, Velocity tarafından istemciye
 *   configuration aşamasında iletilir. Login aşamasını {@link ClientHandshakePacketListenerImplMixin}
 *   karşılar; bu mixin geri kalan aşamaları.</li>
 *   <li>Sunucu kaynak paketini onay ekranı göstermeden kabul eder. Paket launcher'ın önceden
 *   kurduğu paketse ({@link ServerPack}) indirmeden ve kaynakları yeniden yüklemeden
 *   "yüklendi" yanıtı verir; girişte oyun kasmaz.</li>
 * </ul>
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerImplMixin {
	@Shadow
	@Final
	protected Connection connection;

	@Shadow
	@Final
	protected Minecraft minecraft;

	@Shadow
	@Final
	protected ServerData serverData;

	@Inject(method = "handleRequestCookie", at = @At("HEAD"), cancellable = true)
	private void cevher$joinProof(ClientboundCookieRequestPacket packet, CallbackInfo ci) {
		byte[] proof = JoinProof.respond(String.valueOf(packet.key()));
		if (proof != null) {
			this.connection.send(new ServerboundCookieResponsePacket(packet.key(), proof));
			ci.cancel();
		}
	}

	/** Ana iş parçacığına geçildikten sonra (ensureRunningOnSameThread) çalışır. */
	@Inject(
		method = "handleResourcePackPush",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
			shift = At.Shift.AFTER
		),
		cancellable = true
	)
	private void cevher$serverPack(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
		if (!CevherSession.EXIT_ON_LEAVE) {
			return;
		}
		if (ServerPack.isPreinstalled(packet.hash())) {
			UUID id = packet.id();
			this.connection.send(new ServerboundResourcePackPacket(id, ServerboundResourcePackPacket.Action.ACCEPTED));
			this.connection.send(new ServerboundResourcePackPacket(id, ServerboundResourcePackPacket.Action.DOWNLOADED));
			this.connection.send(new ServerboundResourcePackPacket(id, ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
			ci.cancel();
			return;
		}
		// Önceden kurulu değil (ör. paket oyun açıkken değişti): onay sormadan normal yoldan indir.
		ServerData data = this.serverData;
		if (data != null) {
			ServerData.ServerPackStatus status = data.getResourcePackStatus();
			if (status == ServerData.ServerPackStatus.PROMPT || (packet.required() && status == ServerData.ServerPackStatus.DISABLED)) {
				data.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);
				ServerList.saveSingleServer(data);
				this.minecraft.getDownloadedPackSource().allowServerPacks();
			}
		}
	}
}
