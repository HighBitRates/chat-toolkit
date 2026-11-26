package dev.highbitrate.chattoolkit.tabs;

import com.mojang.blaze3d.Blaze3D;
import com.mojang.blaze3d.platform.InputConstants;
import dev.highbitrate.chattoolkit.chat.ChatHelper;
import dev.highbitrate.chattoolkit.text.TextFormatter;
import dev.highbitrate.chattoolkit.ui.Anim;
import dev.highbitrate.chattoolkit.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

// Settings > Edit tabs. Left side lists the tabs of the active preset, right side edits the selected one.
// "Presets..." opens a small window on top for switching, sharing and tying presets to servers.
public final class TabEditorScreen extends Screen {
	private static final int ROW = 15;
	private static final int[] COLORS = {0xFFFFFF, 0xAAAAAA, 0x55FFFF, 0x55FF55, 0xFF55FF, 0xFFC94D, 0xFF5555};
	private static final int FIELD_BG = 0xA0000000;

	private final @Nullable Screen parent;
	private final long openedAt = System.currentTimeMillis();
	private int selected;

	// panel layout, worked out in init()
	private int px0, py0, px1, py1;
	private int listX, rightX, valueX;

	private record Button(int x, int y, int w, int h, Component label, Runnable action, @Nullable Component tooltip, int style) {
		static final int NORMAL = 0, STRONG = 1, DANGER = 2, TEXT = 3, SELECTED = 4;

		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private final List<Button> buttons = new ArrayList<>();

	// dragging a tab in the list to reorder it
	private int pressedRow = -1;
	private double pressedY;
	private boolean reordering;

	// presets window
	private boolean presetsOpen;
	private @Nullable TabPreset presetSelected;
	private @Nullable TabPreset renaming;

	private @Nullable Component status;
	private boolean statusError;
	private long statusAt;

	private TabEditorScreen(@Nullable Screen parent) {
		super(Component.translatable("chattoolkit.tabs.title"));
		this.parent = parent;
	}

	public static TabEditorScreen open(@Nullable Screen parent) {
		TabEditorScreen screen = new TabEditorScreen(parent);
		ChatTab active = TabManager.activeTab();
		screen.selected = active == null ? 0 : Math.max(0, TabManager.tabs().indexOf(active));
		return screen;
	}

	public static TabEditorScreen forTab(@Nullable Screen parent, ChatTab tab) {
		TabEditorScreen screen = new TabEditorScreen(parent);
		screen.selected = Math.max(0, TabManager.tabs().indexOf(tab));
		return screen;
	}

	public static TabEditorScreen forNewTab(@Nullable Screen parent) {
		TabEditorScreen screen = new TabEditorScreen(parent);
		screen.selected = screen.addTab();
		return screen;
	}

	private List<ChatTab> tabs() {
		return TabManager.tabs();
	}

	private @Nullable ChatTab tab() {
		List<ChatTab> tabs = tabs();
		if (tabs.isEmpty()) {
			return null;
		}
		selected = Math.max(0, Math.min(selected, tabs.size() - 1));
		return tabs.get(selected);
	}

	private int addTab() {
		ChatTab tab = new ChatTab(Component.translatable("chattoolkit.tabs.new_tab_name").getString(), 0xFFFFFF, false,
			new TabRule(TabRule.Kind.CONTAINS, ""));
		tabs().add(tab);
		TabManager.changed();
		return tabs().size() - 1;
	}

	@Override
	protected void init() {
		int w = Math.min(505, width - 16);
		int h = Math.min(282, height - 12);
		px0 = (width - w) / 2;
		py0 = (height - h) / 2;
		px1 = px0 + w;
		py1 = py0 + h;
		listX = px0 + 8;
		rightX = px0 + 134;
		valueX = rightX + 100;
		rebuild();
	}

	private void rebuild() {
		clearWidgets();
		buttons.clear();
		if (presetsOpen) {
			buildPresets();
		} else {
			buildEditor();
		}
	}

	private EditBox field(int x, int y, int w, String value, Component hint, Consumer<String> onChange) {
		EditBox box = new EditBox(font, x + 3, y + 2, w - 6, 10, hint);
		box.setBordered(false);
		box.setMaxLength(200);
		box.setValue(value);
		box.setHint(hint.copy().withColor(0x6E6E6E));
		box.setResponder(onChange);
		addRenderableWidget(box);
		return box;
	}

