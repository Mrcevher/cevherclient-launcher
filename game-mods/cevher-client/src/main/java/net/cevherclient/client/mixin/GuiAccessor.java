package net.cevherclient.client.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Eylem çubuğu modülü, oyunun eylem çubuğu mesajını kendi konumunda çizmek için okur. */
@Mixin(Gui.class)
public interface GuiAccessor {
	@Accessor("overlayMessageString")
	Component cevher$overlayMessage();

	@Accessor("overlayMessageTime")
	int cevher$overlayMessageTime();

	@Accessor("animateOverlayMessageColor")
	boolean cevher$animateOverlayMessageColor();
}
