package net.cevherclient.session.mixin;

import net.cevherclient.session.JoinProof;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.cookie.ClientboundCookieRequestPacket;
import net.minecraft.network.protocol.cookie.ServerboundCookieResponsePacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Configuration / oyun aşamasındaki cookie isteğine bilet kanıtıyla yanıt verir.
 *
 * Sunucu bir Velocity proxy'sinin arkasındaysa oyuncu proxy ile login'i çoktan bitirmiştir;
 * CevherAuth'un arka sunucuda login sırasında istediği {@code cevherauth:proof/<nonce>}
 * cookie'si, Velocity tarafından istemciye configuration aşamasında iletilir. Login aşamasını
 * {@link ClientHandshakePacketListenerImplMixin} karşılar; bu mixin geri kalan aşamaları.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerImplMixin {
	@Shadow
	@Final
	protected Connection connection;

	@Inject(method = "handleRequestCookie", at = @At("HEAD"), cancellable = true)
	private void cevher$joinProof(ClientboundCookieRequestPacket packet, CallbackInfo ci) {
		byte[] proof = JoinProof.respond(String.valueOf(packet.key()));
		if (proof != null) {
			this.connection.send(new ServerboundCookieResponsePacket(packet.key(), proof));
			ci.cancel();
		}
	}
}
