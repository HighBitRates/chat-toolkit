package dev.highbitrate.chattoolkit.text;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Converts between chat components and text you can type: &a-&f colors, &l &o &n &m &k &r,
// &#RRGGBB hex, markdown-ish **bold** *italic* __underline__ ~~strike~~ ||magic|| `code`,
// and clickable links
public final class TextFormatter {
	private static final Pattern URL = Pattern.compile("(?i)\\b((?:https?://|www\\.)[^\\s<>\"']*[^\\s<>\"'.,;:!?)\\]])");
	private static final Pattern LEGACY_CODE = Pattern.compile("(?i)[&§](?:#[0-9a-f]{6}|[0-9a-fk-or])");
	private static final int CODE_COLOR = 0xB8C4CE;

	private TextFormatter() {
	}

	// component -> "&" codes

	private record Format(@Nullable TextColor color, boolean bold, boolean italic, boolean underlined, boolean strikethrough, boolean obfuscated) {
		static Format of(Style style) {
			return new Format(style.getColor(), style.isBold(), style.isItalic(), style.isUnderlined(), style.isStrikethrough(), style.isObfuscated());
		}

		boolean plain() {
			return color == null && !bold && !italic && !underlined && !strikethrough && !obfuscated;
		}
	}

	// The component as text with &-codes, so its colours survive copy/paste into plugins, signs, notes...
	public static String toLegacy(Component component) {
		StringBuilder out = new StringBuilder();
		Format[] previous = {new Format(null, false, false, false, false, false)};
		component.visit((style, text) -> {
			if (text.isEmpty()) {
				return Optional.empty();
			}
			Format format = Format.of(style);
			Format prev = previous[0];
			if (!format.equals(prev)) {
				boolean turnedOff = (prev.color != null && format.color == null)
					|| (prev.bold && !format.bold) || (prev.italic && !format.italic) || (prev.underlined && !format.underlined)
					|| (prev.strikethrough && !format.strikethrough) || (prev.obfuscated && !format.obfuscated);
				boolean colorChanged = !Objects.equals(prev.color, format.color);
				if (format.color != null && (colorChanged || turnedOff)) {
					out.append(colorCode(format.color));
					appendFormats(out, format, null);
				} else if (turnedOff) {
					out.append("&r");
					appendFormats(out, format, null);
				} else {
					appendFormats(out, format, prev);
				}
				previous[0] = format;
			}
			out.append(text);
			return Optional.empty();
		}, Style.EMPTY);
		return out.toString();
	}

	private static void appendFormats(StringBuilder out, Format format, @Nullable Format already) {
		if (format.bold && (already == null || !already.bold)) {
			out.append("&l");
		}
		if (format.italic && (already == null || !already.italic)) {
			out.append("&o");
		}
		if (format.underlined && (already == null || !already.underlined)) {
			out.append("&n");
		}
		if (format.strikethrough && (already == null || !already.strikethrough)) {
			out.append("&m");
		}
		if (format.obfuscated && (already == null || !already.obfuscated)) {
			out.append("&k");
		}
	}

	private static String colorCode(TextColor color) {
		int rgb = color.getValue() & 0xFFFFFF;
		for (ChatFormatting formatting : ChatFormatting.values()) {
			TextColor legacy = TextColor.fromLegacyFormat(formatting);
			if (legacy != null && legacy.getValue() == rgb) {
				return "&" + code(formatting);
			}
		}
		return String.format("&#%06X", rgb);
	}

	// The character after "§" for this formatting (ChatFormatting#toString is "§" + code)
	private static char code(ChatFormatting formatting) {
		return formatting.toString().charAt(1);
	}

	// Removes &-codes, leaving the visible text
	public static String stripCodes(String text) {
		return LEGACY_CODE.matcher(text).replaceAll("");
	}

	public static boolean hasFormatting(Component component) {
		boolean[] found = {false};
		component.visit((style, text) -> {
			if (!text.isEmpty() && !Format.of(style).plain()) {
				found[0] = true;
				return Optional.of(Boolean.TRUE);
			}
			return Optional.empty();
		}, Style.EMPTY);
		return found[0];
	}

	// typed markup -> Component

	public static MutableComponent parse(String input) {
		return new Parser(input).run();
	}

	private static final class Parser {
		private final String s;
		private final MutableComponent root = Component.empty();
		private final StringBuilder buffer = new StringBuilder();
		private Style legacy = Style.EMPTY;
		private boolean bold;
		private boolean italic;
		private boolean underline;
		private boolean strike;
		private boolean obfuscated;
		private boolean code;

