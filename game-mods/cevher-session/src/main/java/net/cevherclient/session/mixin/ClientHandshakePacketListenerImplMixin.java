package net.cevherclient.session.mixin;

import net.cevherclient.session.JoinProof;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
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
 * Login aşamasında CevherAuth'un {@code cevherauth:proof/<nonce>} cookie isteğine bilet
 * kanıtıyla yanıt verir (bkz. {@link JoinProof}). Diğer cookie istekleri vanilla gibi işlenir.
 */
@Mixin(ClientHandshakePacketListenerImpl.class)
public abstract class ClientHandshakePacketListenerImplMixin {
	@Shadow
	@Final
	private Connection connection;

	@Inject(method = "handleRequestCookie", at = @At("HEAD"), cancellable = true)
	private void cevher$joinProof(ClientboundCookieRequestPacket packet, CallbackInfo ci) {
		byte[] proof = JoinProof.respond(String.valueOf(packet.key()));
		if (proof != null) {
			this.connection.send(new ServerboundCookieResponsePacket(packet.key(), proof));
			ci.cancel();
		}
	}
}
