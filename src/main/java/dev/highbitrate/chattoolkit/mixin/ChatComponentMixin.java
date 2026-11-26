package dev.highbitrate.chattoolkit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.highbitrate.chattoolkit.chat.AnimationGuard;
import dev.highbitrate.chattoolkit.chat.ChatHighlighter;
import dev.highbitrate.chattoolkit.chat.ChatLift;
import dev.highbitrate.chattoolkit.chat.SearchState;
import dev.highbitrate.chattoolkit.config.ChatToolkitConfig;
import dev.highbitrate.chattoolkit.tabs.TabManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Predicate;

// Hooks the vanilla chat so search results (and the open tab) are shown by the vanilla renderer itself.
// That keeps scrolling, clickable links, hover text, Chat Animation and Chat Heads all working on results
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
	// While searching, only matching messages are turned into visible lines
	// Wraps the whole method, including Chat Animation's own hook, so a hidden message doesn't make the chat jump
	@WrapMethod(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V")
	private void chattoolkit$quietHiddenMessages(Component contents, @Nullable MessageSignature signature, GuiMessageSource source,
		@Nullable GuiMessageTag tag, Operation<Void> original) {
		ChatComponent self = (ChatComponent) (Object) this;
		AnimationGuard.before(self);
		try {
			original.call(contents, signature, source, tag);
		} finally {
			AnimationGuard.after(self);
		}
	}

	@WrapOperation(
		method = "refreshTrimmedMessages",
		at = @At(value = "INVOKE", target = "Ljava/util/function/Predicate;test(Ljava/lang/Object;)Z")
	)
	private boolean chattoolkit$searchFilter(Predicate<Object> filter, Object message, Operation<Boolean> original) {
		GuiMessage guiMessage = (GuiMessage) message;
		return original.call(filter, message) && SearchState.isVisible(guiMessage) && TabManager.showsInMain(guiMessage);
	}

	// New messages arriving during a search are still stored (and logged), just not shown unless they match
	@WrapOperation(
		method = "addMessage",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/ChatComponent;addMessageToDisplayQueue(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V"
		)
	)
	private void chattoolkit$showIfMatching(ChatComponent chat, GuiMessage message, Operation<Void> original) {
		TabManager.onNewMessage(message);
		if (SearchState.isVisible(message) && TabManager.showsInMain(message)) {
			original.call(chat, message);
		} else {
			AnimationGuard.markHidden();
		}
		SearchState.onMessageAdded();
	}

	// Longer chat history (vanilla keeps 100 messages), so there is more to search through
	@ModifyConstant(method = "addMessageToQueue", constant = @Constant(intValue = 100), require = 0)
	private int chattoolkit$messageHistory(int vanilla) {
		return Math.max(vanilla, ChatToolkitConfig.get().historySize);
	}

	@ModifyConstant(method = "addMessageToDisplayQueue", constant = @Constant(intValue = 100), require = 0)
	private int chattoolkit$lineHistory(int vanilla) {
		return Math.max(vanilla, ChatToolkitConfig.get().historySize * 3);
	}

	@Inject(
		method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
		at = @At("HEAD")
	)
	private void chattoolkit$beginPass(GuiGraphicsExtractor graphics, Font font, int ticks, int mouseX, int mouseY,
		ChatComponent.DisplayMode displayMode, boolean changeCursorOnInsertions, CallbackInfo ci) {
		ChatLift.update();
		ChatHighlighter.beginPass(displayMode.foreground);
	}

	// Moves the whole chat up (drawing, hover and clicks all go through here)
	@ModifyVariable(
		method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",
		at = @At("HEAD"),
		argsOnly = true,
		ordinal = 0
	)
	private int chattoolkit$liftChat(int screenHeight) {
		return screenHeight - ChatLift.get();
	}

	@Inject(
		method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
		at = @At("RETURN")
	)
	private void chattoolkit$endPass(GuiGraphicsExtractor graphics, Font font, int ticks, int mouseX, int mouseY,
		ChatComponent.DisplayMode displayMode, boolean changeCursorOnInsertions, CallbackInfo ci) {
		ChatHighlighter.endPass();
	}
}
