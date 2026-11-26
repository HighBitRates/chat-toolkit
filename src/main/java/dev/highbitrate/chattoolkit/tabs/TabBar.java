package dev.highbitrate.chattoolkit.tabs;

import dev.highbitrate.chattoolkit.ui.Draw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// The row of tabs that sits right on top of the chat message box
public final class TabBar {
	public static final int HEIGHT = 10;
	private static final int ARROW = 8;
	private static final int DOT = 6;
	private static final int PLUS = 10;

	// tab == null is the "+" button
	public record Slot(@Nullable ChatTab tab, int x, int w) {
		public boolean onArrow(double mx) {
			return tab != null && mx >= x + w - ARROW - 1;
		}
	}

	private static List<Slot> slots = List.of();
	private static @Nullable ChatTab hovered;

	private TabBar() {
	}

	public static int y(int screenHeight) {
		return screenHeight - 25;
	}

	public static void layout(Font font) {
		List<Slot> out = new ArrayList<>();
		int x = 2;
		for (ChatTab tab : TabManager.tabs()) {
			if (tab.popped) {
				continue;
			}
			int w = font.width(tab.name) + 8 + (tab.unread && tab.dot ? DOT : 0) + (tab == hovered ? ARROW : 0);
			out.add(new Slot(tab, x, w));
			x += w + 1;
		}
		out.add(new Slot(null, x, PLUS));
		slots = out;
	}

	public static int right() {
		if (slots.isEmpty()) {
			return 2;
		}
		Slot last = slots.getLast();
		return last.x() + last.w();
	}

	public static @Nullable Slot slotAt(double mx, double my, int screenHeight) {
		int y = y(screenHeight);
		if (my < y || my >= y + HEIGHT) {
			return null;
		}
		for (Slot slot : slots) {
			if (mx >= slot.x() && mx < slot.x() + slot.w()) {
				return slot;
			}
		}
		return null;
	}

	public static boolean overBar(double mx, double my, int screenHeight) {
		int y = y(screenHeight);
		// a little forgiving vertically so docking a window doesn't need pixel precision
		return my >= y - 6 && my < y + HEIGHT + 4 && mx >= 0 && mx < right() + 40;
	}

	// Where a window dropped at mx would be inserted, as an index into the preset's tab list
	public static int insertIndex(double mx) {
		List<ChatTab> tabs = TabManager.tabs();
		for (Slot slot : slots) {
			if (slot.tab() != null && mx < slot.x() + slot.w() / 2.0) {
				return tabs.indexOf(slot.tab());
			}
		}
		ChatTab lastDocked = null;
		for (Slot slot : slots) {
			if (slot.tab() != null) {
				lastDocked = slot.tab();
			}
		}
		return lastDocked == null ? tabs.size() : tabs.indexOf(lastDocked) + 1;
	}

	public static void render(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenHeight, boolean dropping) {
		Slot hover = slotAt(mouseX, mouseY, screenHeight);
		ChatTab newHovered = hover != null ? hover.tab() : null;
		if (newHovered != hovered) {
			hovered = newHovered;
		}
		layout(font);
		hover = slotAt(mouseX, mouseY, screenHeight);

		int y = y(screenHeight);
		ChatTab active = TabManager.activeTab();
		for (Slot slot : slots) {
			int x = slot.x();
			ChatTab tab = slot.tab();
			boolean over = hover == slot && !dropping;
			if (tab == null) {
				g.fill(x, y, x + slot.w(), y + HEIGHT, Draw.rgba(0x000000, over ? 0.55f : 0.4f));
				int c = Draw.rgba(over ? 0xFFFFFF : 0xC8C8C8, 1);
				g.fill(x + 5, y + 3, x + 6, y + 8, c);
				g.fill(x + 3, y + 5, x + 8, y + 6, c);
				if (over) {
					g.setTooltipForNextFrame(font, Component.translatable("chattoolkit.tabs.new"), mouseX, mouseY);
				}
				continue;
			}
			boolean isActive = tab == active;
			g.fill(x, y, x + slot.w(), y + HEIGHT, Draw.rgba(0x000000, isActive ? 0.67f : over ? 0.55f : 0.4f));
			if (isActive) {
				g.fill(x, y, x + slot.w(), y + 1, Draw.rgba(0xFFFFFF, 0.8f));
			}
			int color = isActive ? 0xFFFFFF : over ? 0xBEBEBE : 0xAAAAAA;
			g.text(font, tab.name, x + 4, y + 1, Draw.rgba(color, 1), true);
			int after = x + 4 + font.width(tab.name);
			if (tab.unread && tab.dot) {
				drawDot(g, after + 1, y + 3);
			}
			if (over) {
				boolean onArrow = slot.onArrow(mouseX);
				drawArrow(g, x + slot.w() - ARROW, y + 3, Draw.rgba(0xFFFFFF, onArrow ? 1 : 0.6f));
				g.setTooltipForNextFrame(font, Component.translatable(onArrow ? "chattoolkit.tabs.pop_out" : "chattoolkit.tabs.tab_hint"), mouseX, mouseY);
			}
		}
		if (dropping) {
			int x1 = right();
			Draw.outline(g, 1, y - 1, x1 + 1, y + HEIGHT + 1, Draw.rgba(0xFFFFFF, 0.8f));
			int index = insertIndex(mouseX);
			int gx = slots.getLast().x();
			for (Slot slot : slots) {
				if (slot.tab() != null && TabManager.tabs().indexOf(slot.tab()) >= index) {
					gx = slot.x();
					break;
				}
			}
			g.fill(gx - 1, y, gx + 1, y + HEIGHT, Draw.rgba(0xFFFFFF, 0.9f));
		}
	}

	// Red dot with a white middle, 4x4
	public static void drawDot(GuiGraphicsExtractor g, int x, int y) {
		int red = 0xFFE63232;
		g.fill(x + 1, y, x + 3, y + 1, red);
		g.fill(x, y + 1, x + 1, y + 3, red);
		g.fill(x + 3, y + 1, x + 4, y + 3, red);
		g.fill(x + 1, y + 3, x + 3, y + 4, red);
		g.fill(x + 1, y + 1, x + 3, y + 3, 0xFFFFFFFF);
	}

	// small "pop out" arrow, 4x4
	private static void drawArrow(GuiGraphicsExtractor g, int x, int y, int color) {
		g.fill(x, y, x + 3, y + 1, color);
		g.fill(x, y + 1, x + 1, y + 3, color);
		g.fill(x + 2, y + 2, x + 4, y + 4, color);
	}
}
