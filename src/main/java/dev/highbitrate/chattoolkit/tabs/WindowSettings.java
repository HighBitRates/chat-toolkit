package dev.highbitrate.chattoolkit.tabs;

import dev.highbitrate.chattoolkit.ui.Draw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

// Small panel for one popped-out window: how see-through it is and how big its text is
public final class WindowSettings {
	private static final int W = 150;
	private static final int H = 64;
	private static final int TRACK_X = 62;
	private static final int TRACK_W = 56;

	private static @Nullable ChatTab tab;
	private static int x;
	private static int y;
	// 0 = opacity slider, 1 = text size slider, -1 = nothing
	private static int dragging = -1;

	private WindowSettings() {
	}

	public static void open(ChatTab window, int mouseX, int mouseY, int screenW, int screenH) {
		tab = window;
		x = Mth.clamp(mouseX - W / 2, 2, screenW - W - 2);
		y = Mth.clamp(mouseY + 6, 2, screenH - H - 2);
		dragging = -1;
	}

	public static boolean isOpen() {
		return tab != null;
	}

	public static void close() {
		if (tab != null) {
			TabStore.save();
		}
		tab = null;
		dragging = -1;
	}

	public static boolean contains(double mx, double my) {
		return tab != null && mx >= x && mx < x + W && my >= y && my < y + H;
	}

	private static int rowY(int row) {
		return y + 20 + row * 14;
	}

	// Returns true if the click was ours
	public static boolean mouseClicked(double mx, double my) {
		if (tab == null) {
			return false;
		}
		if (!contains(mx, my)) {
			close();
			return true;
		}
		for (int row = 0; row < 2; row++) {
			int ry = rowY(row);
			if (my >= ry - 2 && my < ry + 10 && mx >= x + TRACK_X - 3 && mx < x + TRACK_X + TRACK_W + 3) {
				dragging = row;
				dragTo(mx);
				return true;
			}
		}
		if (my >= y + 48 && mx < x + 40) {
			tab.windowOpacity = -1;
			tab.windowTextSize = 100;
			TabStore.save();
		}
		return true;
	}

	public static boolean isDragging() {
		return tab != null && dragging >= 0;
	}

	public static void dragTo(double mx) {
		if (tab == null || dragging < 0) {
			return;
		}
		float t = Mth.clamp((float) ((mx - (x + TRACK_X)) / TRACK_W), 0, 1);
		if (dragging == 0) {
			tab.windowOpacity = Math.round(t * 20) * 5;
		} else {
			tab.windowTextSize = 50 + Math.round(t * 20) * 5;
		}
	}

	public static void endDrag() {
		if (dragging >= 0) {
			dragging = -1;
			TabStore.save();
		}
	}

	public static void render(GuiGraphicsExtractor g, Font font) {
		ChatTab t = tab;
		if (t == null) {
			return;
		}
		if (!t.popped || !TabManager.tabs().contains(t)) {
			close();
			return;
		}
		g.nextStratum();
		g.fill(x, y, x + W, y + H, Draw.rgba(0x101012, 0.94f));
		Draw.outline(g, x, y, x + W, y + H, Draw.rgba(Draw.WHITE, 0.2f));
		g.text(font, Component.translatable("chattoolkit.window.title", t.name), x + 6, y + 4, Draw.rgba(Draw.TEXT, 1), false);
		g.fill(x, y + 15, x + W, y + 16, Draw.rgba(Draw.WHITE, 0.12f));

		float opacity = t.windowOpacity < 0 ? -1 : t.windowOpacity / 100.0f;
		slider(g, font, 0, Component.translatable("chattoolkit.window.opacity"), opacity < 0 ? 0.5f : opacity,
			opacity < 0 ? Component.translatable("chattoolkit.window.auto") : Component.literal(t.windowOpacity + "%"));
		slider(g, font, 1, Component.translatable("chattoolkit.window.text_size"), (t.windowTextSize - 50) / 100.0f,
			Component.literal(t.windowTextSize + "%"));
		g.text(font, Component.translatable("chattoolkit.window.reset"), x + 6, y + 50, Draw.rgba(0x969696, 1), false);
	}

	private static void slider(GuiGraphicsExtractor g, Font font, int row, Component label, float frac, Component value) {
		int ry = rowY(row);
		g.text(font, label, x + 6, ry + 1, Draw.rgba(Draw.TEXT_DIM, 1), false);
		int tx = x + TRACK_X;
		g.fill(tx, ry + 4, tx + TRACK_W, ry + 5, Draw.rgba(Draw.WHITE, 0.25f));
		int kx = tx + Math.round(TRACK_W * frac);
		g.fill(tx, ry + 4, kx, ry + 5, Draw.rgba(Draw.accent(), 0.9f));
		g.fill(kx - 1, ry + 1, kx + 2, ry + 8, Draw.rgba(0xE6E6E6, 1));
		g.text(font, value, tx + TRACK_W + 6, ry + 1, Draw.rgba(0xBEBEBE, 1), false);
	}
}