	private void button(int x, int y, Component label, int style, @Nullable Component tooltip, Runnable action) {
		int w = font.width(label) + 12;
		buttons.add(new Button(x, y, w, 12, label, action, tooltip, style));
	}

	private int buttonWidth(Component label) {
		return font.width(label) + 12;
	}

	// --- editor ---

	private void buildEditor() {
		// header
		Component help = Component.translatable("chattoolkit.tabs.help_button");
		button(px0 + 8 + font.width(title) + 10, py0 + 4, help, Button.NORMAL, null,
			() -> minecraft.gui.setScreen(new HowToUseScreen(this)));
		Component presetsLabel = Component.translatable("chattoolkit.tabs.presets_button");
		Component save = Component.translatable("chattoolkit.tabs.save");
		int x = px1 - 8 - buttonWidth(presetsLabel);
		button(x, py0 + 4, presetsLabel, Button.NORMAL, null, () -> {
			presetsOpen = true;
			presetSelected = TabStore.active();
			rebuild();
		});
		x -= 3 + buttonWidth(save);
		button(x, py0 + 4, save, Button.STRONG, null, () -> {
			TabStore.save();
			setStatus(Component.translatable("chattoolkit.tabs.saved"), false);
		});

		// tab list
		int y = listTop() + tabs().size() * ROW + 2;
		button(listX + 12, y, Component.translatable("chattoolkit.tabs.new"), Button.TEXT, null, () -> {
			selected = addTab();
			rebuild();
		});

		ChatTab tab = tab();
		if (tab == null) {
			return;
		}
		y = py0 + 32;
		EditBox name = field(valueX, y, 140, tab.name, Component.translatable("chattoolkit.tabs.name_hint"), v -> {
			tab.name = v.isBlank() ? "Tab" : v;
		});
		name.setMaxLength(24);
		y += 34;
		for (TabRule rule : tab.rules) {
			Component kind = Component.translatable("chattoolkit.tabs.rule." + rule.kind.key());
			buttons.add(new Button(rightX, y, 96, 12, kind, () -> {
				rule.kind = rule.kind.next();
				TabManager.changed();
				rebuild();
			}, Component.translatable("chattoolkit.tabs.rule.cycle"), Button.NORMAL));
			if (!rule.isCatchAll()) {
				field(rightX + 100, y, 110, rule.text, Component.translatable("chattoolkit.tabs.rule_hint"), v -> {
					rule.text = v;
					TabManager.changed();
				});
			}
			buttons.add(new Button(px1 - 20, y, 12, 12, Component.literal("x"), () -> {
				tab.rules.remove(rule);
				TabManager.changed();
				rebuild();
			}, Component.translatable("chattoolkit.tabs.rule.remove"), Button.TEXT));
			y += 15;
		}
		button(rightX - 4, y, Component.translatable("chattoolkit.tabs.add_rule"), Button.TEXT, null, () -> {
			tab.rules.add(new TabRule(TabRule.Kind.CONTAINS, ""));
			TabManager.changed();
			rebuild();
		});
		y += 24;
		// red dot toggle row
		buttons.add(new Button(px1 - 30, y, 20, 10, Component.empty(), () -> {
			tab.dot = !tab.dot;
		}, null, Button.TEXT));
		y += 15;
		field(valueX, y, 140, tab.sendAs, Component.translatable("chattoolkit.tabs.send_as_hint"), v -> tab.sendAs = v.trim());
		y += 16;
		for (int i = 0; i < COLORS.length; i++) {
			int color = COLORS[i];
			buttons.add(new Button(valueX + i * 14, y, 11, 11, Component.empty(), () -> tab.color = color, null, Button.TEXT));
		}
		y += 16;
		Component docked = Component.translatable("chattoolkit.tabs.docked");
		Component popped = Component.translatable("chattoolkit.tabs.popped");
		int dw = buttonWidth(docked);
		buttons.add(new Button(valueX, y, dw, 12, docked, () -> {
			if (tab.popped) {
				TabManager.dock(tab, tabs().indexOf(tab));
			}
		}, null, tab.popped ? Button.NORMAL : Button.SELECTED));
		buttons.add(new Button(valueX + dw + 1, y, buttonWidth(popped), 12, popped, () -> {
			if (!tab.popped) {
				TabManager.popOut(tab);
			}
		}, null, tab.popped ? Button.SELECTED : Button.NORMAL));

		// footer
		Component delete = Component.translatable("chattoolkit.tabs.delete");
		button(px0 + 8, py1 - 17, delete, Button.DANGER, null, () -> {
			if (tabs().size() <= 1) {
				setStatus(Component.translatable("chattoolkit.tabs.last_tab"), true);
				return;
			}
			tabs().remove(tab);
			TabManager.changed();
			selected = Math.max(0, selected - 1);
			rebuild();
		});
		Component done = Component.translatable("gui.done");
		buttons.add(new Button(px1 - 68, py1 - 17, 60, 12, done, this::onClose, null, Button.STRONG));
	}

