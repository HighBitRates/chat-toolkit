package dev.highbitrate.chattoolkit.tabs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.highbitrate.chattoolkit.ChatToolkit;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Saved tab presets, in config/chattoolkit/tabs.json
public final class TabStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private static final class Data {
		String active = "SRP default";
		List<TabPreset> presets = new ArrayList<>();
	}

	private static Data data = new Data();

	private TabStore() {
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(ChatToolkit.MOD_ID).resolve("tabs.json");
	}

	public static void load() {
		Path file = path();
		Data loaded = null;
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				loaded = GSON.fromJson(reader, Data.class);
			} catch (Exception e) {
				ChatToolkit.LOGGER.warn("Could not read chat tabs from {}", file, e);
			}
		}
		data = loaded != null ? loaded : new Data();
		if (data.presets == null) {
			data.presets = new ArrayList<>();
		}
		data.presets.removeIf(p -> p == null);
		for (TabPreset preset : data.presets) {
			preset.sanitize();
		}
		if (data.presets.isEmpty()) {
			data.presets.add(TabPreset.srpDefault());
		}
		if (find(data.active) == null) {
			data.active = data.presets.getFirst().name;
		}
		TabManager.reset();
	}

	public static void save() {
		Path file = path();
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling("tabs.json.tmp");
			try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(data, writer);
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			ChatToolkit.LOGGER.warn("Could not save chat tabs to {}", file, e);
		}
	}

	public static List<TabPreset> presets() {
		return data.presets;
	}

	public static TabPreset active() {
		TabPreset preset = find(data.active);
		if (preset == null) {
			preset = data.presets.getFirst();
			data.active = preset.name;
		}
		return preset;
	}

	public static void setActive(TabPreset preset) {
		if (!data.presets.contains(preset)) {
			return;
		}
		data.active = preset.name;
		TabManager.reset();
		save();
	}

	public static @Nullable TabPreset find(String name) {
		for (TabPreset preset : data.presets) {
			if (preset.name.equals(name)) {
				return preset;
			}
		}
		return null;
	}

	public static String uniqueName(String base) {
		if (find(base) == null) {
			return base;
		}
		for (int i = 2; ; i++) {
			String name = base + " " + i;
			if (find(name) == null) {
				return name;
			}
		}
	}

	public static TabPreset add(TabPreset preset) {
		preset.name = uniqueName(preset.name);
		data.presets.add(preset);
		save();
		return preset;
	}

	public static boolean remove(TabPreset preset) {
		if (data.presets.size() <= 1) {
			return false;
		}
		boolean wasActive = preset == active();
		data.presets.remove(preset);
		if (wasActive) {
			data.active = data.presets.getFirst().name;
			TabManager.reset();
		}
		save();
		return true;
	}

	public static void rename(TabPreset preset, String newName) {
		newName = newName.trim();
		if (newName.isEmpty() || newName.equals(preset.name)) {
			return;
		}
		boolean wasActive = preset == active();
		preset.name = uniqueName(newName);
		if (wasActive) {
			data.active = preset.name;
		}
		save();
	}

	// Called when joining a server. Switches to the preset tied to that address, if there is one.
	public static void onJoin(@Nullable String address) {
		if (address == null || address.isBlank()) {
			return;
		}
		String joined = normalize(address);
		for (TabPreset preset : data.presets) {
			if (!preset.server.isBlank() && normalize(preset.server).equals(joined)) {
				if (preset != active()) {
					data.active = preset.name;
					save();
				}
				break;
			}
		}
		TabManager.reset();
	}

	private static String normalize(String address) {
		String s = address.trim().toLowerCase(Locale.ROOT);
		if (s.endsWith(":25565")) {
			s = s.substring(0, s.length() - 6);
		}
		if (s.endsWith(".")) {
			s = s.substring(0, s.length() - 1);
		}
		return s;
	}

	public static String toJson(TabPreset preset) {
		return GSON.toJson(preset);
	}

	// Returns null if the text isn't a preset
	public static @Nullable TabPreset fromJson(String json) {
		try {
			TabPreset preset = GSON.fromJson(json.trim(), TabPreset.class);
			if (preset == null || preset.tabs == null || preset.tabs.isEmpty()) {
				return null;
			}
			preset.sanitize();
			return preset;
		} catch (JsonParseException | IllegalStateException e) {
			return null;
		}
	}

	// Folder for exported preset files: config/chattoolkit/presets
	public static Path presetsFolder() {
		return FabricLoader.getInstance().getConfigDir().resolve(ChatToolkit.MOD_ID).resolve("presets");
	}

	// Writes the preset to its own .json file and returns where it went
	public static Path exportToFile(TabPreset preset) throws IOException {
		Path folder = presetsFolder();
		Files.createDirectories(folder);
		String fileName = preset.name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
		if (fileName.isEmpty()) {
			fileName = "preset";
		}
		Path file = folder.resolve(fileName + ".json");
		Files.writeString(file, toJson(preset), StandardCharsets.UTF_8);
		return file;
	}

	public static @Nullable TabPreset importFile(Path file) {
		try {
			if (Files.size(file) > 1_000_000) {
				return null;
			}
			return fromJson(Files.readString(file, StandardCharsets.UTF_8));
		} catch (IOException e) {
			ChatToolkit.LOGGER.warn("Could not read preset file {}", file, e);
			return null;
		}
	}

	// Preset files in the presets folder, newest first
	public static List<Path> presetFiles() {
		Path folder = presetsFolder();
		List<Path> files = new ArrayList<>();
		if (!Files.isDirectory(folder)) {
			return files;
		}
		try (var stream = Files.list(folder)) {
			stream.filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json") && Files.isRegularFile(f))
				.forEach(files::add);
		} catch (IOException e) {
			ChatToolkit.LOGGER.warn("Could not list {}", folder, e);
		}
		files.sort((a, b) -> {
			try {
				return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
			} catch (IOException e) {
				return 0;
			}
		});
		return files;
	}
}
