package dev.highbitrate.chattoolkit.tabs;

import dev.highbitrate.chattoolkit.ChatToolkit;
import dev.highbitrate.chattoolkit.chat.ChatHelper;
import dev.highbitrate.chattoolkit.ui.Draw;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

// Popped-out tabs. With the chat closed they behave like the vanilla chat (newest lines that
// fade out). With the chat open they get a title bar and can be moved, resized and scrolled.
public final class TabWindows implements HudElement {
	private static final Identifier ID = Identifier.fromNamespaceAndPath(ChatToolkit.MOD_ID, "tab_windows");
	public static final int TITLE = 11;
	private static final int LINE = 9;
	private static final int MAX_MESSAGES = 300;

	public enum Part { TITLE, CLOSE, GRIP, BODY }

	public record Hit(ChatTab tab, Part part) {
	}

	public record Rect(int x, int y, int w, int h) {
		int bodyTop() {
			return y + TITLE;
		}

		int bottom() {
			return y + h;
		}

		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private record Line(FormattedCharSequence text, GuiMessage message) {
	}

	private static final class Cache {
		int messages = -1;
		int version = -1;
		int width = -1;
		int total = -1;
		List<Line> lines = List.of();
	}

	private static final Map<ChatTab, Cache> caches = new IdentityHashMap<>();
	// front-most window last; kept apart from the tab list so raising a window doesn't reorder your tabs
	private static final List<ChatTab> zOrder = new ArrayList<>();

	// drag state, driven by the chat screen
	private static @Nullable ChatTab dragging;
	private static boolean resizing;
	private static double grabX, grabY;
	private static int mouseX = -1, mouseY = -1;

	public static void register() {
		HudElementRegistry.addLast(ID, new TabWindows());
	}

	public static List<ChatTab> windows() {
		List<ChatTab> out = new ArrayList<>();
		if (!TabManager.enabled()) {
			return out;
		}
		for (ChatTab tab : TabManager.tabs()) {
			if (tab.popped && !zOrder.contains(tab)) {
				out.add(tab);
			}
		}
		for (ChatTab tab : zOrder) {
			if (tab.popped && TabManager.tabs().contains(tab)) {
				out.add(tab);
			}
		}
		return out;
	}

	public static Rect rect(ChatTab tab, int screenW, int screenH) {
		int w = Math.min(tab.windowWidth, screenW - 4);
		int h = Math.min(tab.windowHeight, screenH - 30);
		int x;
		int y;
		if (tab.windowX < 0 || tab.windowY < 0) {
			// not placed yet: stack them down the right side
			int index = 0;
			for (ChatTab other : TabManager.tabs()) {
				if (other == tab) {
					break;
				}
				if (other.popped) {
					index++;
				}
			}
			x = screenW - w - 4;
			y = 4 + Math.max(0, index) * (h + 6);
			y = Math.min(y, Math.max(0, screenH - h - 40));
		} else {
			x = Math.round(tab.windowX * Math.max(0, screenW - w));
			y = Math.round(tab.windowY * Math.max(0, screenH - h));
		}
		return new Rect(x, y, w, h);
	}

	private static boolean chatOpen() {
		return Minecraft.getInstance().gui.screen() instanceof ChatScreen;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.chatVisibility().get() == ChatVisiblity.HIDDEN) {
			return;
		}
		List<ChatTab> windows = windows();
		if (windows.isEmpty()) {
			caches.clear();
			return;
		}
		boolean open = chatOpen();
		int sw = graphics.guiWidth();
		int sh = graphics.guiHeight();
		for (ChatTab tab : windows) {
			Rect r = rect(tab, sw, sh);
			if (open) {
				drawOpen(graphics, mc, tab, r);
			} else {
				drawClosed(graphics, mc, tab, r);
			}
		}
	}

	private static float textOpacity(Minecraft mc) {
		return mc.options.chatOpacity().get().floatValue() * 0.9f + 0.1f;
	}

	private static float backgroundOpacity(Minecraft mc) {
		return mc.options.textBackgroundOpacity().get().floatValue();
	}

	// Chat closed: like vanilla, only recent lines, fading out after 10 seconds
	private static void drawClosed(GuiGraphicsExtractor g, Minecraft mc, ChatTab tab, Rect r) {
		List<Line> lines = lines(tab, wrapWidth(tab, r), mc.font);
		int now = mc.gui.hud.getGuiTicks();
		float text = textOpacity(mc);
		float bg = background(mc, tab);
		int lineH = lineHeight(tab);
		int y = r.bottom() - 2 - lineH;
		for (Line line : lines) {
			if (y < r.bodyTop()) {
				break;
			}
			double t = (now - line.message().addedTime()) / 200.0;
			t = Mth.clamp((1.0 - t) * 10.0, 0.0, 1.0);
			float alpha = (float) (t * t);
			if (alpha <= 0.01f) {
				break;
			}
			g.fill(r.x(), y, r.x() + r.w(), y + lineH, Draw.rgba(0x000000, bg * alpha));
			drawLine(g, mc.font, tab, line.text(), r.x() + 4, y + 1, Draw.rgba(0xFFFFFF, text * alpha));
			y -= lineH;
		}
	}

