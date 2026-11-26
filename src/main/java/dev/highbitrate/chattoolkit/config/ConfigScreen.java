package dev.highbitrate.chattoolkit.config;

import com.mojang.blaze3d.platform.InputConstants;
import dev.highbitrate.chattoolkit.ChatToolkit;
import dev.highbitrate.chattoolkit.chat.SearchState;
import dev.highbitrate.chattoolkit.command.ToolkitCommands;
import dev.highbitrate.chattoolkit.tabs.HowToUseScreen;
import dev.highbitrate.chattoolkit.tabs.TabEditorScreen;
import dev.highbitrate.chattoolkit.tabs.TabManager;
import dev.highbitrate.chattoolkit.tabs.TabStore;
import dev.highbitrate.chattoolkit.ui.Anim;
import dev.highbitrate.chattoolkit.ui.Draw;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

// The settings screen (also opened from Mod Menu, /ctk, or a key bind). Hand-drawn in the same square,
// translucent style as the chat, with a live preview at the top. Every change applies immediately
public final class ConfigScreen extends Screen {
	private static final int ROW_HEIGHT = 22;
	private static final int SECTION_HEIGHT = 20;
	private static final int HEADER_TOP = 12;
	private static final int PREVIEW_HEIGHT = 30;
	private static final int FOOTER_HEIGHT = 32;
	private static final long TOOLTIP_DELAY = 350;
	private static final int[] PRESETS = {0xFFC94D, 0x55D6FF, 0x7CE38B, 0xFF7AC6, 0xB18CFF, 0xFF8A4D, 0xFFFFFF};

	private final @Nullable Screen parent;
	private final List<Row> rows = new ArrayList<>();
	private final Anim scroll = new Anim(0, 16);
	private final Anim doneHover = new Anim(0, 20);
	private final Anim resetHover = new Anim(0, 20);
	private final long openedAt = System.currentTimeMillis();
	private final boolean overrideAtOpen;
	private float scrollTarget;
	private int contentHeight;
	private int panelX;
	private int panelWidth;
	private int listTop;
	private int listBottom;
	private @Nullable Slider dragging;
	private @Nullable EditBox focusedField;
	private @Nullable Row tooltipRow;
	private long tooltipSince;
	private long resetArmedAt = -1;

	public ConfigScreen(@Nullable Screen parent) {
		super(Component.translatable("chattoolkit.config.title"));
		this.parent = parent;
		this.overrideAtOpen = ChatToolkitConfig.get().overrideClientCommands;
	}

	@Override
	protected void init() {
		panelWidth = Math.min(380, this.width - 24);
		panelX = (this.width - panelWidth) / 2;
		listTop = HEADER_TOP + 26 + PREVIEW_HEIGHT + 8;
		listBottom = this.height - FOOTER_HEIGHT;
		buildRows();
		setScroll(scrollTarget, true);
	}

	// rows

