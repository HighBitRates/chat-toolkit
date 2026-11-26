package dev.highbitrate.chattoolkit.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.List;

// Mirrors the layout maths of ChatComponent#extractRenderState (26.3) so we can tell which
// chat line is under the mouse. All "local" values are in the chat's own scaled coordinate space
public final class ChatGeometry {
	// Vanilla's ChatComponent.BOTTOM_MARGIN
	public static final int BOTTOM_MARGIN = 40;

	private ChatGeometry() {
	}

	public static float scale() {
		return (float) Minecraft.getInstance().options.chatScale().get().doubleValue();
	}

	private static double lineSpacing() {
		return Minecraft.getInstance().options.chatLineSpacing().get();
	}

	// Width of the text area in local units
	public static int maxWidth() {
		return Mth.ceil(ChatComponent.getWidth(Minecraft.getInstance().options.chatWidth().get()) / scale());
	}

	public static int entryHeight() {
		return (int) (9.0 * (lineSpacing() + 1.0));
	}

	public static int entryBottomToMessageY() {
		double s = lineSpacing();
		return (int) Math.round(8.0 * (s + 1.0) - 4.0 * s);
	}

	public static int chatBottom(int guiHeight) {
		return Mth.floor((guiHeight - ChatLift.get() - BOTTOM_MARGIN) / scale());
	}

	// Right edge of the chat background, in screen pixels
	public static int screenRight() {
		return Math.round((maxWidth() + 12) * scale());
	}

	// One visible chat line and its rectangle on screen
	public record Hit(GuiMessage message, GuiMessage.Line line, int x0, int y0, int x1, int y1) {
	}

	// The chat line under the given screen position, or null. Only meaningful while the chat screen is open
	public static @Nullable Hit lineAt(double mouseX, double mouseY) {
		ChatComponent chat = ChatHelper.chat();
		if (chat == null) {
			return null;
		}
		List<GuiMessage.Line> lines = ChatHelper.access(chat).chattoolkit$getTrimmedMessages();
		int scroll = ChatHelper.access(chat).chattoolkit$getScrollPos();
		if (lines.isEmpty()) {
			return null;
		}

		float scale = scale();
		int guiHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
		int chatBottom = chatBottom(guiHeight);
		int entryHeight = entryHeight();
		int maxWidth = maxWidth();

		double localX = mouseX / scale - 4.0;
		double localY = mouseY / scale;
		if (localX < -4.0 || localX > maxWidth + 8.0 || localY >= chatBottom || entryHeight <= 0) {
			return null;
		}

		int index = Mth.floor((chatBottom - localY) / entryHeight);
		int visible = Math.min(lines.size() - scroll, chat.getLinesPerPage());
		if (index < 0 || index >= visible) {
			return null;
		}

		GuiMessage.Line line = lines.get(index + scroll);
		int entryBottom = chatBottom - index * entryHeight;
		int entryTop = entryBottom - entryHeight;
		return new Hit(line.parent(), line, 0, Math.round(entryTop * scale), screenRight(), Math.round(entryBottom * scale));
	}
}