	private static int lineHeight(ChatTab tab) {
		return Math.max(5, Math.round(LINE * tab.textScale()));
	}

	private static int wrapWidth(ChatTab tab, Rect r) {
		return Math.max(20, (int) ((r.w() - 8) / tab.textScale()));
	}

	private static float background(Minecraft mc, ChatTab tab) {
		return tab.windowOpacity >= 0 ? tab.windowOpacity / 100.0f : backgroundOpacity(mc);
	}

	private static void drawLine(GuiGraphicsExtractor g, Font font, ChatTab tab, FormattedCharSequence text, int x, int y, int color) {
		float scale = tab.textScale();
		if (scale == 1.0f) {
			g.text(font, text, x, y, color, true);
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.text(font, text, 0, 0, color, true);
		g.pose().popMatrix();
	}

	private static void drawOpen(GuiGraphicsExtractor g, Minecraft mc, ChatTab tab, Rect r) {
		Font font = mc.font;
		boolean target = TabManager.windowTarget() == tab;
		Hit hover = hitAt(mouseX, mouseY);
		boolean hovered = hover != null && hover.tab() == tab;

		// title bar
		g.fill(r.x(), r.y(), r.x() + r.w(), r.bodyTop(), Draw.rgba(0x000000, target ? 0.75f : 0.62f));
		if (target) {
			g.fill(r.x(), r.bodyTop() - 1, r.x() + r.w(), r.bodyTop(), Draw.rgba(0xFFFFFF, 0.65f));
		}
		int titleRight = font.width(tab.name);
		g.text(font, tab.name, r.x() + 4, r.y() + 2, Draw.rgba(target ? 0xFFFFFF : tab.muted ? 0x6E6E6E : 0xAAAAAA, 1), true);
		if (tab.muted) {
			TabBar.drawMutedBell(g, r.x() + 4 + titleRight + 3, r.y() + 2);
		} else if (tab.unread && tab.notifies()) {
			TabBar.drawDot(g, r.x() + 4 + titleRight + 2, r.y() + 3);
		}
		boolean overClose = hovered && hover.part() == Part.CLOSE;
		Draw.cross(g, r.x() + r.w() - 9, r.y() + 3, Draw.rgba(overClose ? 0xFFFFFF : 0xB0B0B0, 1));

		// body
		float bg = tab.windowOpacity >= 0 ? tab.windowOpacity / 100.0f : Math.max(0.35f, backgroundOpacity(mc));
		g.fill(r.x(), r.bodyTop(), r.x() + r.w(), r.bottom(), Draw.rgba(0x000000, bg));
		List<Line> lines = lines(tab, wrapWidth(tab, r), font);
		int lineH = lineHeight(tab);
		int visible = Math.max(1, (r.h() - TITLE - 4) / lineH);
		tab.scroll = Mth.clamp(tab.scroll, 0, Math.max(0, lines.size() - visible));
		int y = r.bottom() - 2 - lineH;
		float text = textOpacity(mc);
		for (int i = tab.scroll; i < lines.size() && y >= r.bodyTop() + 1; i++) {
			drawLine(g, font, tab, lines.get(i).text(), r.x() + 4, y + 1, Draw.rgba(0xFFFFFF, text));
			y -= lineH;
		}
		if (lines.size() > visible) {
			int track = r.h() - TITLE - 4;
			int thumb = Math.max(6, track * visible / lines.size());
			int maxScroll = lines.size() - visible;
			int thumbY = r.bottom() - 2 - thumb - (track - thumb) * tab.scroll / Math.max(1, maxScroll);
			g.fill(r.x() + r.w() - 2, thumbY, r.x() + r.w() - 1, thumbY + thumb, Draw.rgba(0xFFFFFF, 0.35f));
		}
		// resize grip
		int gx = r.x() + r.w() - 6;
		int gy = r.bottom() - 6;
		int grip = Draw.rgba(0xFFFFFF, hovered && hover.part() == Part.GRIP ? 0.8f : 0.4f);
		g.fill(gx + 4, gy, gx + 5, gy + 1, grip);
		g.fill(gx + 2, gy + 2, gx + 3, gy + 3, grip);
		g.fill(gx + 4, gy + 2, gx + 5, gy + 3, grip);
		g.fill(gx, gy + 4, gx + 1, gy + 5, grip);
		g.fill(gx + 2, gy + 4, gx + 3, gy + 5, grip);
		g.fill(gx + 4, gy + 4, gx + 5, gy + 5, grip);
		if (dragging == tab) {
			Draw.outline(g, r.x() - 1, r.y() - 1, r.x() + r.w() + 1, r.bottom() + 1, Draw.rgba(0xFFFFFF, 0.7f));
		}
	}

	// Wrapped lines for a window, newest first
	private static List<Line> lines(ChatTab tab, int wrapWidth, Font font) {
		Cache cache = caches.computeIfAbsent(tab, t -> new Cache());
		List<GuiMessage> all = ChatHelper.allMessages();
		if (cache.messages == TabManager.messagesVersion() && cache.version == TabManager.version()
			&& cache.width == wrapWidth && cache.total == all.size()) {
			return cache.lines;
		}
		List<Line> lines = new ArrayList<>();
		int wrap = wrapWidth;
		int taken = 0;
		for (GuiMessage message : all) {
			if (!TabManager.belongsTo(message, tab)) {
				continue;
			}
			List<FormattedCharSequence> parts = ComponentRenderUtils.wrapComponents(message.content(), wrap, font);
			for (int i = parts.size() - 1; i >= 0; i--) {
				lines.add(new Line(parts.get(i), message));
			}
			if (++taken >= MAX_MESSAGES) {
				break;
			}
		}
		cache.messages = TabManager.messagesVersion();
		cache.version = TabManager.version();
		cache.width = wrapWidth;
		cache.total = all.size();
		cache.lines = lines;
		return lines;
	}

	// --- interaction, only while the chat is open ---

	public static void setMouse(int x, int y) {
		mouseX = x;
		mouseY = y;
	}

	public static @Nullable Hit hitAt(double mx, double my) {
		if (!chatOpen()) {
			return null;
		}
		Minecraft mc = Minecraft.getInstance();
		int sw = mc.getWindow().getGuiScaledWidth();
		int sh = mc.getWindow().getGuiScaledHeight();
		List<ChatTab> windows = windows();
		// last drawn is on top
		for (int i = windows.size() - 1; i >= 0; i--) {
			ChatTab tab = windows.get(i);
			Rect r = rect(tab, sw, sh);
			if (!r.contains(mx, my)) {
				continue;
			}
			if (my < r.bodyTop()) {
				boolean close = mx >= r.x() + r.w() - 11;
				return new Hit(tab, close ? Part.CLOSE : Part.TITLE);
			}
			if (mx >= r.x() + r.w() - 7 && my >= r.bottom() - 7) {
				return new Hit(tab, Part.GRIP);
			}
			return new Hit(tab, Part.BODY);
		}
		return null;
	}

	public static @Nullable GuiMessage messageAt(ChatTab tab, double mx, double my) {
		Minecraft mc = Minecraft.getInstance();
		Rect r = rect(tab, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
		if (my < r.bodyTop() || my >= r.bottom() - 2) {
			return null;
		}
		int fromBottom = (int) ((r.bottom() - 2 - my) / lineHeight(tab));
		List<Line> lines = lines(tab, wrapWidth(tab, r), mc.font);
		int index = tab.scroll + fromBottom;
		return index >= 0 && index < lines.size() ? lines.get(index).message() : null;
	}

	public static void startDrag(ChatTab tab, boolean resize, double mx, double my) {
		Minecraft mc = Minecraft.getInstance();
		Rect r = rect(tab, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
		dragging = tab;
		resizing = resize;
		grabX = resize ? mx - (r.x() + r.w()) : mx - r.x();
		grabY = resize ? my - r.bottom() : my - r.y();
		// clicked windows come to the front
		zOrder.remove(tab);
		zOrder.add(tab);
	}

	public static @Nullable ChatTab dragging() {
		return dragging;
	}

	public static boolean isMoving() {
		return dragging != null && !resizing;
	}

	public static void dragTo(double mx, double my, int sw, int sh) {
		ChatTab tab = dragging;
		if (tab == null) {
			return;
		}
		Rect r = rect(tab, sw, sh);
		if (resizing) {
			tab.windowWidth = Mth.clamp((int) Math.round(mx - grabX) - r.x(), 80, sw - r.x());
			tab.windowHeight = Mth.clamp((int) Math.round(my - grabY) - r.y(), TITLE + 20, sh - r.y());
			// keep the top-left corner where it is
			Rect resized = rect(tab, sw, sh);
			place(tab, r.x(), r.y(), resized.w(), resized.h(), sw, sh);
			return;
		}
		int x = Mth.clamp((int) Math.round(mx - grabX), 0, Math.max(0, sw - r.w()));
		int y = Mth.clamp((int) Math.round(my - grabY), 0, Math.max(0, sh - r.h()));
		place(tab, x, y, r.w(), r.h(), sw, sh);
	}

	private static void place(ChatTab tab, int x, int y, int w, int h, int sw, int sh) {
		int freeX = Math.max(0, sw - w);
		int freeY = Math.max(0, sh - h);
		tab.windowX = freeX == 0 ? 0 : Mth.clamp((float) x / freeX, 0, 1);
		tab.windowY = freeY == 0 ? 0 : Mth.clamp((float) y / freeY, 0, 1);
	}

	// Returns the tab that was being dragged (so the caller can dock it), and saves
	public static @Nullable ChatTab endDrag() {
		ChatTab tab = dragging;
		dragging = null;
		if (tab != null) {
			TabStore.save();
		}
		return tab;
	}

	public static boolean scroll(ChatTab tab, double amount) {
		tab.scroll = Math.max(0, tab.scroll + (amount > 0 ? 1 : -1) * 3);
		return true;
	}

	public static Component title(ChatTab tab) {
		return Component.literal(tab.name);
	}
}
