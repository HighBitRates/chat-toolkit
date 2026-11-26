package dev.highbitrate.chattoolkit.chat;

import net.minecraft.client.multiplayer.chat.GuiMessage;

// Extra data mixed into every GuiMessage
public interface MessageExtras {
	// Wall-clock time (epoch millis) the message was added to the chat
	long chattoolkit$receivedAt();

	// The message as plain text (cached)
	String chattoolkit$plainText();

	// Which tabs the message belongs to (bit per tab), or -1 if it was worked out for an older set of rules
	int chattoolkit$tabMask(int version);

	void chattoolkit$setTabMask(int version, int mask);

	static MessageExtras of(GuiMessage message) {
		return (MessageExtras) (Object) message;
	}

	static String plain(GuiMessage message) {
		return of(message).chattoolkit$plainText();
	}

	static long receivedAt(GuiMessage message) {
		return of(message).chattoolkit$receivedAt();
	}
}
