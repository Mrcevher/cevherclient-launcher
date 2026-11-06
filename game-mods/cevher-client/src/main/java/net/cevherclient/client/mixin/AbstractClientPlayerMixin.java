package net.cevherclient.client.mixin;

import net.cevherclient.client.cosmetic.Cosmetic;
import net.cevherclient.client.cosmetic.CosmeticSelection;
import net.cevherclient.client.cosmetic.RemoteCosmetics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pelerin kozmetiği: oyuncunun derisindeki pelerin dokusu seçilen pelerinle değiştirilir. Elytra dokusu boş
 * bırakılır; oyun bu durumda elytrayı da pelerin dokusuyla çizer. Hareketli pelerinlerde her çağrıda o anki kare
 * verilir. Oyunun "pelerin göster" ayarı geçerliliğini korur.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void cevher$cape(CallbackInfoReturnable<PlayerSkin> cir) {
		AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
		Minecraft mc = Minecraft.getInstance();
		Cosmetic cosmetic;
		if (self == mc.player) {
			cosmetic = CosmeticSelection.get(Cosmetic.Slot.CAPE);
		} else {
			cosmetic = CosmeticSelection.showOthers() ? RemoteCosmetics.get(self.getUUID(), Cosmetic.Slot.CAPE) : null;
		}
		if (cosmetic instanceof Cosmetic.Cape cape) {
			PlayerSkin skin = cir.getReturnValue();
			cir.setReturnValue(new PlayerSkin(skin.body(), cape.frame(Util.getMillis()), null, skin.model(), skin.secure()));
		}
	}
}
