package net.cevherclient.client.mixin;

import net.cevherclient.client.ui.ServerTps;
import net.cevherclient.client.ui.TotemPops;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Shadow
	private ClientLevel level;

	/** TPS tahmini için saat paketlerinin geliş zamanı. Paket ana iş parçacığına aktarıldıktan sonra sayılır. */
	@Inject(method = "handleSetTime", at = @At("TAIL"))
	private void cevher$time(ClientboundSetTimePacket packet, CallbackInfo ci) {
		ServerTps.onTimePacket(packet.gameTime());
	}

	/** 35 numaralı varlık olayı totem patlamasıdır. */
	@Inject(method = "handleEntityEvent", at = @At("TAIL"))
	private void cevher$totemPop(ClientboundEntityEventPacket packet, CallbackInfo ci) {
		if (packet.getEventId() == 35 && level != null) {
			Entity entity = packet.getEntity(level);
			if (entity != null) {
				TotemPops.onPop(entity);
			}
		}
	}
}
