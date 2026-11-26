package dev.highbitrate.chattoolkit.tabs;

import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// One "show messages that ..." line in a tab. The text can use & color codes, and is matched
// against how the message actually looks, so "&8[&7OOC&8]" only catches a gray [OOC] tag.
public final class TabRule {
	public enum Kind {
		CONTAINS, STARTS_WITH, EVERYTHING_ELSE;

		public Kind next() {
			return values()[(ordinal() + 1) % values().length];
		}

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	public Kind kind = Kind.CONTAINS;
	public String text = "";

	private transient String compiledFrom;
	private transient Pattern compiled;

	public TabRule() {
	}

	public TabRule(Kind kind, String text) {
		this.kind = kind;
		this.text = text;
	}

	public TabRule copy() {
		return new TabRule(kind, text);
	}

	public boolean isCatchAll() {
		return kind == Kind.EVERYTHING_ELSE;
	}

	public boolean matches(StyledText message) {
		if (kind == Kind.EVERYTHING_ELSE || text == null || text.isEmpty()) {
			return false;
		}
		Pattern p = pattern();
		if (p.length() == 0) {
			return false;
		}
		if (kind == Kind.STARTS_WITH) {
			return p.matchesAt(message, 0);
		}
		int last = message.length() - p.length();
		for (int i = 0; i <= last; i++) {
			if (p.matchesAt(message, i)) {
				return true;
			}
		}
		return false;
	}

	private Pattern pattern() {
		if (compiled == null || !text.equals(compiledFrom)) {
			compiled = Pattern.parse(text);
			compiledFrom = text;
		}
		return compiled;
	}

	// The rule text turned into characters with an expected color (or "any") and whether it must be bold
	private record Pattern(char[] chars, int[] colors, boolean[] bold) {
		static final int ANY = -1;

		int length() {
			return chars.length;
		}

		boolean matchesAt(StyledText message, int start) {
			if (start + chars.length > message.length()) {
				return false;
			}
			for (int i = 0; i < chars.length; i++) {
				int at = start + i;
				if (message.chars[at] != chars[i]) {
					return false;
				}
				if (colors[i] != ANY && message.colors[at] != colors[i]) {
					return false;
				}
				// only check bold when the rule asks for it, servers aren't consistent about turning it off
				if (bold[i] && !message.bold[at]) {
					return false;
				}
			}
			return true;
		}

		static Pattern parse(String text) {
			StringBuilder out = new StringBuilder();
			List<Integer> colors = new ArrayList<>();
			List<Boolean> bold = new ArrayList<>();
			int color = ANY;
			boolean isBold = false;
			for (int i = 0; i < text.length(); i++) {
				char c = text.charAt(i);
				if ((c == '&' || c == '§') && i + 1 < text.length()) {
					char code = Character.toLowerCase(text.charAt(i + 1));
					if (code == '#' && i + 7 < text.length() && isHex(text, i + 2, 6)) {
						color = Integer.parseInt(text.substring(i + 2, i + 8), 16);
						isBold = false;
						i += 7;
						continue;
					}
					TextColor legacy = legacyColor(code);
					if (legacy != null) {
						color = legacy.getValue() & 0xFFFFFF;
						isBold = false;
						i++;
						continue;
					}
					if (code == 'l') {
						isBold = true;
						i++;
						continue;
					}
					if (code == 'r') {
						color = ANY;
						isBold = false;
						i++;
						continue;
					}
					if ("kmno".indexOf(code) >= 0) {
						i++;
						continue;
					}
				}
				out.append(c);
				colors.add(color);
				bold.add(isBold);
			}
			int[] colorArray = new int[colors.size()];
			boolean[] boldArray = new boolean[bold.size()];
			for (int i = 0; i < colorArray.length; i++) {
				colorArray[i] = colors.get(i);
				boldArray[i] = bold.get(i);
			}
			return new Pattern(out.toString().toCharArray(), colorArray, boldArray);
		}

		private static boolean isHex(String s, int from, int count) {
			for (int i = from; i < from + count; i++) {
				if (Character.digit(s.charAt(i), 16) < 0) {
					return false;
				}
			}
			return true;
		}

		private static TextColor legacyColor(char code) {
			net.minecraft.ChatFormatting f = net.minecraft.ChatFormatting.getByCode(code);
			return f == null ? null : TextColor.fromLegacyFormat(f);
		}
	}
}
