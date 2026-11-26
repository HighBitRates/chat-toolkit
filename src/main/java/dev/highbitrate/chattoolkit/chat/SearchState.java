package dev.highbitrate.chattoolkit.chat;

import dev.highbitrate.chattoolkit.config.ChatToolkitConfig;
import dev.highbitrate.chattoolkit.tabs.TabManager;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

// The current chat search. While a query is active the vanilla chat only builds lines for matching
// messages (see ChatComponentMixin), so filtering costs nothing per frame
public final class SearchState {
	private static final long REGEX_TIME_LIMIT_NANOS = 4_000_000L;
	private static final int MAX_RANGES_PER_LINE = 48;

	private static String query = "";
	private static String remembered = "";
	private static @Nullable Pattern pattern;
	private static @Nullable String error;
	private static int matchCount;
	private static boolean countDirty = true;

	private SearchState() {
	}

	public static String query() {
		return query;
	}

	// True when a valid, non-empty search is filtering the chat
	public static boolean isActive() {
		return pattern != null;
	}

	public static @Nullable String error() {
		return error;
	}

	// The query to restore when the chat is opened again (only if "remember search" is on)
	public static String remembered() {
		return remembered;
	}

	public static void setQuery(String newQuery) {
		String q = newQuery == null ? "" : newQuery;
		if (q.equals(query)) {
			return;
		}
		query = q;
		rebuild();
	}

	// Recompile after the query or the case/word/regex options changed, then refresh the chat
	public static void rebuild() {
		compile();
		refreshChat();
	}

	private static void compile() {
		error = null;
		pattern = null;
		if (query.isBlank()) {
			return;
		}
		ChatToolkitConfig config = ChatToolkitConfig.get();
		String body = config.regex ? query : Pattern.quote(query);
		if (config.wholeWords) {
			body = "(?<![\\p{L}\\p{N}_])(?:" + body + ")(?![\\p{L}\\p{N}_])";
		}
		int flags = config.caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
		try {
			pattern = Pattern.compile(body, flags);
		} catch (PatternSyntaxException e) {
			error = e.getDescription();
		}
	}

	public static boolean isVisible(GuiMessage message) {
		Pattern p = pattern;
		return p == null || find(p, MessageExtras.plain(message));
	}

	public static boolean matches(String text) {
		Pattern p = pattern;
		return p != null && find(p, text);
	}

	private static boolean find(Pattern p, String text) {
		try {
			return p.matcher(guard(text)).find();
		} catch (RegexTooSlow e) {
			giveUpOnSlowPattern();
			return true;
		}
	}

	// Character ranges [start, end) of every match inside text, for highlighting
	public static List<int[]> ranges(String text) {
		Pattern p = pattern;
		if (p == null || text.isEmpty()) {
			return List.of();
		}
		List<int[]> out = new ArrayList<>();
		try {
			Matcher m = p.matcher(guard(text));
			while (m.find() && out.size() < MAX_RANGES_PER_LINE) {
				if (m.end() > m.start()) {
					out.add(new int[]{m.start(), m.end()});
				}
			}
		} catch (RegexTooSlow e) {
			giveUpOnSlowPattern();
			return List.of();
		}
		return out;
	}

	// Number of messages in the history that match
	public static int matchCount() {
		if (countDirty) {
			countDirty = false;
			int count = 0;
			Pattern p = pattern;
			if (p != null) {
				for (GuiMessage message : ChatHelper.allMessages()) {
					if (TabManager.showsInMain(message) && find(p, MessageExtras.plain(message))) {
						count++;
					}
				}
			}
			matchCount = count;
		}
		return matchCount;
	}

	public static void onMessageAdded() {
		if (pattern != null) {
			countDirty = true;
		}
	}

	// Drops the filter (the HUD chat should show everything again), optionally remembering the text
	public static void deactivate(boolean remember) {
		remembered = remember ? query : "";
		if (!query.isEmpty() || pattern != null) {
			query = "";
			pattern = null;
			error = null;
			refreshChat();
		}
	}

	public static void refreshChat() {
		countDirty = true;
		ChatComponent chat = ChatHelper.chat();
		if (chat != null) {
			chat.rescaleChat();
		}
	}

	private static void giveUpOnSlowPattern() {
		if (pattern != null) {
			pattern = null;
			error = "too slow";
			countDirty = true;
		}
	}

	// A user regex like (a+)+$ can take forever; this wrapper aborts it after a few milliseconds.
	private static CharSequence guard(String text) {
		return ChatToolkitConfig.get().regex ? new GuardedText(text, System.nanoTime() + REGEX_TIME_LIMIT_NANOS) : text;
	}

	private static final class RegexTooSlow extends RuntimeException {
		RegexTooSlow() {
			super(null, null, false, false);
		}
	}

	private record GuardedText(CharSequence inner, long deadline) implements CharSequence {
		@Override
		public char charAt(int index) {
			if ((index & 255) == 0 && System.nanoTime() > deadline) {
				throw new RegexTooSlow();
			}
			return inner.charAt(index);
		}

		@Override
		public int length() {
			return inner.length();
		}

		@Override
		public CharSequence subSequence(int start, int end) {
			return new GuardedText(inner.subSequence(start, end), deadline);
		}

		@Override
		public String toString() {
			return inner.toString();
		}
	}
}
