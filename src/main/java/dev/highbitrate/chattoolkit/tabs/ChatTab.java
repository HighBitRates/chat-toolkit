package dev.highbitrate.chattoolkit.tabs;

import java.util.ArrayList;
import java.util.List;

public final class ChatTab {
	public String name = "Tab";
	public int color = 0xFFFFFF;
	public List<TabRule> rules = new ArrayList<>();
	public boolean dot = true;
	public String sendAs = "";
	public boolean popped;
	// muted tabs never get a red dot and are drawn dimmed
	public boolean muted;

	// window position as a fraction of the free screen space (-1 = not placed yet), size in gui pixels
	public float windowX = -1;
	public float windowY = -1;
	public int windowWidth = 220;
	public int windowHeight = 70;

	public transient boolean unread;
	public transient int scroll;
	// when you last looked at this tab, and where the "new" line goes while you're looking at it again
	public transient long lastViewed;
	public transient long separatorAfter;

	public ChatTab() {
	}

	public ChatTab(String name, int color, boolean popped, TabRule... rules) {
		this.name = name;
		this.color = color;
		this.popped = popped;
		this.rules.addAll(List.of(rules));
	}

	// Whether new messages should light up this tab
	public boolean notifies() {
		return dot && !muted;
	}

	public boolean isCatchAll() {
		for (TabRule rule : rules) {
			if (rule.isCatchAll()) {
				return true;
			}
		}
		return false;
	}

	public boolean matches(StyledText text) {
		for (TabRule rule : rules) {
			if (rule.matches(text)) {
				return true;
			}
		}
		return false;
	}

	public ChatTab copy() {
		ChatTab tab = new ChatTab();
		tab.name = name;
		tab.color = color;
		tab.dot = dot;
		tab.sendAs = sendAs;
		tab.popped = popped;
		tab.muted = muted;
		tab.windowX = windowX;
		tab.windowY = windowY;
		tab.windowWidth = windowWidth;
		tab.windowHeight = windowHeight;
		for (TabRule rule : rules) {
			tab.rules.add(rule.copy());
		}
		return tab;
	}

	void sanitize() {
		if (name == null || name.isBlank()) {
			name = "Tab";
		}
		if (rules == null) {
			rules = new ArrayList<>();
		}
		rules.removeIf(r -> r == null || r.kind == null);
		for (TabRule rule : rules) {
			if (rule.text == null) {
				rule.text = "";
			}
		}
		if (sendAs == null) {
			sendAs = "";
		}
		windowWidth = Math.max(80, Math.min(600, windowWidth));
		windowHeight = Math.max(30, Math.min(400, windowHeight));
		if (windowX > 1 || windowY > 1) {
			windowX = -1;
			windowY = -1;
		}
	}
}