	// --- presets window ---

	private int pw0, ph0, pw1, ph1;

	private void buildPresets() {
		int w = Math.min(300, width - 20);
		int h = 207;
		pw0 = (width - w) / 2;
		ph0 = (height - h) / 2;
		pw1 = pw0 + w;
		ph1 = ph0 + h;
		if (importing) {
			buildImport();
			return;
		}
		List<TabPreset> presets = TabStore.presets();
		if (presetSelected == null || !presets.contains(presetSelected)) {
			presetSelected = TabStore.active();
		}
		TabPreset sel = presetSelected;

		int y = ph0 + 28;
		for (TabPreset preset : presets) {
			if (preset == renaming) {
				EditBox box = field(pw0 + 18, y - 2, 150, preset.name, Component.translatable("chattoolkit.tabs.preset_name"), v -> {
				});
				box.setMaxLength(32);
				setInitialFocus(box);
				box.setResponder(v -> renameText = v);
				renameText = preset.name;
				buttons.add(new Button(pw0 + 172, y - 2, 30, 12, Component.translatable("gui.ok"), () -> {
					TabStore.rename(preset, renameText);
					renaming = null;
					rebuild();
				}, null, Button.STRONG));
			} else {
				buttons.add(new Button(pw0 + 6, y - 3, w - 12, 24, Component.empty(), () -> {
					presetSelected = preset;
					rebuild();
				}, null, Button.TEXT));
			}
			y += 28;
		}
		button(pw0 + 14, y - 4, Component.translatable("chattoolkit.tabs.new_preset"), Button.TEXT, null, () -> {
			presetSelected = TabStore.add(TabPreset.blank(Component.translatable("chattoolkit.tabs.new_preset_name").getString()));
			rebuild();
		});
		y += 14;
		presetsDividerY = y;
		y += 6;
		field(pw0 + 80, y, 110, sel.server, Component.translatable("chattoolkit.tabs.server_hint"), v -> {
			sel.server = v.trim();
			TabStore.save();
		});
		button(pw0 + 194, y, Component.translatable("chattoolkit.tabs.this_server"), Button.NORMAL, null, () -> {
			ServerData server = Minecraft.getInstance().getCurrentServer();
			if (server == null) {
				setStatus(Component.translatable("chattoolkit.tabs.not_on_server"), true);
				return;
			}
			sel.server = server.ip;
			TabStore.save();
			rebuild();
		});
		y += 18;
		int x = pw0 + 10;
		Component[] labels = {
			Component.translatable("chattoolkit.tabs.load"), Component.translatable("chattoolkit.tabs.duplicate"),
			Component.translatable("chattoolkit.tabs.rename"), Component.translatable("chattoolkit.tabs.delete_preset")
		};
		Runnable[] actions = {
			() -> {
				TabStore.setActive(sel);
				selected = 0;
				setStatus(Component.translatable("chattoolkit.tabs.loaded", sel.name), false);
				rebuild();
			},
			() -> {
				presetSelected = TabStore.add(sel.copy(sel.name + " copy"));
				rebuild();
			},
			() -> {
				renaming = sel;
				rebuild();
			},
			() -> {
				if (!TabStore.remove(sel)) {
					setStatus(Component.translatable("chattoolkit.tabs.last_preset"), true);
					return;
				}
				presetSelected = TabStore.active();
				selected = 0;
				rebuild();
			}
		};
		int[] styles = {Button.STRONG, Button.NORMAL, Button.NORMAL, Button.DANGER};
		for (int i = 0; i < labels.length; i++) {
			buttons.add(new Button(x, y, buttonWidth(labels[i]), 12, labels[i], actions[i], null, styles[i]));
			x += buttonWidth(labels[i]) + 3;
		}
		y += 17;
		x = pw0 + 10;
		Component copy = Component.translatable("chattoolkit.tabs.copy_preset");
		button(x, y, copy, Button.NORMAL, null, () -> {
			ChatHelper.copyToClipboard(TabStore.toJson(sel));
			setStatus(Component.translatable("chattoolkit.tabs.copied"), false);
		});
		x += buttonWidth(copy) + 3;
		button(x, y, Component.translatable("chattoolkit.tabs.paste_preset"), Button.NORMAL, null, () -> {
			TabPreset pasted = TabStore.fromJson(Minecraft.getInstance().keyboardHandler.getClipboard());
			if (pasted == null) {
				setStatus(Component.translatable("chattoolkit.tabs.paste_failed"), true);
				return;
			}
			presetSelected = TabStore.add(pasted);
			setStatus(Component.translatable("chattoolkit.tabs.pasted", pasted.name), false);
			rebuild();
		});
		y += 17;
		x = pw0 + 10;
		Component export = Component.translatable("chattoolkit.tabs.export");
		button(x, y, export, Button.NORMAL, Component.translatable("chattoolkit.tabs.export.tooltip"), () -> {
			try {
				Path file = TabStore.exportToFile(sel);
				setStatus(Component.translatable("chattoolkit.tabs.exported", file.getFileName().toString()), false);
			} catch (IOException e) {
				setStatus(Component.translatable("chattoolkit.tabs.export_failed"), true);
			}
		});
		x += buttonWidth(export) + 3;
		Component importLabel = Component.translatable("chattoolkit.tabs.import");
		button(x, y, importLabel, Button.NORMAL, Component.translatable("chattoolkit.tabs.import.tooltip"), () -> {
			importing = true;
			rebuild();
		});
		x += buttonWidth(importLabel) + 3;
		button(x, y, Component.translatable("chattoolkit.tabs.open_folder"), Button.NORMAL, null, this::openFolder);
		Component close = Component.translatable("chattoolkit.tabs.close");
		buttons.add(new Button(pw1 - 58, ph1 - 17, 50, 12, close, () -> {
			presetsOpen = false;
			renaming = null;
			rebuild();
		}, null, Button.STRONG));
	}

