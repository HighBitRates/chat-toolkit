package dev.highbitrate.chattoolkit.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Pulls useful bits (who sent it, links, coordinates) out of a chat message's text
public final class MessageAnalyzer {
	private static final Pattern VANILLA_CHAT = Pattern.compile("^<([A-Za-z0-9_]{1,16})> ");
	private static final Pattern WHISPER = Pattern.compile("^([A-Za-z0-9_]{1,16}) whispers to you: ");
	private static final Pattern URL = Pattern.compile("(?i)\\b((?:https?://|www\\.)[^\\s<>\"']*[^\\s<>\"'.,;:!?)\\]])");
	private static final Pattern COORDS = Pattern.compile(
		"(?i)(?<![\\w.])(?:x\\s*[:=]?\\s*)?(-?\\d{1,8})(?:\\.\\d+)?[\\s,/]+(?:y\\s*[:=]?\\s*)?(-?\\d{1,3})(?:\\.\\d+)?[\\s,/]+(?:z\\s*[:=]?\\s*)?(-?\\d{1,8})(?:\\.\\d+)?(?![\\w.])");
	private static final int SENDER_SEARCH_LENGTH = 64;

	private MessageAnalyzer() {
	}

	// Best guess at who sent the message: vanilla "<name>" format, or the first online player named near the start
	public static @Nullable String sender(String plain) {
		Matcher vanilla = VANILLA_CHAT.matcher(plain);
		if (vanilla.find()) {
			return vanilla.group(1);
		}
		Matcher whisper = WHISPER.matcher(plain);
		if (whisper.find()) {
			return whisper.group(1);
		}
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		if (connection == null) {
			return null;
		}
		String head = plain.length() > SENDER_SEARCH_LENGTH ? plain.substring(0, SENDER_SEARCH_LENGTH) : plain;
		String best = null;
		int bestIndex = Integer.MAX_VALUE;
		for (PlayerInfo info : connection.getOnlinePlayers()) {
			String name = info.getProfile().name();
			if (name == null || name.isEmpty()) {
				continue;
			}
			int index = indexOfWord(head, name);
			if (index >= 0 && (index < bestIndex || (index == bestIndex && name.length() > best.length()))) {
				best = name;
				bestIndex = index;
			}
		}
		return best;
	}

	public static boolean isSelf(String name) {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && name.equalsIgnoreCase(mc.player.getGameProfile().name());
	}

	public static List<String> urls(String plain) {
		List<String> out = new ArrayList<>();
		Matcher m = URL.matcher(plain);
		while (m.find() && out.size() < 3) {
			out.add(m.group(1));
		}
		return out;
	}

	// "x y z" if the message seems to contain block coordinates
	public static @Nullable String coordinates(String plain) {
		Matcher m = COORDS.matcher(plain);
		if (m.find()) {
			int y = Integer.parseInt(m.group(2));
			if (y >= -64 && y <= 320) {
				return m.group(1) + " " + m.group(2) + " " + m.group(3);
			}
		}
		return null;
	}

	private static int indexOfWord(String text, String word) {
		int from = 0;
		while (true) {
			int index = text.indexOf(word, from);
			if (index < 0) {
				return -1;
			}
			boolean startOk = index == 0 || !isNameChar(text.charAt(index - 1));
			int end = index + word.length();
			boolean endOk = end >= text.length() || !isNameChar(text.charAt(end));
			if (startOk && endOk) {
				return index;
			}
			from = index + 1;
		}
	}

	private static boolean isNameChar(char c) {
		return Character.isLetterOrDigit(c) || c == '_';
	}
}
