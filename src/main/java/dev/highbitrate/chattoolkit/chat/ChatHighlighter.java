package dev.highbitrate.chattoolkit.chat;

import dev.highbitrate.chattoolkit.config.ChatToolkitConfig;
import dev.highbitrate.chattoolkit.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;

import java.util.List;

// Paints underneath vanilla chat lines while the chat screen is open: a soft tint on the hovered or
// right-clicked message, and a highlight behind every search match
public final class ChatHighlighter {
	private static boolean drawing;
	private static int entryHeight;
	private static int entryBottomToMessageY;
	private static int maxWidth;

	private ChatHighlighter() {
	}

	public static void beginPass(boolean foreground) {
		drawing = foreground && ChatScreenController.current() != null;
		if (drawing) {
			entryHeight = ChatGeometry.entryHeight();
			entryBottomToMessageY = ChatGeometry.entryBottomToMessageY();
			maxWidth = ChatGeometry.maxWidth();
		}
	}

	public static void endPass() {
		drawing = false;
	}

	public static void beforeLine(ChatComponent.ChatGraphicsAccess access, GuiMessage.Line line, int textTop, float opacity) {
		if (!drawing) {
			return;
		}
		ChatScreenController controller = ChatScreenController.current();
		if (controller == null) {
			return;
		}

		int entryBottom = textTop + entryBottomToMessageY;
		int entryTop = entryBottom - entryHeight;
		int tint = controller.tintFor(line.parent());
		if ((tint >>> 24) != 0) {
			access.fill(-4, entryTop, maxWidth + 8, entryBottom, tint);
		}

		if (SearchState.isActive()) {
			LineText text = LineText.of(line.content());
			List<int[]> ranges = SearchState.ranges(text.text());
			if (!ranges.isEmpty()) {
				Font font = Minecraft.getInstance().font;
				float alpha = ChatToolkitConfig.get().highlightOpacity / 100.0f * Math.max(0.0f, Math.min(1.0f, opacity));
				int color = Draw.rgba(Draw.accent(), alpha);
				for (int[] range : ranges) {
					int x0 = text.widthBefore(font, range[0]);
					int x1 = text.widthBefore(font, range[1]);
					access.fill(x0 - 1, textTop - 1, x1, textTop + 8, color);
				}
			}
		}
	}
}
