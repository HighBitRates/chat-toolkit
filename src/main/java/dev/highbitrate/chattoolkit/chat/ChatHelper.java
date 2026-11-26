package dev.highbitrate.chattoolkit.chat;

import dev.highbitrate.chattoolkit.mixin.ChatComponentAccessor;
import dev.highbitrate.chattoolkit.tabs.TabManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;

// Small helpers around the vanilla chat component
public final class ChatHelper {
	private ChatHelper() {
	}

	public static @Nullable ChatComponent chat() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui == null || mc.gui.hud == null) {
			return null;
		}
		return mc.gui.hud.getChat();
	}

	public static ChatComponentAccessor access(ChatComponent chat) {
		return (ChatComponentAccessor) chat;
	}

	public static List<GuiMessage> allMessages() {
		ChatComponent chat = chat();
		return chat == null ? List.of() : access(chat).chattoolkit$getAllMessages();
	}

	// Prints a client-side message to chat (only you can see it)
	public static void print(Component message) {
		ChatComponent chat = chat();
		if (chat != null) {
			chat.addClientSystemMessage(message);
		}
	}

	public static void copyToClipboard(String text) {
		Minecraft.getInstance().keyboardHandler.setClipboard(text);
	}

	// Takes one message out of the chat history (only on your screen), keeping the scroll position
	public static boolean removeMessage(GuiMessage message) {
		ChatComponent chat = chat();
		if (chat == null) {
			return false;
		}
		ChatComponentAccessor access = access(chat);
		List<GuiMessage> all = access.chattoolkit$getAllMessages();
		boolean removed = false;
		// GuiMessage is a record, so match by identity in case two messages look the same
		for (int i = 0; i < all.size(); i++) {
			if (all.get(i) == message) {
				all.remove(i);
				removed = true;
				break;
			}
		}
		if (!removed) {
			return false;
		}
		int scroll = access.chattoolkit$getScrollPos();
		SearchState.refreshChat();
		int max = Math.max(0, access.chattoolkit$getTrimmedMessages().size() - chat.getLinesPerPage());
		access.chattoolkit$setScrollPos(Math.min(scroll, max));
		TabManager.onMessagesChanged();
		return true;
	}
}
