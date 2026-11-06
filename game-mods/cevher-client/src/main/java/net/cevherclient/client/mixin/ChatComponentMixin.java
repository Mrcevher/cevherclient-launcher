package net.cevherclient.client.mixin;

import net.cevherclient.client.module.impl.ChatTimestampsModule;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Sohbet Saati: sohbete eklenen her mesajın başına saat yazılır. */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
	@ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;"
		+ "Lnet/minecraft/client/GuiMessageTag;)V", at = @At("HEAD"), argsOnly = true)
	private Component cevher$timestamp(Component message) {
		return ChatTimestampsModule.decorate(message);
	}
}
