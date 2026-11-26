package dev.highbitrate.chattoolkit.tabs;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.Optional;

// A message flattened to characters with the color and bold flag each one is drawn with.
// Rules compare against this instead of a "&8[&7OOC" string, because the server can encode the
// same look in different ways (hex vs legacy colors, extra resets, nested components...)
public final class StyledText {
	public static final int DEFAULT_COLOR = 0xFFFFFF;

	final char[] chars;
	final int[] colors;
	final boolean[] bold;

	private StyledText(char[] chars, int[] colors, boolean[] bold) {
		this.chars = chars;
		this.colors = colors;
		this.bold = bold;
	}

	public static StyledText of(Component component) {
		StringBuilder text = new StringBuilder();
		IntList colors = new IntList();
		BoolList bold = new BoolList();
		component.visit((style, part) -> {
			TextColor color = style.getColor();
			int rgb = color == null ? DEFAULT_COLOR : color.getValue() & 0xFFFFFF;
			boolean b = style.isBold();
			for (int i = 0; i < part.length(); i++) {
				char c = part.charAt(i);
				if (c == '§' && i + 1 < part.length()) {
					// old servers still send section signs inside plain text
					i++;
					continue;
				}
				text.append(c);
				colors.add(rgb);
				bold.add(b);
			}
			return Optional.empty();
		}, Style.EMPTY);
		return new StyledText(text.toString().toCharArray(), colors.toArray(), bold.toArray());
	}

	public int length() {
		return chars.length;
	}

	private static final class IntList {
		int[] data = new int[64];
		int size;

		void add(int v) {
			if (size == data.length) {
				data = java.util.Arrays.copyOf(data, size * 2);
			}
			data[size++] = v;
		}

		int[] toArray() {
			return java.util.Arrays.copyOf(data, size);
		}
	}

	private static final class BoolList {
		boolean[] data = new boolean[64];
		int size;

		void add(boolean v) {
			if (size == data.length) {
				data = java.util.Arrays.copyOf(data, size * 2);
			}
			data[size++] = v;
		}

		boolean[] toArray() {
			return java.util.Arrays.copyOf(data, size);
		}
	}
}
