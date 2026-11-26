package dev.highbitrate.chattoolkit.tabs;

import java.util.ArrayList;
import java.util.List;

public final class TabPreset {
	public String name = "Preset";
	// server address this preset loads on, empty = none
	public String server = "";
	public List<ChatTab> tabs = new ArrayList<>();

	public static TabPreset srpDefault() {
		TabPreset preset = new TabPreset();
		preset.name = "SRP default";
		preset.tabs.add(new ChatTab("RP", 0xFFFFFF, false, new TabRule(TabRule.Kind.EVERYTHING_ELSE, "")));
		preset.tabs.add(new ChatTab("OOC", 0xAAAAAA, false, new TabRule(TabRule.Kind.CONTAINS, "&8[&7OOC&8]&r")));
		preset.tabs.add(new ChatTab("LOOC", 0xAAAAAA, false, new TabRule(TabRule.Kind.CONTAINS, "&8[&7LOOC&8]")));
		preset.tabs.add(new ChatTab("Friends", 0xFF55FF, false, new TabRule(TabRule.Kind.STARTS_WITH, "&d[&5&lF&d]")));
		return preset;
	}

	public static TabPreset blank(String name) {
		TabPreset preset = new TabPreset();
		preset.name = name;
		preset.tabs.add(new ChatTab("All", 0xFFFFFF, false, new TabRule(TabRule.Kind.EVERYTHING_ELSE, "")));
		return preset;
	}

	public TabPreset copy(String newName) {
		TabPreset preset = new TabPreset();
		preset.name = newName;
		preset.server = server;
		for (ChatTab tab : tabs) {
			preset.tabs.add(tab.copy());
		}
		return preset;
	}

	void sanitize() {
		if (name == null || name.isBlank()) {
			name = "Preset";
		}
		if (server == null) {
			server = "";
		}
		if (tabs == null) {
			tabs = new ArrayList<>();
		}
		tabs.removeIf(t -> t == null);
		while (tabs.size() > 31) {
			tabs.removeLast();
		}
		for (ChatTab tab : tabs) {
			tab.sanitize();
		}
		if (tabs.isEmpty()) {
			tabs.add(new ChatTab("All", 0xFFFFFF, false, new TabRule(TabRule.Kind.EVERYTHING_ELSE, "")));
		}
	}
}
