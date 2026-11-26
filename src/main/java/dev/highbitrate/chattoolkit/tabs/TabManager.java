package dev.highbitrate.chattoolkit.tabs;

import dev.highbitrate.chattoolkit.chat.MessageExtras;
import dev.highbitrate.chattoolkit.chat.SearchState;
import dev.highbitrate.chattoolkit.config.ChatToolkitConfig;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.jspecify.annotations.Nullable;

import java.util.List;

// Runtime side of chat tabs: which tab is open, where typed messages go, unread dots,
// and which tabs each message belongs to.
public final class TabManager {
	// bumped whenever tabs or rules change, so cached per-message results get recomputed
	private static int version;
	// bumped whenever a message arrives, windows use it to know when to re-wrap
	private static int messages;
	private static @Nullable ChatTab active;
	private static @Nullable ChatTab target;

	private TabManager() {
	}

	public static boolean enabled() {
		return ChatToolkitConfig.get().tabsEnabled;
	}

	public static List<ChatTab> tabs() {
		return TabStore.active().tabs;
	}

	public static int version() {
		return version;
	}

	public static int messagesVersion() {
		return messages;
	}

	// Start fresh after loading or switching presets
	static void reset() {
		version++;
		messages++;
		target = null;
		active = null;
		long now = System.currentTimeMillis();
		for (ChatTab tab : tabs()) {
			tab.unread = false;
			tab.scroll = 0;
			tab.lastViewed = now;
			tab.separatorAfter = 0;
		}
		fixActive();
		SearchState.refreshChat();
	}

	// Call after editing tabs or rules
	public static void changed() {
		version++;
		messages++;
		fixActive();
		if (target != null && (!tabs().contains(target) || !target.popped)) {
			target = null;
		}
		SearchState.refreshChat();
	}

	private static void fixActive() {
		List<ChatTab> tabs = tabs();
		if (active != null && !active.popped && tabs.contains(active)) {
			return;
		}
		active = null;
		for (ChatTab tab : tabs) {
			if (!tab.popped) {
				active = tab;
				return;
			}
		}
	}

	public static @Nullable ChatTab activeTab() {
		fixActive();
		return active;
	}

	public static void select(ChatTab tab) {
		if (!tabs().contains(tab) || tab.popped) {
			return;
		}
		boolean hadUnread = tab.unread;
		tab.unread = false;
		target = null;
		if (tab != active) {
			leave(active);
			tab.separatorAfter = hadUnread && tab.lastViewed > 0 && ChatToolkitConfig.get().unreadSeparator ? tab.lastViewed : 0;
			active = tab;
			SearchState.refreshChat();
		}
	}

	private static void leave(@Nullable ChatTab tab) {
		if (tab != null) {
			tab.lastViewed = System.currentTimeMillis();
			tab.separatorAfter = 0;
		}
	}

	// The chat was closed: whatever was open counts as read up to now
	public static void onChatClosed() {
		leave(activeTab());
	}

	// Messages newer than this get the "new" line above them in the open tab (0 = no line)
	public static long separatorAfter() {
		if (!enabled() || !ChatToolkitConfig.get().unreadSeparator) {
			return 0;
		}
		ChatTab open = activeTab();
		return open == null ? 0 : open.separatorAfter;
	}

	public static void popOut(ChatTab tab) {
		if (tab.popped) {
			return;
		}
		tab.popped = true;
		tab.unread = false;
		tab.scroll = 0;
		changed();
		TabStore.save();
	}

	// Puts a window back in the tab bar, at the given position (or the end)
	public static void dock(ChatTab tab, int insertAt) {
		List<ChatTab> tabs = tabs();
		if (!tabs.contains(tab)) {
			return;
		}
		int from = tabs.indexOf(tab);
		tabs.remove(tab);
		if (insertAt > from) {
			insertAt--;
		}
		insertAt = Math.max(0, Math.min(insertAt, tabs.size()));
		tabs.add(insertAt, tab);
		tab.popped = false;
		tab.unread = false;
		if (target == tab) {
			target = null;
		}
		changed();
		TabStore.save();
	}

	public static void setMuted(ChatTab tab, boolean muted) {
		tab.muted = muted;
		if (muted) {
			tab.unread = false;
		}
		TabStore.save();
	}

	// Bit i is set if the message belongs in tab i
	public static int mask(GuiMessage message) {
		MessageExtras extras = MessageExtras.of(message);
		int cached = extras.chattoolkit$tabMask(version);
		if (cached != -1) {
			return cached;
		}
		List<ChatTab> tabs = tabs();
		StyledText text = null;
		int mask = 0;
		int count = Math.min(tabs.size(), 31);
		for (int i = 0; i < count; i++) {
			ChatTab tab = tabs.get(i);
			if (tab.rules.isEmpty()) {
				continue;
			}
			if (text == null) {
				text = StyledText.of(MessageExtras.content(message));
			}
			if (tab.matches(text)) {
				mask |= 1 << i;
			}
		}
		if (mask == 0) {
			for (int i = 0; i < count; i++) {
				if (tabs.get(i).isCatchAll()) {
					mask |= 1 << i;
				}
			}
		}
		extras.chattoolkit$setTabMask(version, mask);
		return mask;
	}

	public static boolean belongsTo(GuiMessage message, ChatTab tab) {
		int index = tabs().indexOf(tab);
		return index >= 0 && index < 31 && (mask(message) & (1 << index)) != 0;
	}

	// Used by the chat mixin to decide what the main chat box shows
	public static boolean showsInMain(GuiMessage message) {
		if (!enabled()) {
			return true;
		}
		ChatTab open = activeTab();
		return open == null || belongsTo(message, open);
	}

	public static void onNewMessage(GuiMessage message) {
		messages++;
		if (!enabled()) {
			return;
		}
		List<ChatTab> tabs = tabs();
		ChatTab open = activeTab();
		int mask = mask(message);
		for (int i = 0; i < Math.min(tabs.size(), 31); i++) {
			ChatTab tab = tabs.get(i);
			if ((mask & (1 << i)) == 0) {
				continue;
			}
			if (tab.popped) {
				tab.scroll = 0;
				if (tab != target && !tab.muted) {
					tab.unread = true;
				}
			} else if (tab != open && !tab.muted) {
				tab.unread = true;
			}
		}
	}

	public static void onMessagesChanged() {
		messages++;
	}

	// Where a typed message goes: the window you last clicked, otherwise the open tab
	public static @Nullable ChatTab sendTarget() {
		if (target != null && target.popped && tabs().contains(target)) {
			return target;
		}
		return activeTab();
	}

	public static void setTarget(@Nullable ChatTab tab) {
		target = tab;
		if (tab != null) {
			tab.unread = false;
		}
	}

	public static @Nullable ChatTab windowTarget() {
		return target != null && target.popped ? target : null;
	}

	public static String transformOutgoing(String message) {
		if (!enabled() || message.startsWith("/")) {
			return message;
		}
		ChatTab tab = sendTarget();
		if (tab == null || tab.sendAs.isBlank()) {
			return message;
		}
		String pattern = tab.sendAs.trim();
		return pattern.contains("{message}") ? pattern.replace("{message}", message) : pattern + " " + message;
	}
}
