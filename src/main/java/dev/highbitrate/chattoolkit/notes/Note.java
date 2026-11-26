package dev.highbitrate.chattoolkit.notes;

import org.jspecify.annotations.Nullable;

// One saved note. text may contain &-codes and markdown. Coordinates are optional
public final class Note {
	public int id;
	public String text = "";
	public long created;
	public String origin = "";
	public boolean pinned;
	public @Nullable String coords;
	public @Nullable String dimension;

	public Note() {
	}

	public Note(int id, String text, long created, String origin, @Nullable String coords, @Nullable String dimension) {
		this.id = id;
		this.text = text;
		this.created = created;
		this.origin = origin;
		this.coords = coords;
		this.dimension = dimension;
	}
}
