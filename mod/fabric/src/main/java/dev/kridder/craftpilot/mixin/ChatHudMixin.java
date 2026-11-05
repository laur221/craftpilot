package dev.kridder.craftpilot.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.kridder.craftpilot.ChatLog;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;

@Mixin(ChatHud.class)
public class ChatHudMixin {
	// addMessage(Text) delegates to this overload, so hooking it covers both.
	@Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At("HEAD"))
	private void craftpilot$capture(Text message, MessageSignatureData sig, MessageIndicator ind, CallbackInfo ci) {
		ChatLog.add(message.getString());
	}
}
