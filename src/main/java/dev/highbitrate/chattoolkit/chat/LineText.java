package dev.highbitrate.chattoolkit.chat;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

// A chat line flattened to plain text, remembering each character's style so we can measure where matches start and end
public final class LineText {
	private final int[] codePoints;
	private final Style[] styles;
	private final int[] charToCodePoint;
	private final String text;

	private LineText(int[] codePoints, Style[] styles, String text, int[] charToCodePoint) {
		this.codePoints = codePoints;
		this.styles = styles;
		this.text = text;
		this.charToCodePoint = charToCodePoint;
	}

	public static LineText of(FormattedCharSequence sequence) {
		List<Integer> cps = new ArrayList<>();
		List<Style> styles = new ArrayList<>();
		sequence.accept((position, style, codePoint) -> {
			cps.add(codePoint);
			styles.add(style);
			return true;
		});
		int[] cpArray = new int[cps.size()];
		StringBuilder sb = new StringBuilder(cps.size());
		for (int i = 0; i < cpArray.length; i++) {
			cpArray[i] = cps.get(i);
			sb.appendCodePoint(cpArray[i]);
		}
		String text = sb.toString();
		int[] map = new int[text.length() + 1];
		int cp = 0;
		for (int i = 0; i < text.length(); ) {
			int count = Character.charCount(text.codePointAt(i));
			for (int k = 0; k < count; k++) {
				map[i + k] = cp;
			}
			i += count;
			cp++;
		}
		map[text.length()] = cp;
		return new LineText(cpArray, styles.toArray(new Style[0]), text, map);
	}

	public String text() {
		return text;
	}

	// Width in pixels of the first charIndex characters, honouring bold etc
	public int widthBefore(Font font, int charIndex) {
		int count = charToCodePoint[Math.max(0, Math.min(charIndex, text.length()))];
		if (count == 0) {
			return 0;
		}
		FormattedCharSequence prefix = sink -> {
			for (int i = 0; i < count; i++) {
				if (!sink.accept(i, styles[i], codePoints[i])) {
					return false;
				}
			}
			return true;
		};
		return font.width(prefix);
	}
}
