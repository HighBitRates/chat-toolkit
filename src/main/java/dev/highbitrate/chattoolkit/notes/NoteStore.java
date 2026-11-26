package dev.highbitrate.chattoolkit.notes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.highbitrate.chattoolkit.ChatToolkit;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

// Persistent notes, stored in config/chattoolkit/notes.json
public final class NoteStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final NoteStore INSTANCE = new NoteStore();

	private static final class Data {
		int nextId = 1;
		List<Note> notes = new ArrayList<>();
	}

	private Data data = new Data();

	public static NoteStore get() {
		return INSTANCE;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(ChatToolkit.MOD_ID).resolve("notes.json");
	}

	public void load() {
		Path file = path();
		if (!Files.exists(file)) {
			data = new Data();
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			Data loaded = GSON.fromJson(reader, Data.class);
			data = loaded != null ? loaded : new Data();
			if (data.notes == null) {
				data.notes = new ArrayList<>();
			}
			data.notes.removeIf(n -> n == null || n.text == null);
			for (Note note : data.notes) {
				data.nextId = Math.max(data.nextId, note.id + 1);
			}
		} catch (Exception e) {
			ChatToolkit.LOGGER.warn("Could not read notes from {}", file, e);
			data = new Data();
		}
	}

	public void save() {
		Path file = path();
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling("notes.json.tmp");
			try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(data, writer);
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			ChatToolkit.LOGGER.warn("Could not save notes to {}", file, e);
		}
	}

	public Note add(String text, @Nullable String coords, @Nullable String dimension) {
		Note note = new Note(data.nextId++, text, System.currentTimeMillis(), currentOrigin(), coords, dimension);
		data.notes.add(note);
		save();
		return note;
	}

	public @Nullable Note byId(int id) {
		for (Note note : data.notes) {
			if (note.id == id) {
				return note;
			}
		}
		return null;
	}

	public boolean remove(int id) {
		boolean removed = data.notes.removeIf(n -> n.id == id);
		if (removed) {
			save();
		}
		return removed;
	}

	public boolean setText(int id, String text) {
		Note note = byId(id);
		if (note == null) {
			return false;
		}
		note.text = text;
		save();
		return true;
	}

	public boolean setPinned(int id, boolean pinned) {
		Note note = byId(id);
		if (note == null) {
			return false;
		}
		note.pinned = pinned;
		save();
		return true;
	}

	public int clear() {
		int count = data.notes.size();
		data.notes.clear();
		data.nextId = 1;
		save();
		return count;
	}

	// Newest first
	public List<Note> all() {
		List<Note> list = new ArrayList<>(data.notes);
		Collections.reverse(list);
		return list;
	}

	public List<Note> pinned() {
		List<Note> list = new ArrayList<>();
		for (Note note : data.notes) {
			if (note.pinned) {
				list.add(note);
			}
		}
		return list;
	}

	public List<Note> search(String query) {
		String q = query.toLowerCase(Locale.ROOT);
		List<Note> list = new ArrayList<>();
		for (Note note : all()) {
			if (note.text.toLowerCase(Locale.ROOT).contains(q) || note.origin.toLowerCase(Locale.ROOT).contains(q)) {
				list.add(note);
			}
		}
		return list;
	}

	public int size() {
		return data.notes.size();
	}

	public List<Integer> ids() {
		List<Integer> ids = new ArrayList<>();
		for (Note note : data.notes) {
			ids.add(note.id);
		}
		return ids;
	}

	// Which server (or singleplayer world) you are on, so notes remember where they came from
	public static String currentOrigin() {
		Minecraft mc = Minecraft.getInstance();
		ServerData server = mc.getCurrentServer();
		if (server != null) {
			return server.ip;
		}
		if (mc.getSingleplayerServer() != null) {
			return "Singleplayer: " + mc.getSingleplayerServer().getWorldData().getLevelName();
		}
		return "Menu";
	}
}
