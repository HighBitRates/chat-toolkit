package dev.highbitrate.chattoolkit.command;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

// Builders for the toolkit's chat feedback, so every command looks the same
public final class Msg {
	public static final int GRAY = 0xA0A0A0;
	public static final int DARK_GRAY = 0x707070;
	public static final int WHITE = 0xFFFFFF;
	public static final int RED = 0xFF6B6B;

	private Msg() {
	}

	public static MutableComponent text(String text, int color) {
		return Component.literal(text).withColor(color);
	}

	public static MutableComponent tr(String key, int color, Object... args) {
		return Component.translatable(key, args).withColor(color);
	}

	// A small [label] button
	public static MutableComponent button(Component label, ClickEvent click, Component hover) {
		return Component.literal("[").append(label).append("]").setStyle(Style.EMPTY
			.withColor(GRAY)
			.withClickEvent(click)
			.withHoverEvent(new HoverEvent.ShowText(hover)));
	}

	public static MutableComponent button(String labelKey, ClickEvent click, String hoverKey, Object... hoverArgs) {
		return button(Component.translatable(labelKey), click, Component.translatable(hoverKey, hoverArgs));
	}

	// Text that copies value when clicked and inserts it when shift-clicked
	public static MutableComponent copyable(String display, String value, int color, boolean bold) {
		return Component.literal(display).setStyle(Style.EMPTY
			.withColor(color)
			.withBold(bold)
			.withInsertion(value)
			.withClickEvent(new ClickEvent.CopyToClipboard(value))
			.withHoverEvent(new HoverEvent.ShowText(Component.translatable("chattoolkit.click_to_copy", value))));
	}

	public static MutableComponent space() {
		return Component.literal(" ");
	}
}
