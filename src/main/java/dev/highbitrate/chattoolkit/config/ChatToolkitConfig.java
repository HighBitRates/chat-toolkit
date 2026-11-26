package dev.highbitrate.chattoolkit.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.highbitrate.chattoolkit.ChatToolkit;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

// All user settings. Saved as config/chattoolkit.json
public final class ChatToolkitConfig {
	public enum SearchWidth { MATCH_CHAT, FULL }

	public enum HudCorner { TOP_LEFT, TOP_RIGHT }

	public enum AngleMode { DEGREES, RADIANS }

	// --- Search ---
	public boolean searchBar = true;
	public SearchWidth searchWidth = SearchWidth.MATCH_CHAT;
	public boolean rememberSearch = false;
	public boolean caseSensitive = false;
	public boolean wholeWords = false;
	public boolean regex = false;
	public int historySize = 1000;

	// --- Tabs ---
	public boolean tabsEnabled = true;
	public boolean unreadSeparator = true;

	// --- Chat ---
	public boolean stackMessages = true;
	public int stackWindowSeconds = 60;

	// --- Position ---
	public boolean liftChat = true;
	public int chatOffset = 0;

	// --- Look & feel ---
	public boolean matchChatOpacity = true;
	public int panelOpacity = 60;
	public int accentColor = 0xFFC94D;
	public int highlightOpacity = 45;
	public boolean animations = true;
	public int animationSpeed = 100;
	public boolean hoverHighlight = true;

	// --- Right-click menu ---
	public boolean contextMenu = true;
	public boolean middleClickCopy = true;
	public boolean showTimestamps = true;
	public boolean clock24h = false;

	// --- Commands ---
	public boolean overrideClientCommands = true;
	public AngleMode calcAngles = AngleMode.DEGREES;
	public int calcDecimals = 10;
	public boolean calcSeparators = true;
	public boolean calcStacks = true;

	// --- HUD ---
	public boolean pinnedHud = true;
	public HudCorner hudCorner = HudCorner.TOP_LEFT;
	// custom spot for the pinned panel, -1 means use hudCorner
	public float hudX = -1;
	public float hudY = -1;
	public boolean timerSound = true;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ChatToolkitConfig instance = new ChatToolkitConfig();

	public static ChatToolkitConfig get() {
		return instance;
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(ChatToolkit.MOD_ID + ".json");
	}

	public static void load() {
		Path file = path();
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				ChatToolkitConfig loaded = GSON.fromJson(reader, ChatToolkitConfig.class);
				instance = loaded != null ? loaded : new ChatToolkitConfig();
			} catch (Exception e) {
				ChatToolkit.LOGGER.warn("Could not read {}, using defaults", file, e);
				instance = new ChatToolkitConfig();
			}
		} else {
			instance = new ChatToolkitConfig();
		}
		instance.sanitize();
		save();
	}

	public static void save() {
		Path file = path();
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, writer);
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			ChatToolkit.LOGGER.warn("Could not save {}", file, e);
		}
	}

	public static void reset() {
		instance = new ChatToolkitConfig();
		save();
	}

	// Clamps everything into a valid range (the file is hand-editable)
	public void sanitize() {
		if (searchWidth == null) {
			searchWidth = SearchWidth.MATCH_CHAT;
		}
		if (hudCorner == null) {
			hudCorner = HudCorner.TOP_LEFT;
		}
		if (calcAngles == null) {
			calcAngles = AngleMode.DEGREES;
		}
		historySize = clamp(historySize, 100, 10_000);
		stackWindowSeconds = clamp(stackWindowSeconds, 0, 600);
		panelOpacity = clamp(panelOpacity, 0, 100);
		chatOffset = clamp(chatOffset, 0, 100);
		if (hudX > 1 || hudY > 1 || hudX < 0 || hudY < 0) {
			hudX = -1;
			hudY = -1;
		}
		highlightOpacity = clamp(highlightOpacity, 5, 100);
		animationSpeed = clamp(animationSpeed, 25, 300);
		calcDecimals = clamp(calcDecimals, 0, 15);
		accentColor &= 0xFFFFFF;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
