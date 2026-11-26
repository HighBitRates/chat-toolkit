package dev.highbitrate.chattoolkit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.highbitrate.chattoolkit.chat.ChatScreenController;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.highbitrate.chattoolkit.chat.TypingAids;
import dev.highbitrate.chattoolkit.config.ChatToolkitConfig;
import dev.highbitrate.chattoolkit.tabs.TabManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Stops vanilla chat hover tooltips from popping up underneath our right-click menu,
// sends typed messages through the open tab's "send messages as" command, and keeps unsent drafts
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
	// set when the chat is closed on purpose (Escape), as opposed to sending or being interrupted
	@Unique
	private boolean chattoolkit$closedByPlayer;

	@Inject(method = "onClose", at = @At("HEAD"))
	private void chattoolkit$closing(CallbackInfo ci) {
		chattoolkit$closedByPlayer = true;
	}

	@ModifyVariable(method = "handleChatInput", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private String chattoolkit$sendThroughTab(String message) {
		TypingAids.onSent(message);
		return TabManager.transformOutgoing(message);
	}

	// Pressing Escape keeps what you typed for next time (vanilla only does this with its own option on)
	@ModifyReturnValue(method = "shouldDiscardDraft", at = @At("RETURN"))
	private boolean chattoolkit$keepDraft(boolean discard) {
		if (discard && chattoolkit$closedByPlayer && ChatToolkitConfig.get().keepDrafts) {
			return false;
		}
		return discard;
	}

	@WrapOperation(
		method = "extractRenderState",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/ChatComponent;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V"
		),
		require = 0
	)
	private void chattoolkit$hideHoverUnderMenu(ChatComponent chat, GuiGraphicsExtractor graphics, Font font, int ticks, int mouseX, int mouseY,
		ChatComponent.DisplayMode displayMode, boolean changeCursorOnInsertions, Operation<Void> original) {
		ChatScreenController controller = ChatScreenController.current();
		if (controller != null && controller.capturesMouse(mouseX, mouseY)) {
			mouseX = -10_000;
			mouseY = -10_000;
		}
		original.call(chat, graphics, font, ticks, mouseX, mouseY, displayMode, changeCursorOnInsertions);
	}
}
