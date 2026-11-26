package dev.highbitrate.chattoolkit.chat;

import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

// Extra data mixed into every GuiMessage
public interface MessageExtras {
	// Wall-clock time (epoch millis) the message was added to the chat
	long chattoolkit$receivedAt();

	// The message as plain text (cached)
	String chattoolkit$plainText();

	// Which tabs the message belongs to (bit per tab), or -1 if it was worked out for an older set of rules
	int chattoolkit$tabMask(int version);

	void chattoolkit$setTabMask(int version, int mask);

	// Set when this message replaced one or more identical earlier ones: the original text
	// (without the "(x3)" counter), how many times it came in, and when the first one arrived
	@Nullable Component chattoolkit$stackBase();

	int chattoolkit$stackCount();

	long chattoolkit$stackFirst();

	void chattoolkit$setStack(Component base, int count, long first);

	static MessageExtras of(GuiMessage message) {
		return (MessageExtras) (Object) message;
	}

	static String plain(GuiMessage message) {
		return of(message).chattoolkit$plainText();
	}

	// The message as it was sent, without our repeat counter
	static Component content(GuiMessage message) {
		Component base = of(message).chattoolkit$stackBase();
		return base != null ? base : message.content();
	}

	static long receivedAt(GuiMessage message) {
		return of(message).chattoolkit$receivedAt();
	}
}