	private void buildRows() {
		ChatToolkitConfig c = ChatToolkitConfig.get();
		rows.clear();

		section("tabs");
		buttonRow("edit_tabs", () -> Component.translatable("chattoolkit.config.option.edit_tabs.button"),
			() -> this.minecraft.gui.setScreen(TabEditorScreen.open(this)),
			() -> Component.translatable("chattoolkit.config.option.edit_tabs.detail", TabStore.active().name));
		toggle("tabs_enabled", () -> c.tabsEnabled, v -> {
			c.tabsEnabled = v;
			TabManager.changed();
		});
		buttonRow("tabs_help", () -> Component.translatable("chattoolkit.config.option.tabs_help.button"),
			() -> this.minecraft.gui.setScreen(new HowToUseScreen(this)), null);

		section("search");
		toggle("search_bar", () -> c.searchBar, v -> c.searchBar = v);
		choice("search_width", ChatToolkitConfig.SearchWidth.values(), () -> c.searchWidth, v -> c.searchWidth = v);
		toggle("lift_chat", () -> c.liftChat, v -> c.liftChat = v);
		slider("chat_offset", 0, 60, 2, () -> c.chatOffset, v -> c.chatOffset = v, v -> Component.translatable("chattoolkit.config.value.pixels", v));
		toggle("remember_search", () -> c.rememberSearch, v -> c.rememberSearch = v);
		toggle("case_sensitive", () -> c.caseSensitive, v -> c.caseSensitive = v);
		toggle("whole_words", () -> c.wholeWords, v -> c.wholeWords = v);
		toggle("regex", () -> c.regex, v -> c.regex = v);
		slider("history_size", 100, 5000, 100, () -> c.historySize, v -> c.historySize = v,
			v -> Component.translatable("chattoolkit.config.value.messages", String.format(Locale.ROOT, "%,d", v)));

		section("look");
		toggle("match_chat_opacity", () -> c.matchChatOpacity, v -> c.matchChatOpacity = v);
		slider("panel_opacity", 0, 100, 5, () -> c.panelOpacity, v -> c.panelOpacity = v, ConfigScreen::percent);
		color("accent_color", () -> c.accentColor, v -> c.accentColor = v);
		slider("highlight_opacity", 5, 100, 5, () -> c.highlightOpacity, v -> c.highlightOpacity = v, ConfigScreen::percent);
		toggle("hover_highlight", () -> c.hoverHighlight, v -> c.hoverHighlight = v);
		toggle("animations", () -> c.animations, v -> c.animations = v);
		slider("animation_speed", 25, 300, 25, () -> c.animationSpeed, v -> c.animationSpeed = v,
			v -> Component.literal(String.format(Locale.ROOT, "%.2fx", v / 100.0)));

		section("menu");
		toggle("context_menu", () -> c.contextMenu, v -> c.contextMenu = v);
		toggle("middle_click_copy", () -> c.middleClickCopy, v -> c.middleClickCopy = v);
		toggle("show_timestamps", () -> c.showTimestamps, v -> c.showTimestamps = v);
		toggle("clock_24h", () -> c.clock24h, v -> c.clock24h = v);

		section("commands");
		toggle("override_client_commands", () -> c.overrideClientCommands, v -> c.overrideClientCommands = v);
		choice("calc_angles", ChatToolkitConfig.AngleMode.values(), () -> c.calcAngles, v -> c.calcAngles = v);
		slider("calc_decimals", 0, 15, 1, () -> c.calcDecimals, v -> c.calcDecimals = v, v -> Component.literal(String.valueOf(v)));
		toggle("calc_separators", () -> c.calcSeparators, v -> c.calcSeparators = v);
		toggle("calc_stacks", () -> c.calcStacks, v -> c.calcStacks = v);

		section("hud");
		toggle("pinned_hud", () -> c.pinnedHud, v -> c.pinnedHud = v);
		choice("hud_corner", ChatToolkitConfig.HudCorner.values(), () -> c.hudCorner, v -> {
			c.hudCorner = v;
			c.hudX = -1;
			c.hudY = -1;
		});
		toggle("timer_sound", () -> c.timerSound, v -> c.timerSound = v);

		contentHeight = 4;
		for (Row row : rows) {
			contentHeight += row.height;
		}
	}

	private void section(String key) {
		rows.add(new Section(Component.translatable("chattoolkit.config.section." + key)));
	}

	private void toggle(String key, BooleanSupplier get, Consumer<Boolean> set) {
		rows.add(new Toggle(key, get, set));
	}

	private <E extends Enum<E>> void choice(String key, E[] values, Supplier<E> get, Consumer<E> set) {
		rows.add(new Choice<>(key, values, get, set,
			v -> Component.translatable("chattoolkit.config.option." + key + "." + v.name().toLowerCase(Locale.ROOT))));
	}

	private void slider(String key, int min, int max, int step, IntSupplier get, IntConsumer set, IntFunction<Component> format) {
		rows.add(new Slider(key, min, max, step, get, set, format));
	}

	private void buttonRow(String key, Supplier<Component> label, Runnable action, @Nullable Supplier<Component> detail) {
		rows.add(new ButtonRow(key, label, action, detail));
	}

	private void color(String key, IntSupplier get, IntConsumer set) {
		rows.add(new ColorRow(key, get, set));
	}

	private static Component percent(int value) {
		return Component.literal(value + "%");
	}