	private boolean importing;
	private int importFilesShown;

	// Lists the .json files in the presets folder to pick from
	private void buildImport() {
		List<Path> files = TabStore.presetFiles();
		int y = ph0 + 30;
		int shown = 0;
		for (Path file : files) {
			if (shown++ >= 7) {
				break;
			}
			String name = file.getFileName().toString();
			buttons.add(new Button(pw0 + 6, y - 3, pw1 - pw0 - 12, 13, Component.literal(name), () -> importFrom(file), null, Button.TEXT));
			y += 14;
		}
		importFilesShown = Math.min(shown, 7);
		Component back = Component.translatable("chattoolkit.tabs.back");
		button(pw0 + 10, ph1 - 17, back, Button.NORMAL, null, () -> {
			importing = false;
			rebuild();
		});
		button(pw0 + 14 + buttonWidth(back), ph1 - 17, Component.translatable("chattoolkit.tabs.open_folder"), Button.NORMAL, null, this::openFolder);
	}

	private void importFrom(Path file) {
		TabPreset preset = TabStore.importFile(file);
		if (preset == null) {
			setStatus(Component.translatable("chattoolkit.tabs.import_failed", file.getFileName().toString()), true);
			return;
		}
		presetSelected = TabStore.add(preset);
		importing = false;
		setStatus(Component.translatable("chattoolkit.tabs.pasted", preset.name), false);
		rebuild();
	}

