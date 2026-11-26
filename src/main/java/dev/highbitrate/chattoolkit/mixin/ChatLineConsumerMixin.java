package dev.highbitrate.chattoolkit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.highbitrate.chattoolkit.chat.ChatHighlighter;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// The anonymous line consumer inside ChatComponent#extractRenderState draws one chat line at a time.
// Just before each line's text we paint our hover tint and search-match highlights underneath it,
// in the same transform as the line, so they follow scrolling, chat scale and Chat Animation's slide
@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$1")
public abstract class ChatLineConsumerMixin {
	@WrapOperation(
		method = "accept",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z"
		),
		require = 0
	)
	private boolean chattoolkit$paintHighlights(ChatComponent.ChatGraphicsAccess access, int textTop, float opacity,
		FormattedCharSequence text, Operation<Boolean> original, GuiMessage.Line line) {
		ChatHighlighter.beforeLine(access, line, textTop, opacity);
		return original.call(access, textTop, opacity, text);
	}
}