	private static void click() {
		net.minecraft.client.Minecraft.getInstance().getSoundManager()
			.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.5f, 0.3f));
	}

	private abstract class Row {
		final int height;
		final @Nullable String key;

		Row(int height, @Nullable String key) {
			this.height = height;
			this.key = key;
		}

		Component label() {
			return Component.translatable("chattoolkit.config.option." + key);
		}

		@Nullable Component tooltip() {
			return key == null ? null : Component.translatable("chattoolkit.config.option." + key + ".tooltip");
		}

		abstract void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int mouseX, int mouseY, float alpha);

		boolean mouseClicked(double mouseX, double mouseY, int x, int y, int w, int button) {
			return false;
		}

		void drawLabel(GuiGraphicsExtractor g, Font font, int x, int y, float alpha) {
			g.text(font, label(), x + 10, y + (ROW_HEIGHT - 8) / 2, Draw.rgba(Draw.TEXT, alpha), false);
		}
	}

	private final class Section extends Row {
		private final Component title;

		Section(Component title) {
			super(SECTION_HEIGHT, null);
			this.title = title;
		}

		@Override
		void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int mouseX, int mouseY, float alpha) {
			int textY = y + 8;
			g.fill(x + 4, textY, x + 6, textY + 8, Draw.rgba(Draw.accent(), alpha));
			g.text(font, title.copy().withStyle(s -> s.withBold(true)), x + 10, textY, Draw.rgba(Draw.accent(), alpha), false);
			int lineX = x + 16 + font.width(title) + 4;
			g.fill(lineX, textY + 4, x + w - 6, textY + 5, Draw.rgba(Draw.WHITE, 0.08f * alpha));
		}
	}

	// A label with a button on the right that opens another screen
	private final class ButtonRow extends Row {
		private final Supplier<Component> buttonLabel;
		private final Runnable action;
		private final @Nullable Supplier<Component> detail;

		ButtonRow(String key, Supplier<Component> buttonLabel, Runnable action, @Nullable Supplier<Component> detail) {
			super(ROW_HEIGHT, key);
			this.buttonLabel = buttonLabel;
			this.action = action;
			this.detail = detail;
		}

		private int buttonWidth(Font font) {
			return font.width(buttonLabel.get()) + 14;
		}

		@Override
		void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int mouseX, int mouseY, float alpha) {
			drawLabel(g, font, x, y, alpha);
			if (detail != null) {
				Component d = detail.get();
				g.text(font, d, x + 16 + font.width(label()), y + (ROW_HEIGHT - 8) / 2, Draw.rgba(Draw.TEXT_FAINT, alpha), false);
			}
			int bw = buttonWidth(font);
			int bx = x + w - 10 - bw;
			int by = y + 4;
			boolean over = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + 14;
			g.fill(bx, by, bx + bw, by + 14, Draw.rgba(Draw.WHITE, (over ? 0.3f : 0.18f) * alpha));
			Component label = buttonLabel.get();
			g.text(font, label, bx + (bw - font.width(label)) / 2 + 1, by + 3, Draw.rgba(Draw.TEXT, alpha), false);
		}

		@Override
		boolean mouseClicked(double mouseX, double mouseY, int x, int y, int w, int button) {
			if (button != InputConstants.MOUSE_BUTTON_LEFT) {
				return false;
			}
			click();
			action.run();
			return true;
		}
	}

	private final class Toggle extends Row {
		private final BooleanSupplier get;
		private final Consumer<Boolean> set;
		private final Anim knob;

		Toggle(String key, BooleanSupplier get, Consumer<Boolean> set) {
			super(ROW_HEIGHT, key);
			this.get = get;
			this.set = set;
			this.knob = new Anim(get.getAsBoolean() ? 1 : 0, 16);
		}

		@Override
		void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int mouseX, int mouseY, float alpha) {
			drawLabel(g, font, x, y, alpha);
			float k = knob.target(get.getAsBoolean() ? 1 : 0).get();
			int trackX = x + w - 10 - 22;
			int trackY = y + 6;
			g.fill(trackX, trackY, trackX + 22, trackY + 10, Draw.rgba(Draw.lerpRgb(0x2E2E2E, Draw.accent(), k), (0.65f + 0.3f * k) * alpha));
			Draw.outline(g, trackX, trackY, trackX + 22, trackY + 10, Draw.rgba(Draw.WHITE, 0.12f * alpha));
			int knobX = trackX + 1 + Math.round(12 * Anim.easeInOutSine(k));
			g.fill(knobX, trackY + 1, knobX + 9, trackY + 9, Draw.rgba(Draw.lerpRgb(0xBDBDBD, Draw.WHITE, k), alpha));
		}

		@Override
		boolean mouseClicked(double mouseX, double mouseY, int x, int y, int w, int button) {
			if (button != InputConstants.MOUSE_BUTTON_LEFT) {
				return false;
			}
			set.accept(!get.getAsBoolean());
			click();
			return true;
		}
	}

	private final class Choice<E extends Enum<E>> extends Row {
		private final E[] values;
		private final Supplier<E> get;
		private final Consumer<E> set;
		private final Function<E, Component> name;
		private final Anim hover = new Anim(0, 20);

		Choice(String key, E[] values, Supplier<E> get, Consumer<E> set, Function<E, Component> name) {
			super(ROW_HEIGHT, key);
			this.values = values;
			this.get = get;
			this.set = set;
			this.name = name;
		}

		private int boxLeft(int x, int w) {
			return x + w - 10 - 104;
		}

		@Override
		void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int mouseX, int mouseY, float alpha) {
			drawLabel(g, font, x, y, alpha);
			int bx = boxLeft(x, w);
			int by = y + 4;
			boolean over = mouseX >= bx && mouseX < bx + 104 && mouseY >= by && mouseY < by + 14;
			float h = hover.target(over ? 1 : 0).get();
			g.fill(bx, by, bx + 104, by + 14, Draw.rgba(0x000000, 0.35f * alpha));
			g.fill(bx, by, bx + 104, by + 14, Draw.rgba(Draw.WHITE, 0.07f * h * alpha));
			Draw.outline(g, bx, by, bx + 104, by + 14, Draw.rgba(Draw.lerpRgb(0x555555, Draw.accent(), h), 0.6f * alpha));
			Component value = name.apply(get.get());
			int tw = font.width(value);
			g.text(font, value, bx + (104 - tw) / 2, by + 3, Draw.rgba(Draw.TEXT, alpha), false);
			g.text(font, "<", bx + 4, by + 3, Draw.rgba(Draw.TEXT_FAINT, alpha), false);
			g.text(font, ">", bx + 104 - 4 - font.width(">"), by + 3, Draw.rgba(Draw.TEXT_FAINT, alpha), false);
		}

		@Override
		boolean mouseClicked(double mouseX, double mouseY, int x, int y, int w, int button) {
			int dir = button == InputConstants.MOUSE_BUTTON_RIGHT ? -1 : 1;
			int index = get.get().ordinal();
			set.accept(values[Math.floorMod(index + dir, values.length)]);
			click();
			return true;
		}
	}

	private final class Slider extends Row {
		private static final int TRACK_WIDTH = 110;
		private final int min;
		private final int max;
		private final int step;
		private final IntSupplier get;
		private final IntConsumer set;
		private final IntFunction<Component> format;
		private final Anim position;
		private final Anim hover = new Anim(0, 20);

		Slider(String key, int min, int max, int step, IntSupplier get, IntConsumer set, IntFunction<Component> format) {
			super(ROW_HEIGHT, key);
			this.min = min;
			this.max = max;
			this.step = step;
			this.get = get;
			this.set = set;
			this.format = format;
			this.position = new Anim(fraction(), 22);
		}

		private float fraction() {
			return (get.getAsInt() - min) / (float) (max - min);
		}

		private int trackLeft(int x, int w) {
			return x + w - 10 - TRACK_WIDTH;
		}

		@Override
		void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int mouseX, int mouseY, float alpha) {
			drawLabel(g, font, x, y, alpha);
			int tx = trackLeft(x, w);
			int ty = y + ROW_HEIGHT / 2;
			boolean over = dragging == this || (mouseX >= tx - 4 && mouseX <= tx + TRACK_WIDTH + 4 && mouseY >= y + 3 && mouseY < y + ROW_HEIGHT - 3);
			float h = hover.target(over ? 1 : 0).get();
			float f = position.target(fraction()).get();
			int fill = Math.round(TRACK_WIDTH * f);
			g.fill(tx, ty - 1, tx + TRACK_WIDTH, ty + 1, Draw.rgba(Draw.WHITE, 0.16f * alpha));
			g.fill(tx, ty - 1, tx + fill, ty + 1, Draw.rgba(Draw.accent(), 0.9f * alpha));
			int kx = tx + fill;
			int knobHalf = 2 + Math.round(h);
			g.fill(kx - knobHalf, ty - 5, kx + knobHalf, ty + 5, Draw.rgba(Draw.lerpRgb(0xDDDDDD, Draw.WHITE, h), alpha));
			Component value = format.apply(get.getAsInt());
			g.text(font, value, tx - 8 - font.width(value), y + (ROW_HEIGHT - 8) / 2, Draw.rgba(Draw.lerpRgb(Draw.TEXT_DIM, Draw.TEXT, h), alpha), false);
		}

		@Override
		boolean mouseClicked(double mouseX, double mouseY, int x, int y, int w, int button) {
			int tx = trackLeft(x, w);
			if (button != InputConstants.MOUSE_BUTTON_LEFT || mouseX < tx - 6 || mouseX > tx + TRACK_WIDTH + 6) {
				return false;
			}
			dragging = this;
			setFromMouse(mouseX, x, w);
			return true;
		}

		void setFromMouse(double mouseX, int x, int w) {
			int tx = trackLeft(x, w);
			double f = Math.max(0, Math.min(1, (mouseX - tx) / TRACK_WIDTH));
			int value = min + (int) Math.round(f * (max - min) / step) * step;
			value = Math.max(min, Math.min(max, value));
			if (value != get.getAsInt()) {
				set.accept(value);
			}
		}
	}

	private final class ColorRow extends Row {
		private static final int SWATCH = 10;
		private final IntSupplier get;
		private final IntConsumer set;
		private final EditBox hex;
		private final Anim[] hover = new Anim[PRESETS.length];

		ColorRow(String key, IntSupplier get, IntConsumer set) {
			super(ROW_HEIGHT, key);
			this.get = get;
			this.set = set;
			this.hex = new EditBox(font, 0, 0, 52, 10, Component.translatable("chattoolkit.config.option." + key));
			this.hex.setBordered(false);
			this.hex.setMaxLength(7);
			this.hex.setValue(String.format("#%06X", get.getAsInt() & 0xFFFFFF));
			this.hex.setResponder(text -> {
				String digits = text.startsWith("#") ? text.substring(1) : text;
				if (digits.matches("[0-9a-fA-F]{6}")) {
					set.accept(Integer.parseInt(digits, 16));
				}
			});
			for (int i = 0; i < hover.length; i++) {
				hover[i] = new Anim(0, 20);
			}
		}

		private int fieldLeft(int x, int w) {
			return x + w - 10 - 56;
		}

		private int swatchLeft(int x, int w, int index) {
			return fieldLeft(x, w) - 8 - (PRESETS.length - index) * (SWATCH + 3);
		}

		@Override
		void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int mouseX, int mouseY, float alpha) {
			drawLabel(g, font, x, y, alpha);
			int current = get.getAsInt() & 0xFFFFFF;
			int sy = y + (ROW_HEIGHT - SWATCH) / 2;
			for (int i = 0; i < PRESETS.length; i++) {
				int sx = swatchLeft(x, w, i);
				boolean over = mouseX >= sx && mouseX < sx + SWATCH && mouseY >= sy && mouseY < sy + SWATCH;
				float h = hover[i].target(over ? 1 : 0).get();
				int grow = Math.round(h);
				g.fill(sx - grow, sy - grow, sx + SWATCH + grow, sy + SWATCH + grow, Draw.rgba(PRESETS[i], alpha));
				if (PRESETS[i] == current) {
					Draw.outline(g, sx - 2, sy - 2, sx + SWATCH + 2, sy + SWATCH + 2, Draw.rgba(Draw.WHITE, 0.9f * alpha));
				}
			}
			int fx = fieldLeft(x, w);
			int fy = y + 4;
			g.fill(fx, fy, fx + 56, fy + 14, Draw.rgba(0x000000, 0.35f * alpha));
			Draw.outline(g, fx, fy, fx + 56, fy + 14, Draw.rgba(hex.isFocused() ? Draw.accent() : 0x555555, 0.7f * alpha));
			g.fill(fx + 3, fy + 3, fx + 11, fy + 11, Draw.rgba(current, alpha));
			if (!hex.isFocused() && !hex.getValue().equalsIgnoreCase(String.format("#%06X", current))) {
				hex.setValue(String.format("#%06X", current));
			}
			hex.setX(fx + 14);
			hex.setY(fy + 3);
			hex.setTextColor(Draw.rgba(Draw.TEXT, alpha));
			hex.extractRenderState(g, mouseX, mouseY, 0);
		}

		@Override
		boolean mouseClicked(double mouseX, double mouseY, int x, int y, int w, int button) {
			int sy = y + (ROW_HEIGHT - SWATCH) / 2;
			for (int i = 0; i < PRESETS.length; i++) {
				int sx = swatchLeft(x, w, i);
				if (mouseX >= sx - 1 && mouseX < sx + SWATCH + 1 && mouseY >= sy - 1 && mouseY < sy + SWATCH + 1) {
					set.accept(PRESETS[i]);
					hex.setValue(String.format("#%06X", PRESETS[i]));
					click();
					return true;
				}
			}
			int fx = fieldLeft(x, w);
			if (mouseX >= fx && mouseX < fx + 56) {
				focusField(hex);
				hex.moveCursorToEnd(false);
				return true;
			}
			return false;
		}
	}

	// layout helpers

	private void setScroll(float value, boolean instant) {
		float max = Math.max(0, contentHeight - (listBottom - listTop));
		scrollTarget = Math.max(0, Math.min(max, value));
		if (instant) {
			scroll.snap(scrollTarget);
		} else {
			scroll.target(scrollTarget);
		}
	}

	private record Placed(Row row, int y) {
	}

	private List<Placed> placedRows() {
		List<Placed> out = new ArrayList<>(rows.size());
		int y = listTop + 2 - Math.round(scroll.get());
		for (Row row : rows) {
			out.add(new Placed(row, y));
			y += row.height;
		}
		return out;
	}

	private void focusField(@Nullable EditBox field) {
		if (focusedField == field) {
			return;
		}
		if (focusedField != null) {
			focusedField.setFocused(false);
		}
		focusedField = field;
		if (field != null) {
			field.setFocused(true);
		}
	}

	private boolean overDone(double mouseX, double mouseY) {
		int bw = 100;
		int bx = panelX + panelWidth - bw;
		int by = this.height - 24;
		return mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + 16;
	}

	private boolean overReset(double mouseX, double mouseY) {
		int bw = 100;
		int bx = panelX;
		int by = this.height - 24;
		return mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + 16;
	}

	private boolean resetArmed() {
		return resetArmedAt > 0 && System.currentTimeMillis() - resetArmedAt < 3000;
	}

	// rendering

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		float alpha = Anim.easeOutCubic(Anim.progress(openedAt, 220));
		graphics.pose().pushMatrix();
		graphics.pose().translate(0, (1.0f - alpha) * 6.0f);

		Font font = this.font;
		int x = panelX;
		int w = panelWidth;

		// header
		Component title = Component.literal("Chat Toolkit").withStyle(s -> s.withBold(true));
		graphics.text(font, title, x + 2, HEADER_TOP, Draw.rgba(Draw.TEXT, alpha), true);
		graphics.text(font, "v" + ChatToolkit.version(), x + 6 + font.width(title), HEADER_TOP, Draw.rgba(Draw.TEXT_FAINT, alpha), false);
		Component subtitle = Component.translatable("chattoolkit.config.subtitle");
		graphics.text(font, subtitle, x + w - font.width(subtitle) - 2, HEADER_TOP, Draw.rgba(Draw.TEXT_DIM, alpha), false);
		graphics.fill(x, HEADER_TOP + 12, x + w, HEADER_TOP + 13, Draw.rgba(Draw.accent(), 0.7f * alpha));

		renderPreview(graphics, font, x, HEADER_TOP + 20, w, alpha);

		// list
		graphics.fill(x, listTop, x + w, listBottom, Draw.rgba(0x000000, Math.max(0.2f, Draw.panelAlpha() * 0.8f) * alpha));
		graphics.enableScissor(x, listTop, x + w, listBottom);
		Row hoveredRow = null;
		for (Placed placed : placedRows()) {
			if (placed.y + placed.row.height < listTop || placed.y > listBottom) {
				continue;
			}
			boolean hovered = !(placed.row instanceof Section) && mouseX >= x && mouseX < x + w && mouseY >= placed.y && mouseY < placed.y + placed.row.height
				&& mouseY >= listTop && mouseY < listBottom;
			if (hovered) {
				// tooltips only over the label half, so they never cover the control you're using
				if (mouseX < x + w / 2) {
					hoveredRow = placed.row;
				}
				graphics.fill(x, placed.y, x + w, placed.y + placed.row.height, Draw.rgba(Draw.WHITE, 0.045f * alpha));
			}
			placed.row.render(graphics, font, x, placed.y, w, mouseX, mouseY, alpha);
		}
		graphics.disableScissor();

		// scrollbar
		int viewHeight = listBottom - listTop;
		if (contentHeight > viewHeight) {
			int barHeight = Math.max(20, viewHeight * viewHeight / contentHeight);
			int barY = listTop + Math.round((viewHeight - barHeight) * (scroll.get() / Math.max(1, contentHeight - viewHeight)));
			graphics.fill(x + w - 2, barY, x + w, barY + barHeight, Draw.rgba(Draw.accent(), 0.7f * alpha));
		}

		// footer buttons
		renderButton(graphics, font, panelX, this.height - 24, 100,
			Component.translatable(resetArmed() ? "chattoolkit.config.reset.confirm" : "chattoolkit.config.reset"),
			resetHover.target(overReset(mouseX, mouseY) ? 1 : 0).get(), resetArmed() ? Draw.ERROR : 0x888888, alpha);
		renderButton(graphics, font, panelX + panelWidth - 100, this.height - 24, 100, Component.translatable("chattoolkit.config.done"),
			doneHover.target(overDone(mouseX, mouseY) ? 1 : 0).get(), Draw.accent(), alpha);

		graphics.pose().popMatrix();

		// delayed tooltip for the hovered option
		if (hoveredRow != tooltipRow) {
			tooltipRow = hoveredRow;
			tooltipSince = System.currentTimeMillis();
		}
		if (tooltipRow != null && dragging == null && System.currentTimeMillis() - tooltipSince > TOOLTIP_DELAY) {
			Component tip = tooltipRow.tooltip();
			if (tip != null) {
				List<FormattedCharSequence> lines = font.split(tip, 220);
				graphics.setTooltipForNextFrame(font, lines, mouseX, mouseY);
			}
		}
	}

	private void renderButton(GuiGraphicsExtractor g, Font font, int x, int y, int w, Component label, float hover, int color, float alpha) {
		g.fill(x, y, x + w, y + 16, Draw.rgba(0x000000, Math.max(0.3f, Draw.panelAlpha()) * alpha));
		g.fill(x, y, x + w, y + 16, Draw.rgba(color, 0.18f * hover * alpha));
		g.fill(x, y + 15, x + Math.round(w * (0.25f + 0.75f * Anim.easeOutCubic(hover))), y + 16, Draw.rgba(color, 0.9f * alpha));
		int tw = font.width(label);
		g.text(font, label, x + (w - tw) / 2, y + 4, Draw.rgba(Draw.lerpRgb(0xDDDDDD, Draw.TEXT, hover), alpha), false);
	}

	// A fake chat line and search bar that use the current colours, so changes are visible right away
	private void renderPreview(GuiGraphicsExtractor g, Font font, int x, int y, int w, float alpha) {
		float chatBg = (float) this.minecraft.options.textBackgroundOpacity().get().doubleValue();
		g.fill(x, y, x + w, y + 11, Draw.rgba(0x000000, Math.max(0.25f, chatBg) * alpha));
		String before = "<Steve> found ";
		String match = "diamonds";
		String after = " at 120 64 -300!";
		int tx = x + 4;
		int bx = tx + font.width(before);
		int mw = font.width(match);
		g.fill(bx - 1, y + 1, bx + mw, y + 10, Draw.rgba(Draw.accent(), ChatToolkitConfig.get().highlightOpacity / 100.0f * alpha));
		g.text(font, before, tx, y + 2, Draw.rgba(Draw.TEXT, alpha), true);
		g.text(font, match, bx, y + 2, Draw.rgba(Draw.TEXT, alpha), true);
		g.text(font, after, bx + mw, y + 2, Draw.rgba(Draw.TEXT, alpha), true);

		int by = y + 15;
		int bg = Draw.chatBackground();
		g.fill(x, by, x + w, by + 12, Draw.argb(Math.round((bg >>> 24) * alpha), bg));
		g.text(font, match, x + 4, by + 2, Draw.rgba(0xE0E0E0, alpha), false);
		ChatToolkitConfig c = ChatToolkitConfig.get();
		String[] labels = {"Aa", "W", ".*"};
		boolean[] enabled = {c.caseSensitive, c.wholeWords, c.regex};
		int left = x + w - 42;
		Component counter = Component.translatable("chattoolkit.search.many", 3);
		g.text(font, counter, left - 4 - font.width(counter), by + 2, Draw.rgba(0x808080, alpha), false);
		for (int i = 0; i < 3; i++) {
			int cx = left + i * 14;
			if (enabled[i]) {
				g.fill(cx, by, cx + 14, by + 12, Draw.rgba(0xFFFFFF, 0.18f * alpha));
			}
			g.text(font, labels[i], cx + (15 - font.width(labels[i])) / 2, by + 2, Draw.rgba(enabled[i] ? 0xFFFFFF : 0x707070, alpha), false);
		}
	}

	// input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x();
		double mouseY = event.y();
		int button = event.button();

		if (overDone(mouseX, mouseY) && button == InputConstants.MOUSE_BUTTON_LEFT) {
			click();
			onClose();
			return true;
		}
		if (overReset(mouseX, mouseY) && button == InputConstants.MOUSE_BUTTON_LEFT) {
			click();
			if (resetArmed()) {
				resetArmedAt = -1;
				ChatToolkitConfig.reset();
				SearchState.rebuild();
				focusField(null);
				buildRows();
				setScroll(scrollTarget, true);
			} else {
				resetArmedAt = System.currentTimeMillis();
			}
			return true;
		}

		if (mouseX >= panelX && mouseX < panelX + panelWidth && mouseY >= listTop && mouseY < listBottom) {
			for (Placed placed : placedRows()) {
				if (mouseY >= placed.y && mouseY < placed.y + placed.row.height) {
					EditBox before = focusedField;
					placed.row.mouseClicked(mouseX, mouseY, panelX, placed.y, panelWidth, button);
					if (focusedField != null && focusedField == before && !focusedField.isMouseOver(mouseX, mouseY)) {
						focusField(null);
					} else if (focusedField != null) {
						focusedField.mouseClicked(event, doubleClick);
					}
					return true;
				}
			}
		}
		focusField(null);
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging != null) {
			dragging.setFromMouse(event.x(), panelX, panelWidth);
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null) {
			dragging = null;
			click();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		setScroll(scrollTarget - (float) scrollY * 26, false);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (focusedField != null) {
			if (event.isEscape() || event.isConfirmation()) {
				focusField(null);
				return true;
			}
			focusedField.keyPressed(event);
			return true;
		}
		if (event.isDown()) {
			setScroll(scrollTarget + ROW_HEIGHT, false);
			return true;
		}
		if (event.isUp()) {
			setScroll(scrollTarget - ROW_HEIGHT, false);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (focusedField != null) {
			return focusedField.charTyped(event);
		}
		return super.charTyped(event);
	}

	@Override
	public void onClose() {
		focusField(null);
		ChatToolkitConfig config = ChatToolkitConfig.get();
		config.sanitize();
		ChatToolkitConfig.save();
		SearchState.rebuild();
		if (config.overrideClientCommands != overrideAtOpen && ToolkitCommands.clientCommandsInstalled()) {
			try {
				ClientCommands.refreshCommandCompletions();
			} catch (IllegalStateException ignored) {
				// not connected; commands are rebuilt when you join a world anyway
			}
		}
		this.minecraft.gui.setScreen(parent);
	}
}
