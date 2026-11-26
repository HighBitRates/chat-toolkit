package dev.highbitrate.chattoolkit.hud;

import dev.highbitrate.chattoolkit.ChatToolkit;
import dev.highbitrate.chattoolkit.config.ChatToolkitConfig;
import dev.highbitrate.chattoolkit.notes.Note;
import dev.highbitrate.chattoolkit.notes.NoteStore;
import dev.highbitrate.chattoolkit.text.TextFormatter;
import dev.highbitrate.chattoolkit.timer.TimerManager;
import dev.highbitrate.chattoolkit.ui.Anim;
import dev.highbitrate.chattoolkit.ui.Draw;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

// A small panel with pinned notes and running timers. Sits in a corner until you drag it
// somewhere else with the chat open (right-click it to send it back to the corner)
public final class PinnedHud implements HudElement {
	private static final int MARGIN = 4;
	private static final int SNAP = 6;

	// where the panel was last drawn, for hit testing from the chat screen
	private static boolean shown;
	private static int lastX, lastY, lastW, lastH;

	private static boolean dragging;
	private static double grabX, grabY;

	private static final Identifier ID = Identifier.fromNamespaceAndPath(ChatToolkit.MOD_ID, "pinned");
	private static final int MAX_TEXT_WIDTH = 170;
	private static final int MAX_LINES_PER_NOTE = 3;
	private static final int PAD = 4;
	private static final int LINE = 10;

	private final Anim fade = new Anim(0, 8);

	private record Row(FormattedCharSequence text, int color, float progress) {
	}

	public static void register() {
		HudElementRegistry.addLast(ID, new PinnedHud());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		ChatToolkitConfig config = ChatToolkitConfig.get();
		Minecraft mc = Minecraft.getInstance();
		shown = false;
		if (!config.pinnedHud || mc.player == null || mc.debugEntries.isOverlayVisible()) {
			fade.snap(0);
			return;
		}
		List<Note> notes = NoteStore.get().pinned();
		List<TimerManager.Timer> timers = TimerManager.active();
		if (notes.isEmpty() && timers.isEmpty()) {
			dragging = false;
			fade.snap(0);
			return;
		}
		float alpha = fade.target(1).get();
		if (alpha <= 0.01f) {
			return;
		}

		Font font = mc.font;
		List<Row> rows = new ArrayList<>();
		for (TimerManager.Timer timer : timers) {
			String text = TimerManager.formatClock(timer.remaining()) + (timer.label().isEmpty() ? "" : "  " + timer.label());
			rows.add(new Row(Component.literal(font.plainSubstrByWidth(text, MAX_TEXT_WIDTH)).getVisualOrderText(), Draw.TEXT, timer.progress()));
		}
		for (Note note : notes) {
			Component text = Component.literal("- ").withColor(Draw.accent()).append(TextFormatter.parse(note.text));
			List<FormattedCharSequence> lines = font.split(text, MAX_TEXT_WIDTH);
			for (int i = 0; i < Math.min(lines.size(), MAX_LINES_PER_NOTE); i++) {
				rows.add(new Row(lines.get(i), Draw.TEXT, -1));
			}
		}

		int width = 0;
		for (Row row : rows) {
			width = Math.max(width, font.width(row.text()));
		}
		int panelWidth = width + PAD * 2 + 2;
		int panelHeight = rows.size() * LINE + PAD * 2 - 1;
		int freeX = Math.max(0, graphics.guiWidth() - panelWidth);
		int freeY = Math.max(0, graphics.guiHeight() - panelHeight);
		int x;
		int y;
		if (config.hudX >= 0 && config.hudY >= 0) {
			// saved as a fraction of the free space so it stays on screen when the window or panel size changes
			x = Math.round(config.hudX * freeX);
			y = Math.round(config.hudY * freeY);
		} else {
			x = config.hudCorner == ChatToolkitConfig.HudCorner.TOP_LEFT ? MARGIN : freeX - MARGIN;
			y = MARGIN;
		}
		shown = true;
		lastX = x;
		lastY = y;
		lastW = panelWidth;
		lastH = panelHeight;

		graphics.fill(x, y, x + panelWidth, y + panelHeight, Draw.rgba(0x000000, Math.max(0.25f, Draw.panelAlpha()) * alpha));
		graphics.fill(x, y, x + 2, y + panelHeight, Draw.rgba(Draw.accent(), 0.85f * alpha));

		int textY = y + PAD;
		for (Row row : rows) {
			graphics.text(font, row.text(), x + PAD + 2, textY, Draw.rgba(row.color(), alpha), true);
			if (row.progress() >= 0) {
				int barWidth = Math.round(width * row.progress());
				graphics.fill(x + PAD + 2, textY + 9, x + PAD + 2 + width, textY + 10, Draw.rgba(Draw.WHITE, 0.12f * alpha));
				graphics.fill(x + PAD + 2, textY + 9, x + PAD + 2 + barWidth, textY + 10, Draw.rgba(Draw.accent(), 0.8f * alpha));
			}
			textY += LINE;
		}
		if (!Anim.enabled()) {
			fade.snap(1);
		}
	}

	public static boolean contains(double mx, double my) {
		return shown && mx >= lastX && mx < lastX + lastW && my >= lastY && my < lastY + lastH;
	}

	public static boolean isDragging() {
		return dragging;
	}

	public static void startDrag(double mx, double my) {
		dragging = true;
		grabX = mx - lastX;
		grabY = my - lastY;
	}

	// follows the mouse, snapping to the screen edges when close
	public static void dragTo(double mx, double my, int screenW, int screenH) {
		if (!dragging) {
			return;
		}
		int freeX = Math.max(0, screenW - lastW);
		int freeY = Math.max(0, screenH - lastH);
		int x = (int) Math.round(mx - grabX);
		int y = (int) Math.round(my - grabY);
		x = snap(x, freeX);
		y = snap(y, freeY);
		ChatToolkitConfig config = ChatToolkitConfig.get();
		config.hudX = freeX == 0 ? 0 : (float) x / freeX;
		config.hudY = freeY == 0 ? 0 : (float) y / freeY;
		lastX = x;
		lastY = y;
	}

	private static int snap(int pos, int max) {
		if (Math.abs(pos - MARGIN) <= SNAP) {
			return Math.min(MARGIN, max);
		}
		if (Math.abs(pos - (max - MARGIN)) <= SNAP) {
			return Math.max(0, max - MARGIN);
		}
		return Math.max(0, Math.min(max, pos));
	}

	public static void endDrag() {
		if (dragging) {
			dragging = false;
			ChatToolkitConfig.save();
		}
	}

	public static void resetPosition() {
		ChatToolkitConfig config = ChatToolkitConfig.get();
		config.hudX = -1;
		config.hudY = -1;
		ChatToolkitConfig.save();
	}

	// thin outline while hovering or dragging so you can tell it can be moved
	public static void drawDragHint(GuiGraphicsExtractor graphics, boolean hovered) {
		if (!shown || (!hovered && !dragging)) {
			return;
		}
		Draw.outline(graphics, lastX - 1, lastY - 1, lastX + lastW + 1, lastY + lastH + 1, Draw.rgba(Draw.WHITE, dragging ? 0.6f : 0.35f));
	}
}