		Parser(String s) {
			this.s = s;
		}

		MutableComponent run() {
			int i = 0;
			while (i < s.length()) {
				char c = s.charAt(i);
				if (c == '\\' && i + 1 < s.length() && "\\*_~|`&".indexOf(s.charAt(i + 1)) >= 0) {
					buffer.append(s.charAt(i + 1));
					i += 2;
					continue;
				}
				if (!code && (c == '&' || c == '§') && i + 1 < s.length()) {
					int used = legacyCode(i);
					if (used > 0) {
						i += used;
						continue;
					}
				}
				if (c == '`' && (code || closes("`", i + 1))) {
					flush();
					code = !code;
					i++;
					continue;
				}
				if (!code) {
					int used = markdown(i);
					if (used > 0) {
						i += used;
						continue;
					}
				}
				buffer.append(c);
				i++;
			}
			flush();
			return root;
		}

		private int legacyCode(int i) {
			char next = Character.toLowerCase(s.charAt(i + 1));
			if (next == '#' && i + 8 <= s.length()) {
				String hex = s.substring(i + 2, i + 8);
				if (hex.matches("[0-9a-fA-F]{6}")) {
					flush();
					legacy = Style.EMPTY.withColor(Integer.parseInt(hex, 16));
					return 8;
				}
			}
			ChatFormatting formatting = ChatFormatting.getByCode(next);
			if (formatting == null) {
				return 0;
			}
			flush();
			if (formatting == ChatFormatting.RESET) {
				legacy = Style.EMPTY;
			} else if (TextColor.fromLegacyFormat(formatting) != null) {
				legacy = Style.EMPTY.withColor(formatting);
			} else {
				legacy = legacy.applyFormat(formatting);
			}
			return 2;
		}

		private int markdown(int i) {
			if (s.startsWith("**", i) && (bold || closes("**", i + 2))) {
				flush();
				bold = !bold;
				return 2;
			}
			if (s.startsWith("__", i) && (underline || closes("__", i + 2))) {
				flush();
				underline = !underline;
				return 2;
			}
			if (s.startsWith("~~", i) && (strike || closes("~~", i + 2))) {
				flush();
				strike = !strike;
				return 2;
			}
			if (s.startsWith("||", i) && (obfuscated || closes("||", i + 2))) {
				flush();
				obfuscated = !obfuscated;
				return 2;
			}
			if (s.charAt(i) == '*' && !s.startsWith("**", i) && (italic || closes("*", i + 1))) {
				flush();
				italic = !italic;
				return 1;
			}
			return 0;
		}

		// A marker only counts if it is closed again later with something in between
		private boolean closes(String marker, int from) {
			int index = s.indexOf(marker, from);
			return index > from;
		}

		private void flush() {
			if (buffer.isEmpty()) {
				return;
			}
			String text = buffer.toString();
			buffer.setLength(0);
			Style style = legacy;
			if (bold) {
				style = style.withBold(true);
			}
			if (italic) {
				style = style.withItalic(true);
			}
			if (underline) {
				style = style.withUnderlined(true);
			}
			if (strike) {
				style = style.withStrikethrough(true);
			}
			if (obfuscated) {
				style = style.withObfuscated(true);
			}
			if (code) {
				style = Style.EMPTY.withColor(CODE_COLOR);
			}
			appendWithLinks(root, text, style);
		}
	}

	// Appends text, turning web links into clickable, underlined links
	public static void appendWithLinks(MutableComponent target, String text, Style style) {
		Matcher m = URL.matcher(text);
		int last = 0;
		while (m.find()) {
			if (m.start() > last) {
				target.append(Component.literal(text.substring(last, m.start())).setStyle(style));
			}
			String url = m.group(1);
			Style linkStyle = style;
			try {
				URI uri = URI.create(url.regionMatches(true, 0, "http", 0, 4) ? url : "https://" + url);
				linkStyle = style.withUnderlined(true)
					.withClickEvent(new ClickEvent.OpenUrl(uri))
					.withHoverEvent(new HoverEvent.ShowText(Component.translatable("chattoolkit.link.open", url)));
			} catch (IllegalArgumentException ignored) {
				// not a valid URI after all, keep it as text
			}
			target.append(Component.literal(url).setStyle(linkStyle));
			last = m.end();
		}
		if (last < text.length()) {
			target.append(Component.literal(text.substring(last)).setStyle(style));
		}
	}
}
