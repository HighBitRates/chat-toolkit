package dev.highbitrate.chattoolkit.chat;

import dev.highbitrate.chattoolkit.config.ChatToolkitConfig;
import dev.highbitrate.chattoolkit.mixin.ChatComponentAccessor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

// "You cannot interact with this block!" twelve times in a row turns into one line ending in (x12).
// When a message comes in that matches one of the last few, the old copy is taken out and the
// new one gets the counter, so it lands at the bottom where you'd expect it.
public final class MessageStacker {
	// how far back to look for a match, so a repeat still stacks if someone talked in between
	private static final int LOOK_BACK = 8;
	private static final int COUNT_COLOR = 0x969696;

	private static @Nullable Component pendingBase;
	private static int pendingCount;
	private static long pendingFirst;

	private MessageStacker() {
	}

	// Called before vanilla builds the message. Returns what should actually be shown
	public static Component prepare(ChatComponent chat, Component contents) {
		pendingBase = null;
		ChatToolkitConfig config = ChatToolkitConfig.get();
		if (!config.stackMessages) {
			return contents;
		}
		ChatComponentAccessor access = ChatHelper.access(chat);
		List<GuiMessage> all = access.chattoolkit$getAllMessages();
		long now = System.currentTimeMillis();
		long window = config.stackWindowSeconds * 1000L;
		GuiMessage match = null;
		for (int i = 0; i < Math.min(LOOK_BACK, all.size()); i++) {
			GuiMessage old = all.get(i);
			if (window > 0 && now - MessageExtras.receivedAt(old) > window) {
				break;
			}
			if (MessageExtras.content(old).equals(contents)) {
				match = old;
				break;
			}
		}
		if (match == null) {
			return contents;
		}

		MessageExtras extras = MessageExtras.of(match);
		int count = extras.chattoolkit$stackCount() + 1;
		long first = extras.chattoolkit$stackFirst();
		GuiMessage old = match;
		all.removeIf(m -> m == old);
		int removedLines = 0;
		List<GuiMessage.Line> lines = access.chattoolkit$getTrimmedMessages();
		for (int i = lines.size() - 1; i >= 0; i--) {
			if (lines.get(i).parent() == old) {
				lines.remove(i);
				removedLines++;
			}
		}
		// if you're scrolled up, don't let the chat shift under you
		int scroll = access.chattoolkit$getScrollPos();
		if (scroll > 0 && removedLines > 0) {
			access.chattoolkit$setScrollPos(Math.max(0, scroll - removedLines));
		}

		pendingBase = contents;
		pendingCount = count;
		pendingFirst = first;
		return contents.copy().append(counter(count, first));
	}

	// Called with the message vanilla just built from what prepare() returned
	public static void attach(GuiMessage message) {
		if (pendingBase != null) {
			MessageExtras.of(message).chattoolkit$setStack(pendingBase, pendingCount, pendingFirst);
			pendingBase = null;
		}
	}

	public static void clear() {
		pendingBase = null;
	}

	private static Component counter(int count, long first) {
		String pattern = ChatToolkitConfig.get().clock24h ? "HH:mm" : "h:mm a";
		String since = DateTimeFormatter.ofPattern(pattern, Locale.ROOT).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(first));
		Component hover = Component.translatable("chattoolkit.stack.hover", count, since);
		Style style = Style.EMPTY.withColor(COUNT_COLOR).withBold(false).withItalic(false).withUnderlined(false)
			.withStrikethrough(false).withObfuscated(false).withHoverEvent(new HoverEvent.ShowText(hover));
		return Component.literal("  (x" + count + ")").withStyle(style);
	}
}