	private void openFolder() {
		try {
			Files.createDirectories(TabStore.presetsFolder());
		} catch (IOException ignored) {
		}
		Blaze3D.openPath(TabStore.presetsFolder());
	}

	// Dropping .json files onto the game window while this screen is open imports them
	@Override
	public void onFilesDrop(List<Path> files) {
		int added = 0;
		TabPreset last = null;
		for (Path file : files) {
			if (!file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")) {
				continue;
			}
			TabPreset preset = TabStore.importFile(file);
			if (preset != null) {
				last = TabStore.add(preset);
				added++;
			}
		}
		if (last == null) {
			setStatus(Component.translatable("chattoolkit.tabs.drop_failed"), true);
			return;
		}
		presetsOpen = true;
		importing = false;
		presetSelected = last;
		setStatus(added == 1 ? Component.translatable("chattoolkit.tabs.pasted", last.name)
			: Component.translatable("chattoolkit.tabs.imported_many", added), false);
		rebuild();
	}

	private String renameText = "";
	private int presetsDividerY;

	private void setStatus(Component text, boolean error) {
		status = text;
		statusError = error;
		statusAt = System.currentTimeMillis();
	}

	// --- input ---

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			for (Button b : List.copyOf(buttons)) {
				if (b.contains(mx, my)) {
					click();
					setFocused(null);
					b.action().run();
					return true;
				}
			}
			if (!presetsOpen) {
				int row = rowAt(mx, my);
				if (row >= 0) {
					pressedRow = row;
					pressedY = my;
					reordering = false;
					if (row != selected) {
						selected = row;
						click();
						rebuild();
					}
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (pressedRow >= 0) {
			if (Math.abs(event.y() - pressedY) > 3) {
				reordering = true;
			}
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (pressedRow >= 0) {
			if (reordering) {
				int target = dropIndex(event.y());
				List<ChatTab> tabs = tabs();
				if (target != pressedRow && pressedRow < tabs.size()) {
					ChatTab moved = tabs.remove(pressedRow);
					target = Math.max(0, Math.min(target > pressedRow ? target - 1 : target, tabs.size()));
					tabs.add(target, moved);
					selected = target;
					TabManager.changed();
					rebuild();
				}
			}
			pressedRow = -1;
			reordering = false;
			return true;
		}
		return super.mouseReleased(event);
	}

	private int listTop() {
		return py0 + 50;
	}

	private int rowAt(double mx, double my) {
		if (mx < listX - 2 || mx >= listX + 112) {
			return -1;
		}
		int row = (int) Math.floor((my - listTop() + 2) / ROW);
		return row >= 0 && row < tabs().size() ? row : -1;
	}

	private int dropIndex(double my) {
		int index = (int) Math.round((my - listTop()) / ROW);
		return Math.max(0, Math.min(index, tabs().size()));
	}

	private static void click() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.6f, 0.35f));
	}

	@Override
	public void onClose() {
		if (importing) {
			importing = false;
			rebuild();
			return;
		}
		if (presetsOpen) {
			presetsOpen = false;
			renaming = null;
			rebuild();
			return;
		}
		TabStore.save();
		TabManager.changed();
		minecraft.gui.setScreen(parent);
	}

	// --- drawing ---

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		float fade = Anim.easeOutCubic(Anim.progress(openedAt, 200));
		g.fill(px0, py0, px1, py1, Draw.rgba(0x000000, 0.55f + 0.15f * fade));
		g.text(font, title, px0 + 8, py0 + 7, 0xFFFFFFFF, true);
		g.fill(px0, py0 + 20, px1, py0 + 21, Draw.rgba(0xFFFFFF, 0.16f));

		drawEditorBody(g, mouseX, mouseY);

		if (presetsOpen) {
			g.nextStratum();
			g.fill(0, 0, width, height, Draw.rgba(0x000000, 0.45f));
			drawPresets(g, mouseX, mouseY);
		}
		drawButtons(g, mouseX, mouseY);
		// edit boxes go on top of our backgrounds
		super.extractRenderState(g, mouseX, mouseY, partialTick);

