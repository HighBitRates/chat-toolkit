package dev.highbitrate.chattoolkit.timer;

import dev.highbitrate.chattoolkit.chat.ChatHelper;
import dev.highbitrate.chattoolkit.config.ChatToolkitConfig;
import dev.highbitrate.chattoolkit.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Countdown reminders for /ctimer. They run on real time, so they keep counting in menus and while lagging
public final class TimerManager {
	public record Timer(int id, String label, long startedAt, long endsAt) {
		public long remaining() {
			return Math.max(0, endsAt - System.currentTimeMillis());
		}

		public float progress() {
			long total = Math.max(1, endsAt - startedAt);
			return 1.0f - (float) remaining() / total;
		}
	}

	private static final Pattern PART = Pattern.compile("(\\d+(?:\\.\\d+)?)(h|hr|hrs|hours?|m|min|mins|minutes?|s|sec|secs|seconds?|t|ticks?)?");
	private static final long MAX_DURATION = 7L * 24 * 3600 * 1000;
	private static final int MAX_TIMERS = 20;

	private static final List<Timer> TIMERS = new ArrayList<>();
	private static int nextId = 1;

	private TimerManager() {
	}

	public static Timer start(long millis, String label) {
		if (TIMERS.size() >= MAX_TIMERS) {
			TIMERS.removeFirst();
		}
		long now = System.currentTimeMillis();
		Timer timer = new Timer(nextId++, label, now, now + millis);
		TIMERS.add(timer);
		return timer;
	}

	public static boolean cancel(int id) {
		return TIMERS.removeIf(t -> t.id() == id);
	}

	public static int cancelAll() {
		int count = TIMERS.size();
		TIMERS.clear();
		return count;
	}

	public static List<Timer> active() {
		return List.copyOf(TIMERS);
	}

	public static void tick() {
		if (TIMERS.isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		Iterator<Timer> it = TIMERS.iterator();
		List<Timer> done = new ArrayList<>();
		while (it.hasNext()) {
			Timer timer = it.next();
			if (now >= timer.endsAt()) {
				it.remove();
				done.add(timer);
			}
		}
		for (Timer timer : done) {
			ring(timer);
		}
	}

	private static void ring(Timer timer) {
		Minecraft mc = Minecraft.getInstance();
		MutableComponent message = Component.translatable("chattoolkit.timer.done").withColor(Draw.accent());
		if (!timer.label().isEmpty()) {
			message.append(Component.literal(": " + timer.label()).withColor(0xFFFFFF));
		}
		message.append(Component.literal(" "))
			.append(Component.translatable("chattoolkit.timer.again").setStyle(Style.EMPTY
				.withColor(Draw.TEXT_DIM)
				.withClickEvent(new ClickEvent.SuggestCommand("/ctimer " + formatShort(timer.endsAt() - timer.startedAt()) + (timer.label().isEmpty() ? "" : " " + timer.label())))
				.withHoverEvent(new HoverEvent.ShowText(Component.translatable("chattoolkit.timer.again.hover")))));
		ChatHelper.print(message);
		if (ChatToolkitConfig.get().timerSound) {
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 1.2f));
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL, 0.9f));
		}
	}

	// Parses "90", "90s", "5m", "1h30m", "2.5m", "1:30", "1:02:03" or "100t" (ticks)
	public static long parseDuration(String text) throws IllegalArgumentException {
		String s = text.trim().toLowerCase(Locale.ROOT).replace(" ", "");
		if (s.isEmpty()) {
			throw new IllegalArgumentException("empty");
		}
		long millis;
		if (s.contains(":")) {
			String[] parts = s.split(":");
			if (parts.length > 3) {
				throw new IllegalArgumentException(text);
			}
			long total = 0;
			for (String part : parts) {
				if (!part.matches("\\d+")) {
					throw new IllegalArgumentException(text);
				}
				total = total * 60 + Long.parseLong(part);
			}
			millis = total * 1000;
		} else {
			Matcher m = PART.matcher(s);
			int pos = 0;
			double total = 0;
			while (pos < s.length()) {
				if (!m.find(pos) || m.start() != pos || m.end() == pos) {
					throw new IllegalArgumentException(text);
				}
				double amount = Double.parseDouble(m.group(1));
				String unit = m.group(2) == null ? "s" : m.group(2);
				total += amount * switch (unit.charAt(0)) {
					case 'h' -> 3_600_000.0;
					case 'm' -> 60_000.0;
					case 't' -> 50.0;
					default -> 1_000.0;
				};
				pos = m.end();
			}
			millis = Math.round(total);
		}
		if (millis <= 0 || millis > MAX_DURATION) {
			throw new IllegalArgumentException(text);
		}
		return millis;
	}

	// 5025000 -> "1h 23m 45s"
	public static String formatLong(long millis) {
		long seconds = Math.max(0, (millis + 999) / 1000);
		long h = seconds / 3600;
		long m = (seconds % 3600) / 60;
		long s = seconds % 60;
		StringBuilder sb = new StringBuilder();
		if (h > 0) {
			sb.append(h).append("h ");
		}
		if (m > 0 || h > 0) {
			sb.append(m).append("m ");
		}
		sb.append(s).append('s');
		return sb.toString();
	}

	// 5025000 -> "1h23m45s" (re-parsable)
	public static String formatShort(long millis) {
		return formatLong(millis).replace(" ", "");
	}

	// 5025000 -> "1:23:45", 125000 -> "2:05"
	public static String formatClock(long millis) {
		long seconds = Math.max(0, (millis + 999) / 1000);
		long h = seconds / 3600;
		long m = (seconds % 3600) / 60;
		long s = seconds % 60;
		return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) : String.format(Locale.ROOT, "%d:%02d", m, s);
	}
}
