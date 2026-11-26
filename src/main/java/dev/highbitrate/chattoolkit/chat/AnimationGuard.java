package dev.highbitrate.chattoolkit.chat;

import dev.highbitrate.chattoolkit.ChatToolkit;
import net.minecraft.client.gui.components.ChatComponent;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Locale;

// Chat Animation slides the whole chat up every time a message arrives, even if that message
// went to another tab or is hidden by a search. That makes the chat jump for nothing, so when
// a message isn't shown we put its "last message" timer back to what it was.
public final class AnimationGuard {
	private static boolean searched;
	private static @Nullable Field timer;
	private static long saved;
	private static boolean shown = true;

	private AnimationGuard() {
	}

	private static @Nullable Field timer() {
		if (!searched) {
			searched = true;
			for (Field field : ChatComponent.class.getDeclaredFields()) {
				if (field.getType() == long.class && !Modifier.isStatic(field.getModifiers())
					&& field.getName().toLowerCase(Locale.ROOT).contains("lastmessagetime")) {
					try {
						field.setAccessible(true);
						timer = field;
						ChatToolkit.LOGGER.info("Found Chat Animation's message timer ({}), new messages in other tabs won't bounce the chat", field.getName());
					} catch (RuntimeException e) {
						ChatToolkit.LOGGER.warn("Couldn't access {}", field.getName(), e);
					}
					break;
				}
			}
		}
		return timer;
	}

	public static void before(ChatComponent chat) {
		shown = true;
		Field field = timer();
		if (field != null) {
			try {
				saved = field.getLong(chat);
			} catch (IllegalAccessException ignored) {
			}
		}
	}

	// set by the display queue hook when a message is kept out of the main chat
	public static void markHidden() {
		shown = false;
	}

	public static void after(ChatComponent chat) {
		Field field = timer();
		if (field != null && !shown) {
			try {
				field.setLong(chat, saved);
			} catch (IllegalAccessException ignored) {
			}
		}
		shown = true;
	}
}