		if (status != null) {
			float t = Anim.progress(statusAt, 2600);
			if (t >= 1) {
				status = null;
			} else {
				float a = t > 0.8f ? (1 - t) / 0.2f : 1;
				int sx = presetsOpen ? pw0 + 10 : px0 + 8 + buttonWidth(Component.translatable("chattoolkit.tabs.delete")) + 8;
				int sy = presetsOpen ? ph1 - 31 : py1 - 15;
				g.text(font, status, sx, sy, Draw.rgba(statusError ? 0xFF8080 : 0x9BE39B, a), false);
			}
		}
	}

	private void drawButtons(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		for (Button b : buttons) {
			boolean over = b.contains(mouseX, mouseY);
			switch (b.style()) {
				case Button.TEXT -> {
					if (!b.label().getString().isEmpty()) {
						g.text(font, b.label(), b.x() + 4, b.y() + 2, over ? 0xFFFFFFFF : 0xFFBEBEBE, true);
					}
				}
				case Button.SELECTED -> {
					g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), Draw.rgba(0xFFFFFF, 0.24f));
					g.text(font, b.label(), b.x() + (b.w() - font.width(b.label())) / 2 + 1, b.y() + 2, 0xFFFFFFFF, true);
				}
				default -> {
					int bg = switch (b.style()) {
						case Button.STRONG -> Draw.rgba(0xFFFFFF, over ? 0.36f : 0.27f);
						case Button.DANGER -> Draw.rgba(0xFF5555, over ? 0.36f : 0.24f);
						default -> Draw.rgba(0xFFFFFF, over ? 0.22f : 0.13f);
					};
					g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), bg);
					int color = b.style() == Button.DANGER ? 0xFFFFAAAA : b.style() == Button.STRONG || over ? 0xFFFFFFFF : 0xFFD2D2D2;
					g.text(font, b.label(), b.x() + (b.w() - font.width(b.label())) / 2 + 1, b.y() + 2, color, true);
				}
			}
			if (over && b.tooltip() != null) {
				g.setTooltipForNextFrame(font, b.tooltip(), mouseX, mouseY);
			}
		}
	}

	private void drawField(GuiGraphicsExtractor g, int x, int y, int w) {
		g.fill(x, y, x + w, y + 12, FIELD_BG);
		Draw.outline(g, x, y, x + w, y + 12, Draw.rgba(0xFFFFFF, 0.2f));
	}

	private void drawEditorBody(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		// preset label in the header
		Component presetsLabel = Component.translatable("chattoolkit.tabs.presets_button");
		Component save = Component.translatable("chattoolkit.tabs.save");
		int right = px1 - 8 - buttonWidth(presetsLabel) - 3 - buttonWidth(save) - 6;
		Component current = Component.translatable("chattoolkit.tabs.preset_label", TabStore.active().name);
		g.text(font, current, right - font.width(current), py0 + 6, 0xFFAAAAAA, false);

		// tab list
		g.text(font, Component.translatable("chattoolkit.tabs.list"), listX, py0 + 36, 0xFF969696, false);
		List<ChatTab> tabs = tabs();
		int y = listTop();
		for (int i = 0; i < tabs.size(); i++) {
			ChatTab tab = tabs.get(i);
			if (i == selected) {
				g.fill(listX - 2, y - 2, listX + 110, y + 11, Draw.rgba(0xFFFFFF, 0.16f));
			} else if (!presetsOpen && rowAt(mouseX, mouseY) == i) {
				g.fill(listX - 2, y - 2, listX + 110, y + 11, Draw.rgba(0xFFFFFF, 0.07f));
			}
			int grip = 0xFF6E6E6E;
			for (int r = 0; r < 3; r++) {
				g.fill(listX, y + 1 + r * 2, listX + 2, y + 2 + r * 2, grip);
				g.fill(listX + 3, y + 1 + r * 2, listX + 5, y + 2 + r * 2, grip);
			}
			g.fill(listX + 8, y + 2, listX + 13, y + 7, 0xFF000000 | tab.color);
			int nameEnd = listX + 16 + font.width(tab.name);
			g.text(font, tab.name, listX + 16, y, i == selected ? 0xFFFFFFFF : 0xFFAAAAAA, true);
			if (tab.unread && tab.dot) {
				TabBar.drawDot(g, nameEnd + 1, y + 2);
			}
			Component mode = Component.translatable(tab.popped ? "chattoolkit.tabs.mode.window" : "chattoolkit.tabs.mode.docked");
			g.text(font, mode, listX + 108 - font.width(mode), y, 0xFF646464, false);
			y += ROW;
		}
		if (reordering && pressedRow >= 0) {
			int line = listTop() + dropIndex(mouseY) * ROW - 2;
			g.fill(listX - 2, line, listX + 110, line + 1, 0xFFFFFFFF);
		}
		g.text(font, Component.translatable("chattoolkit.tabs.drag_hint"), listX, py1 - 40, 0xFF646464, false);
		g.fill(px0 + 124, py0 + 28, px0 + 125, py1 - 26, Draw.rgba(0xFFFFFF, 0.16f));

		ChatTab tab = tab();
		if (tab == null) {
			return;
		}
		int ry = py0 + 32;
		g.text(font, Component.translatable("chattoolkit.tabs.name"), rightX, ry + 2, 0xFFAAAAAA, true);
		drawField(g, valueX, ry, 140);
		ry += 20;
		g.text(font, Component.translatable("chattoolkit.tabs.show_messages"), rightX, ry + 2, 0xFFAAAAAA, true);
		ry += 14;
		for (TabRule rule : tab.rules) {
			if (rule.isCatchAll()) {
				g.text(font, Component.translatable("chattoolkit.tabs.everything_else_hint"), rightX + 102, ry + 2, 0xFF787878, false);
			} else {
				drawField(g, rightX + 100, ry, 110);
				if (!rule.text.isEmpty()) {
					Component looks = Component.translatable("chattoolkit.tabs.looks_like");
					int lx = rightX + 216;
					g.text(font, looks, lx, ry + 2, 0xFF6E6E6E, false);
					g.text(font, TextFormatter.parse(rule.text), lx + font.width(looks) + 4, ry + 2, 0xFFFFFFFF, true);
				}
			}
			// little arrow on the rule kind button
			g.fill(rightX + 86, ry + 5, rightX + 91, ry + 6, 0xFFC8C8C8);
			g.fill(rightX + 87, ry + 6, rightX + 90, ry + 7, 0xFFC8C8C8);
			g.fill(rightX + 88, ry + 7, rightX + 89, ry + 8, 0xFFC8C8C8);
			ry += 15;
		}
		if (tab.rules.isEmpty()) {
			g.text(font, Component.translatable("chattoolkit.tabs.no_rules"), rightX + 70, ry + 2, 0xFF787878, false);
		}
		ry += 18;
		g.fill(rightX, ry, px1 - 8, ry + 1, Draw.rgba(0xFFFFFF, 0.1f));
		ry += 6;
		g.text(font, Component.translatable("chattoolkit.tabs.red_dot"), rightX, ry + 1, 0xFFAAAAAA, true);
		drawToggle(g, px1 - 30, ry, tab.dot);
		ry += 15;
		g.text(font, Component.translatable("chattoolkit.tabs.send_as"), rightX, ry + 2, 0xFFAAAAAA, true);
		drawField(g, valueX, ry, 140);
		ry += 16;
		g.text(font, Component.translatable("chattoolkit.tabs.color"), rightX, ry + 2, 0xFFAAAAAA, true);
		for (int i = 0; i < COLORS.length; i++) {
			int sx = valueX + i * 14;
			g.fill(sx, ry, sx + 11, ry + 11, 0xFF000000 | COLORS[i]);
			if (COLORS[i] == tab.color) {
				Draw.outline(g, sx - 1, ry - 1, sx + 12, ry + 12, 0xFFFFFFFF);
			}
		}
		ry += 16;
		g.text(font, Component.translatable("chattoolkit.tabs.window"), rightX, ry + 2, 0xFFAAAAAA, true);

		g.fill(px0, py1 - 22, px1, py1 - 21, Draw.rgba(0xFFFFFF, 0.16f));
	}

	private static void drawToggle(GuiGraphicsExtractor g, int x, int y, boolean on) {
		g.fill(x, y, x + 20, y + 10, on ? 0x80FFFFFF : 0x30FFFFFF);
		int kx = on ? x + 11 : x + 1;
		g.fill(kx, y + 1, kx + 8, y + 9, on ? 0xFFFFFFFF : 0xFF969696);
	}

	private void drawPresets(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		g.fill(pw0, ph0, pw1, ph1, 0xF5101012);
		Draw.outline(g, pw0, ph0, pw1, ph1, Draw.rgba(0xFFFFFF, 0.24f));
		Component heading = Component.translatable(importing ? "chattoolkit.tabs.import_title" : "chattoolkit.tabs.presets");
		g.text(font, heading, pw0 + 8, ph0 + 7, 0xFFFFFFFF, true);
		g.fill(pw0, ph0 + 20, pw1, ph0 + 21, Draw.rgba(0xFFFFFF, 0.16f));
		if (importing) {
			int y = ph0 + 30;
			for (int i = 0; i < importFilesShown; i++) {
				if (mouseX >= pw0 + 6 && mouseX < pw1 - 6 && mouseY >= y - 3 && mouseY < y + 10) {
					g.fill(pw0 + 6, y - 3, pw1 - 6, y + 10, Draw.rgba(0xFFFFFF, 0.08f));
				}
				y += 14;
			}
			if (importFilesShown == 0) {
				g.text(font, Component.translatable("chattoolkit.tabs.no_files"), pw0 + 10, y, 0xFF969696, false);
				y += 12;
			}
			y += 6;
			for (FormattedCharSequence line : font.split(Component.translatable("chattoolkit.tabs.import_hint"), pw1 - pw0 - 20)) {
				g.text(font, line, pw0 + 10, y, 0xFF787878, false);
				y += 10;
			}
			return;
		}
		int y = ph0 + 28;
		TabPreset active = TabStore.active();
		for (TabPreset preset : TabStore.presets()) {
			boolean sel = preset == presetSelected;
			if (sel) {
				g.fill(pw0 + 6, y - 3, pw1 - 6, y + 21, Draw.rgba(0xFFFFFF, 0.14f));
			} else if (mouseX >= pw0 + 6 && mouseX < pw1 - 6 && mouseY >= y - 3 && mouseY < y + 21) {
				g.fill(pw0 + 6, y - 3, pw1 - 6, y + 21, Draw.rgba(0xFFFFFF, 0.06f));
			}
			// radio: filled for the preset that's loaded right now
			int rx = pw0 + 10;
			int c = 0xFFDCDCDC;
			g.fill(rx + 1, y + 1, rx + 4, y + 2, c);
			g.fill(rx + 1, y + 5, rx + 4, y + 6, c);
			g.fill(rx, y + 2, rx + 1, y + 5, c);
			g.fill(rx + 4, y + 2, rx + 5, y + 5, c);
			if (preset == active) {
				g.fill(rx + 2, y + 3, rx + 3, y + 4, 0xFFFFFFFF);
			}
			if (preset != renaming) {
				g.text(font, preset.name, pw0 + 20, y, sel ? 0xFFFFFFFF : 0xFFD2D2D2, true);
			} else {
				drawField(g, pw0 + 18, y - 2, 150);
			}
			Component count = Component.translatable(preset.tabs.size() == 1 ? "chattoolkit.tabs.count_one" : "chattoolkit.tabs.count",
				preset.tabs.size());
			g.text(font, count, pw1 - 10 - font.width(count), y, 0xFF8C8C8C, false);
			StringBuilder names = new StringBuilder();
			for (ChatTab tab : preset.tabs) {
				if (!names.isEmpty()) {
					names.append(", ");
				}
				names.append(tab.name);
			}
			String sub = preset.server.isBlank() ? names.toString()
				: Component.translatable("chattoolkit.tabs.loads_on", preset.server).getString();
			g.text(font, font.plainSubstrByWidth(sub, pw1 - pw0 - 40), pw0 + 20, y + 10, 0xFF6E6E6E, false);
			y += 28;
		}
		g.fill(pw0 + 6, presetsDividerY, pw1 - 6, presetsDividerY + 1, Draw.rgba(0xFFFFFF, 0.12f));
		int fy = presetsDividerY + 6;
		g.text(font, Component.translatable("chattoolkit.tabs.load_on_join"), pw0 + 10, fy + 2, 0xFFAAAAAA, true);
		drawField(g, pw0 + 80, fy, 110);
	}
}
