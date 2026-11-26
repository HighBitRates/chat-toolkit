package dev.highbitrate.chattoolkit.ui;

import dev.highbitrate.chattoolkit.config.ChatToolkitConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

// Colour maths and the few drawing primitives the toolkit's square, low-opacity style is built from
public final class Draw {
	public static final int WHITE = 0xFFFFFF;
	public static final int TEXT = 0xFFFFFF;
	public static final int TEXT_DIM = 0xA8A8A8;
	public static final int TEXT_FAINT = 0x6E6E6E;
	public static final int ERROR = 0xFF6B6B;
	public static final int SUCCESS = 0x7CE38B;

	private Draw() {
	}

	public static int argb(int alpha, int rgb) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
	}

	// RGB with a 0..1 alpha
	public static int rgba(int rgb, float alpha) {
		return argb(Math.round(Anim.clamp01(alpha) * 255.0f), rgb);
	}

	public static int lerpRgb(int from, int to, float t) {
		t = Anim.clamp01(t);
		int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
		int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
		int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return (r << 16) | (g << 8) | b;
	}

	public static int accent() {
		return ChatToolkitConfig.get().accentColor & 0xFFFFFF;
	}

	// Base opacity of toolkit panels (0..1), from the settings
	public static float panelAlpha() {
		return ChatToolkitConfig.get().panelOpacity / 100.0f;
	}

	// Same background as the vanilla chat input, unless the user picked their own opacity
	public static int chatBackground() {
		if (ChatToolkitConfig.get().matchChatOpacity) {
			return Minecraft.getInstance().options.getBackgroundColor(Integer.MIN_VALUE);
		}
		return rgba(0x000000, panelAlpha());
	}

	// A square, translucent black panel like the vanilla chat box
	public static void panel(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, float fade) {
		g.fill(x0, y0, x1, y1, rgba(0x000000, panelAlpha() * fade));
	}

	// 1px outline inside the given rectangle
	public static void outline(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int argb) {
		if (((argb >>> 24) & 0xFF) == 0 || x1 - x0 < 2 || y1 - y0 < 2) {
			return;
		}
		g.fill(x0, y0, x1, y0 + 1, argb);
		g.fill(x0, y1 - 1, x1, y1, argb);
		g.fill(x0, y0 + 1, x0 + 1, y1 - 1, argb);
		g.fill(x1 - 1, y0 + 1, x1, y1 - 1, argb);
	}

	// A tiny pixel-art magnifying glass, 7x7, top-left at (x, y)
	public static void magnifier(GuiGraphicsExtractor g, int x, int y, int argb) {
		g.fill(x + 1, y, x + 4, y + 1, argb);
		g.fill(x + 1, y + 4, x + 4, y + 5, argb);
		g.fill(x, y + 1, x + 1, y + 4, argb);
		g.fill(x + 4, y + 1, x + 5, y + 4, argb);
		g.fill(x + 4, y + 4, x + 5, y + 5, argb);
		g.fill(x + 5, y + 5, x + 6, y + 6, argb);
		g.fill(x + 6, y + 6, x + 7, y + 7, argb);
	}

	// A small "x" (clear) glyph, 5x5
	public static void cross(GuiGraphicsExtractor g, int x, int y, int argb) {
		for (int i = 0; i < 5; i++) {
			g.fill(x + i, y + i, x + i + 1, y + i + 1, argb);
			g.fill(x + 4 - i, y + i, x + 5 - i, y + i + 1, argb);
		}
	}
}
