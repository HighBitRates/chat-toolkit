package dev.highbitrate.chattoolkit.chat;

import dev.highbitrate.chattoolkit.config.ChatToolkitConfig;
import dev.highbitrate.chattoolkit.tabs.TabManager;
import dev.highbitrate.chattoolkit.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;

// Helpers for writing long RP messages:
//  - a character counter at the end of the input box that turns orange near the limit
//  - when a message ends with "--" (the server joins it with the next one), the part you
//    just sent stays above the input box in grey so you know where to carry on from
//  - a "draft kept" note when the chat reopens with text you hadn't sent yet
public final class TypingAids {
	public static final int LIMIT = 256;
	public static final int PREVIEW_HEIGHT = 12;
	private static final String APPEND = "--";
	private static final int WARN = 0xFF8C4D;

	private static String lastPart = "";
	private static int parts;
	private static boolean draftRestored;

	private TypingAids() {
	}

	// --- append preview ---

	public static void onSent(String typed) {
		if (typed.stripTrailing().endsWith(APPEND)) {
			lastPart = typed.stripTrailing();
			parts++;
		} else {
			clearAppend();
		}
	}

	public static void clearAppend() {
		lastPart = "";
		parts = 0;
	}

	private static boolean previewShown() {
		return parts > 0 && ChatToolkitConfig.get().appendPreview && Minecraft.getInstance().gui.screen() instanceof ChatScreen;
	}

	// Extra room the preview needs above the input box (the chat, search bar and tabs move up by this)
	public static int previewHeight() {
		return previewShown() ? PREVIEW_HEIGHT : 0;
	}

	// --- drafts ---

	public static void markDraftRestored(boolean restored) {
		draftRestored = restored;
	}

	public static void onEdited() {
		draftRestored = false;
	}

	// --- drawing ---

	// How much room to leave at the right end of the input box
	public static int reservedWidth(Font font) {
		int w = 0;
		if (ChatToolkitConfig.get().charCounter) {
			w += font.width(LIMIT + "/" + LIMIT) + 10;
		}
		if (draftRestored) {
			w += font.width(Component.translatable("chattoolkit.typing.draft_kept")) + 6;
		}
		return w;
	}

	public static void render(GuiGraphicsExtractor g, Font font, int screenWidth, int screenHeight, String value) {
		ChatToolkitConfig config = ChatToolkitConfig.get();
		int textY = screenHeight - 12;
		int right = screenWidth - 5;

		if (config.charCounter && !value.isEmpty()) {
			// count what will actually be sent, including a tab's "send messages as" command
			int length = TabManager.transformOutgoing(value).length();
			int left = LIMIT - length;
			boolean warn = left <= config.charWarnAt;
			String count = length + "/" + LIMIT;
			int w = font.width(count);
			g.text(font, count, right - w, textY, Draw.rgba(warn ? WARN : 0x787878, 1), false);
			right -= w + 6;
		}
		if (draftRestored && !value.isEmpty()) {
			Component note = Component.translatable("chattoolkit.typing.draft_kept");
			g.text(font, note, right - font.width(note), textY, Draw.rgba(0x5F5F5F, 1), false);
		}

		if (previewShown()) {
			int y = screenHeight - 26;
			g.fill(2, y, screenWidth - 2, y + 11, Draw.rgba(0x000000, 0.35f));
			Component tag = Component.translatable("chattoolkit.typing.part", parts);
			int tagW = font.width(tag);
			g.text(font, tag, screenWidth - 5 - tagW, y + 2, Draw.rgba(0x6E6E6E, 1), false);
			int room = screenWidth - 4 - tagW - 14;
			String shown = lastPart;
			if (font.width(shown) > room) {
				// keep the end, that's the bit you're continuing from
				String tail = font.plainSubstrByWidth(new StringBuilder(shown).reverse().toString(), room - font.width("..."));
				shown = "..." + new StringBuilder(tail).reverse();
			}
			g.text(font, shown, 4, y + 2, Draw.rgba(0x969696, 0.75f), false);
		}
	}
}
