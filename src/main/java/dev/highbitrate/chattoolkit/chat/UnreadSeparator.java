package dev.highbitrate.chattoolkit.chat;

import dev.highbitrate.chattoolkit.tabs.TabManager;
import dev.highbitrate.chattoolkit.ui.Draw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;

import java.util.List;

// A thin red line with a small NEW tag above the first message you haven't seen,
// when you switch to a tab that had a red dot
public final class UnreadSeparator {
	private static final int RED = 0xE63C3C;

	private UnreadSeparator() {
	}

	public static void render(GuiGraphicsExtractor g, Font font, int screenHeight) {
		long after = TabManager.separatorAfter();
		ChatComponent chat = ChatHelper.chat();
		if (after <= 0 || chat == null) {
			return;
		}
		List<GuiMessage.Line> lines = ChatHelper.access(chat).chattoolkit$getTrimmedMessages();
		// lines are newest first, so the unread ones are at the front
		int boundary = -1;
		for (int i = 0; i < lines.size(); i++) {
			if (MessageExtras.receivedAt(lines.get(i).parent()) > after) {
				boundary = i;
			} else {
				break;
			}
		}
		// nothing new, or everything is new (no point drawing a line above all of it)
		if (boundary < 0 || boundary >= lines.size() - 1) {
			return;
		}
		int scroll = ChatHelper.access(chat).chattoolkit$getScrollPos();
		int visible = Math.min(lines.size() - scroll, chat.getLinesPerPage());
		int row = boundary - scroll;
		if (row < 0 || row >= visible) {
			return;
		}

		float scale = ChatGeometry.scale();
		int entryHeight = ChatGeometry.entryHeight();
		int entryTop = ChatGeometry.chatBottom(screenHeight) - (row + 1) * entryHeight;
		int y = Math.round(entryTop * scale);
		int right = ChatGeometry.screenRight();

		Component label = Component.translatable("chattoolkit.separator.new");
		int tagWidth = font.width(label) + 4;
		int tagX = right - tagWidth - 2;
		g.fill(0, y, tagX - 2, y + 1, Draw.rgba(RED, 0.85f));
		g.fill(tagX, y - 4, tagX + tagWidth, y + 5, Draw.rgba(RED, 0.9f));
		g.text(font, label, tagX + 2, y - 3, 0xFFFFFFFF, false);
	}
}
