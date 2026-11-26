package dev.highbitrate.chattoolkit.chat;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.highbitrate.chattoolkit.config.ChatToolkitConfig;
import dev.highbitrate.chattoolkit.hud.PinnedHud;
import dev.highbitrate.chattoolkit.tabs.ChatTab;
import dev.highbitrate.chattoolkit.tabs.TabBar;
import dev.highbitrate.chattoolkit.tabs.TabEditorScreen;
import dev.highbitrate.chattoolkit.tabs.TabManager;
import dev.highbitrate.chattoolkit.tabs.TabWindows;
import dev.highbitrate.chattoolkit.tabs.WindowSettings;
import dev.highbitrate.chattoolkit.screenshot.ScreenshotToast;
import dev.highbitrate.chattoolkit.mixin.ChatComponentAccessor;
import dev.highbitrate.chattoolkit.mixin.ChatScreenAccessor;
import dev.highbitrate.chattoolkit.notes.Note;
import dev.highbitrate.chattoolkit.notes.NoteStore;
import dev.highbitrate.chattoolkit.text.TextFormatter;
import dev.highbitrate.chattoolkit.ui.Anim;
import dev.highbitrate.chattoolkit.ui.Draw;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Everything the toolkit adds to the open chat screen: the search bar, the right-click menu, hover
// highlights, middle-click copy, little "Copied!" toasts and keyboard shortcuts. One instance per chat screen
public final class ChatScreenController {
	private static final int KEY_PAGE_UP = 0x4000004B;
	private static final int KEY_PAGE_DOWN = 0x4000004E;
	private static final float FLASH_MS = 1600.0f;
	private static final float TOAST_MS = 1100.0f;

	private static @Nullable ChatScreenController current;
	private static boolean focusSearchOnOpen;

	private final ChatScreen screen;
	private final @Nullable SearchBarWidget searchBar;
	private @Nullable Object installedFor;
	private @Nullable ContextMenu menu;
	private boolean searching;
	// what the input box held when the chat opened, to notice the first edit
	private String openedWith = "";
	private @Nullable GuiMessage hovered;
	private final Anim hoverAnim = new Anim(0, 20);
	private @Nullable GuiMessage flashMessage;
	private long flashStart;
	private @Nullable Toast toast;

	private record Toast(Component text, int x, int y, long start, boolean error) {
	}

	private ChatScreenController(ChatScreen screen) {
		this.screen = screen;
		ChatToolkitConfig config = ChatToolkitConfig.get();
		this.searchBar = config.searchBar ? new SearchBarWidget(this, Minecraft.getInstance().font) : null;
		if (searchBar != null) {
			String restore = !SearchState.query().isEmpty() ? SearchState.query() : SearchState.remembered();
			if (!restore.isEmpty()) {
				searchBar.setValueSilently(restore);
				SearchState.setQuery(restore);
			}
		}
	}

	public static @Nullable ChatScreenController current() {
		return current;
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof ChatScreen chat) {
				attach(chat);
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(ChatScreenController::tick);
	}

	// Used by the "Search chat" key: the next chat screen opens with the search box focused
	public static void requestSearchFocus() {
		focusSearchOnOpen = true;
	}

	private static void attach(ChatScreen chat) {
		if (current != null && current.screen != chat) {
			current.dispose();
			current = null;
		}
		if (current == null) {
			current = new ChatScreenController(chat);
		}
		current.install();
	}

	private static void tick(Minecraft client) {
		ChatScreenController controller = current;
		if (controller == null) {
			return;
		}
		Screen screen = client.gui.screen();
		if (screen != controller.screen && (screen == null || screen instanceof ChatScreen)) {
			// Chat was closed (other screens, like "open this link?", keep the search alive until you come back)
			controller.dispose();
			current = null;
		}
	}

	private void install() {
		Object marker = ScreenMouseEvents.allowMouseClick(screen);
		if (marker == installedFor) {
			return; // Fabric fires AFTER_INIT twice on resize; only hook each generation of events once
		}
		installedFor = marker;

		ChatScreenAccessor access = (ChatScreenAccessor) screen;
		if (access.chattoolkit$isDraft() && ChatToolkitConfig.get().keepDrafts) {
			// vanilla shows drafts in grey italics and wipes them all on one Backspace; keep it as normal text
			access.chattoolkit$setDraft(false);
			TypingAids.markDraftRestored(true);
		} else if (!access.chattoolkit$isDraft()) {
			TypingAids.markDraftRestored(false);
		}
		openedWith = inputValue();

		if (searchBar != null) {
			searchBar.layout(screen.width, screen.height);
			List<AbstractWidget> widgets = Screens.getWidgets(screen);
			if (!widgets.contains(searchBar)) {
				widgets.add(searchBar);
			}
			if (focusSearchOnOpen) {
				focusSearchOnOpen = false;
				focusSearch(true);
			}
		}

		ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> onMouseClick(event));
		ScreenMouseEvents.allowMouseDrag(screen).register((s, event, dx, dy) -> {
			if (WindowSettings.isDragging()) {
				WindowSettings.dragTo(event.x());
				return false;
			}
			if (PinnedHud.isDragging()) {
				PinnedHud.dragTo(event.x(), event.y(), screen.width, screen.height);
				return false;
			}
			if (TabWindows.dragging() != null) {
				TabWindows.dragTo(event.x(), event.y(), screen.width, screen.height);
				return false;
			}
			return true;
		});
		ScreenMouseEvents.allowMouseRelease(screen).register((s, event) -> {
			if (WindowSettings.isDragging()) {
				WindowSettings.endDrag();
				return false;
			}
			if (PinnedHud.isDragging()) {
				PinnedHud.endDrag();
				return false;
			}
			if (TabWindows.dragging() != null) {
				boolean moving = TabWindows.isMoving();
				ChatTab tab = TabWindows.endDrag();
				if (moving && tab != null && TabBar.overBar(event.x(), event.y(), screen.height)) {
					TabManager.dock(tab, TabBar.insertIndex(event.x()));
					TabManager.select(tab);
				}
				return false;
			}
			return true;
		});
		ScreenMouseEvents.allowMouseScroll(screen).register((s, x, y, h, v) -> onMouseScroll(x, y, v));
		ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> onKeyPress(event));
		ScreenKeyboardEvents.allowCharType(screen).register((s, event) -> onCharType(event));
		ScreenEvents.beforeExtract(screen).register((s, graphics, mouseX, mouseY, partial) -> beforeRender(mouseX, mouseY));
		ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, partial) -> afterRender(graphics, mouseX, mouseY));
	}

	private void dispose() {
		TabManager.onChatClosed();
		WindowSettings.close();
		PinnedHud.endDrag();
		TabWindows.endDrag();
		menu = null;
		hovered = null;
		SearchState.deactivate(ChatToolkitConfig.get().rememberSearch);
	}

	// focus juggling between the chat input and the search box

	private @Nullable EditBox input() {
		return ((ChatScreenAccessor) screen).chattoolkit$getInput();
	}

	String inputValue() {
		EditBox input = input();
		return input == null ? "" : input.getValue();
	}

	// We track which box has focus ourselves instead of trusting the screen's focus, since the
	// chat screen (and other chat mods) like to put focus back on the chat input.
	private void focusSearch(boolean selectAll) {
		if (searchBar == null) {
			return;
		}
		searching = true;
		EditBox input = input();
		if (input != null) {
			input.setCanLoseFocus(true);
		}
		screen.setFocused(searchBar);
		syncFocus();
		if (selectAll) {
			searchBar.selectAll();
		}
	}

	private void focusInput() {
		searching = false;
		EditBox input = input();
		if (input != null) {
			screen.setFocused(input);
		}
		syncFocus();
	}

	private void syncFocus() {
		EditBox input = input();
		if (input != null && input.isFocused() == searching) {
			input.setCanLoseFocus(true);
			input.setFocused(!searching);
		}
		if (input != null) {
			input.setCanLoseFocus(searching);
		}
		if (searchBar != null) {
			searchBar.syncFocus(searching);
		}
	}

	private boolean searchFocused() {
		return searchBar != null && searching;
	}

	// input

	private boolean onMouseClick(MouseButtonEvent event) {
		double x = event.x();
		double y = event.y();
		int button = event.button();

		if (menu != null && menu.isOpen()) {
			menu.mouseClicked(x, y, button);
			return false;
		}
		if (WindowSettings.isOpen() && WindowSettings.mouseClicked(x, y)) {
			return false;
		}
		if (ScreenshotToast.mouseClicked(x, y)) {
			return false;
		}

		if (PinnedHud.contains(x, y) && (menu == null || !menu.isOpen())) {
			if (button == InputConstants.MOUSE_BUTTON_LEFT) {
				PinnedHud.startDrag(x, y);
				return false;
			}
			if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
				PinnedHud.resetPosition();
				return false;
			}
		}
		if (TabManager.enabled() && (menu == null || !menu.isOpen()) && onTabsClick(x, y, button)) {
			return false;
		}
		if (searchBar != null && searchBar.handleClick(event)) {
			if (button == InputConstants.MOUSE_BUTTON_LEFT && !searching) {
				focusSearch(false);
			}
			return false;
		}
		if (searching && button == InputConstants.MOUSE_BUTTON_LEFT) {
			focusInput();
		}

		ChatToolkitConfig config = ChatToolkitConfig.get();
		if (button == InputConstants.MOUSE_BUTTON_RIGHT && config.contextMenu) {
			ChatGeometry.Hit hit = ChatGeometry.lineAt(x, y);
			if (hit != null) {
				openMenu(hit.message(), (int) x, (int) y);
				return false;
			}
		}
		if (button == InputConstants.MOUSE_BUTTON_MIDDLE && config.middleClickCopy) {
			ChatGeometry.Hit hit = ChatGeometry.lineAt(x, y);
			if (hit != null) {
				copy(MessageExtras.plain(hit.message()), (int) x, (int) y);
				return false;
			}
		}
		return true;
	}

	private boolean onMouseScroll(double x, double y, double amount) {
		if (menu != null) {
			menu.close();
		}
		TabWindows.Hit hit = TabWindows.hitAt(x, y);
		if (hit != null) {
			TabWindows.scroll(hit.tab(), amount);
			return false;
		}
		return true;
	}

	// Clicks on popped-out windows and the tab bar. Returns true if the click was used.
	private boolean onTabsClick(double x, double y, int button) {
		TabWindows.Hit hit = TabWindows.hitAt(x, y);
		if (hit != null) {
			ChatTab tab = hit.tab();
			if (button == InputConstants.MOUSE_BUTTON_LEFT) {
				switch (hit.part()) {
					case CLOSE -> {
						TabManager.dock(tab, TabManager.tabs().size());
						click();
					}
					case TITLE -> {
						TabManager.setTarget(tab);
						TabWindows.startDrag(tab, false, x, y);
					}
					case GRIP -> TabWindows.startDrag(tab, true, x, y);
					case BODY -> {
						TabManager.setTarget(tab);
						focusInput();
					}
				}
				return true;
			}
			if (button == InputConstants.MOUSE_BUTTON_RIGHT && (hit.part() == TabWindows.Part.TITLE || hit.part() == TabWindows.Part.CLOSE)) {
				openTabMenu(tab, (int) x, (int) y);
				return true;
			}
			if (button == InputConstants.MOUSE_BUTTON_RIGHT && ChatToolkitConfig.get().contextMenu) {
				GuiMessage message = TabWindows.messageAt(tab, x, y);
				if (message != null) {
					openMenu(message, (int) x, (int) y);
				}
				return true;
			}
			if (button == InputConstants.MOUSE_BUTTON_MIDDLE && ChatToolkitConfig.get().middleClickCopy) {
				GuiMessage message = TabWindows.messageAt(tab, x, y);
				if (message != null) {
					copy(MessageExtras.plain(message), (int) x, (int) y);
				}
				return true;
			}
			return true;
		}

		if (!tabBarShown()) {
			return false;
		}
		TabBar.Slot slot = TabBar.slotAt(x, y, screen.height);
		if (slot == null) {
			if (button == InputConstants.MOUSE_BUTTON_LEFT && ChatGeometry.lineAt(x, y) != null) {
				// clicking the main chat means "type into the open tab" again
				TabManager.setTarget(null);
			}
			return false;
		}
		ChatTab tab = slot.tab();
		if (tab == null) {
			if (button == InputConstants.MOUSE_BUTTON_LEFT) {
				click();
				Minecraft.getInstance().gui.setScreen(TabEditorScreen.forNewTab(null));
			}
			return true;
		}
		if (button == InputConstants.MOUSE_BUTTON_LEFT) {
			if (slot.onArrow(x)) {
				TabManager.popOut(tab);
			} else {
				TabManager.select(tab);
			}
			click();
		} else if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
			openTabMenu(tab, (int) x, (int) y);
		}
		return true;
	}

	// Right-click on a tab or a window's title bar
	private void openTabMenu(ChatTab tab, int x, int y) {
		List<ContextMenu.Item> items = new ArrayList<>();
		if (tab.popped) {
			items.add(item("chattoolkit.tabs.menu.window", null, false,
				() -> WindowSettings.open(tab, x, y, screen.width, screen.height)));
			items.add(item("chattoolkit.tabs.menu.dock", null, false, () -> {
				TabManager.dock(tab, TabManager.tabs().size());
				TabManager.select(tab);
			}));
		} else {
			items.add(item("chattoolkit.tabs.menu.open", null, false, () -> TabManager.select(tab)));
			items.add(item("chattoolkit.tabs.menu.pop_out", null, false, () -> TabManager.popOut(tab)));
		}
		items.add(item(tab.muted ? "chattoolkit.tabs.menu.unmute" : "chattoolkit.tabs.menu.mute", null, true,
			() -> TabManager.setMuted(tab, !tab.muted)));
		items.add(item("chattoolkit.tabs.menu.edit", null, false,
			() -> Minecraft.getInstance().gui.setScreen(TabEditorScreen.forTab(null, tab))));
		menu = new ContextMenu(null, Component.literal(tab.name), items, x, y, screen.width, screen.height, Minecraft.getInstance().font);
		hovered = null;
	}

	private boolean tabBarShown() {
		return TabManager.enabled() && (TabWindows.dragging() != null || !inputValue().startsWith("/"));
	}

	private static void click() {
		Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
			net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.6f, 0.35f));
	}

	private boolean onKeyPress(KeyEvent event) {
		if (menu != null && menu.isOpen() && menu.keyPressed(event)) {
			return false;
		}
		if (WindowSettings.isOpen() && event.isEscape()) {
			WindowSettings.close();
			return false;
		}

		// modifiers on the event, or the live key state (some input paths don't fill in the modifiers)
		boolean ctrl = (event.hasControlDownWithQuirk() || Minecraft.getInstance().hasControlDown()) && !event.hasAltDown();
		if (searchBar != null && ctrl && !event.hasShiftDown() && event.shortcutKey() == 'f') {
			focusSearch(true);
			return false;
		}

		if (!searchFocused()) {
			return true;
		}

		if (event.isEscape()) {
			if (!searchBar.value().isEmpty()) {
				searchBar.setValueAndSearch("");
			} else {
				focusInput();
			}
			return false;
		}
		if (event.isConfirmation() || event.isCycleFocus()) {
			focusInput();
			return false;
		}
		ChatComponent chat = ChatHelper.chat();
		if (chat != null) {
			if (event.isUp()) {
				chat.scrollChat(1);
				return false;
			}
			if (event.isDown()) {
				chat.scrollChat(-1);
				return false;
			}
			if (event.shortcutKey() == KEY_PAGE_UP) {
				chat.scrollChat(chat.getLinesPerPage() - 1);
				return false;
			}
			if (event.shortcutKey() == KEY_PAGE_DOWN) {
				chat.scrollChat(-chat.getLinesPerPage() + 1);
				return false;
			}
		}
		searchBar.fieldKeyPressed(event);
		return false;
	}

	private boolean onCharType(CharacterEvent event) {
		if (searchFocused()) {
			searchBar.fieldCharTyped(event);
			return false;
		}
		return true;
	}

	public boolean isMenuOpen() {
		return menu != null && menu.isOpen();
	}

	public boolean isSearchFocused() {
		return searchFocused();
	}

	// True when the mouse is over one of our overlays, so vanilla chat hover tooltips should be suppressed
	public boolean capturesMouse(double mouseX, double mouseY) {
		return (menu != null && menu.alpha() > 0.05f && menu.contains(mouseX, mouseY)) || TabWindows.hitAt(mouseX, mouseY) != null
			|| WindowSettings.contains(mouseX, mouseY) || ScreenshotToast.contains(mouseX, mouseY);
	}

	// rendering

	private void beforeRender(int mouseX, int mouseY) {
		syncFocus();
		if (!inputValue().equals(openedWith)) {
			TypingAids.onEdited();
		}
		// follow the mouse every frame, drag events alone can look choppy
		PinnedHud.dragTo(mouseX, mouseY, screen.width, screen.height);
		TabWindows.setMouse(mouseX, mouseY);
		ScreenshotToast.setMouse(mouseX, mouseY);
		TabWindows.dragTo(mouseX, mouseY, screen.width, screen.height);
		layoutInput();
		if (menu != null && menu.isFinished()) {
			menu = null;
		}
		if (menu == null || !menu.isOpen()) {
			ChatGeometry.Hit hit = ChatToolkitConfig.get().hoverHighlight ? ChatGeometry.lineAt(mouseX, mouseY) : null;
			hovered = hit != null ? hit.message() : null;
		}
	}

	// The tint behind a message's lines: right-click target, hover, or the flash after "Show in chat"
	public int tintFor(GuiMessage message) {
		float alpha = 0.0f;
		int rgb = Draw.WHITE;
		float hover = hoverAnim.target(hovered != null && (menu == null || !menu.isOpen()) ? 1 : 0).get();
		if (menu != null && menu.target() == message) {
			alpha = 0.20f * menu.alpha();
			rgb = Draw.accent();
		} else if (message == hovered) {
			alpha = 0.10f * hover;
		}
		if (message == flashMessage) {
			float t = Anim.progress(flashStart, FLASH_MS);
			if (t >= 1.0f) {
				flashMessage = null;
			} else {
				float pulse = (1.0f - t) * (0.55f + 0.45f * (float) Math.cos(t * Math.PI * 4));
				if (0.35f * pulse > alpha) {
					alpha = 0.35f * pulse;
					rgb = Draw.accent();
				}
			}
		}
		return alpha > 0.004f ? Draw.rgba(rgb, alpha) : 0;
	}

	// Shows which tab a message will be sent through, and makes room for it in the input box
	private @Nullable ChatTab sendChip() {
		if (!TabManager.enabled() || inputValue().startsWith("/")) {
			return null;
		}
		ChatTab tab = TabManager.sendTarget();
		return tab != null && !tab.sendAs.isBlank() ? tab : null;
	}

	private void layoutInput() {
		EditBox input = input();
		if (input == null) {
			return;
		}
		ChatTab chip = sendChip();
		Font font = Minecraft.getInstance().font;
		int x = chip == null ? 4 : 4 + font.width(chip.name) + 9;
		int width = screen.width - x - TypingAids.reservedWidth(font);
		if (input.getX() != x || input.getWidth() != width) {
			input.setX(x);
			input.setWidth(width);
		}
	}

	private void afterRender(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Font font = Minecraft.getInstance().font;
		if (!SearchState.isActive()) {
			UnreadSeparator.render(graphics, font, screen.height);
		}
		TypingAids.render(graphics, font, screen.width, screen.height, inputValue());
		if (tabBarShown()) {
			boolean dropping = TabWindows.isMoving() && TabBar.overBar(mouseX, mouseY, screen.height);
			TabBar.render(graphics, font, mouseX, mouseY, screen.height, dropping);
		}
		ChatTab chip = sendChip();
		if (chip != null) {
			int w = font.width(chip.name) + 6;
			graphics.fill(3, screen.height - 13, 3 + w, screen.height - 3, Draw.rgba(chip.color, 0.35f));
			graphics.text(font, chip.name, 6, screen.height - 12, Draw.rgba(0xFFFFFF, 1), true);
		}
		TabWindows.Hit windowHit = TabWindows.hitAt(mouseX, mouseY);
		if (TabWindows.dragging() != null || (windowHit != null && windowHit.part() != TabWindows.Part.BODY && windowHit.part() != TabWindows.Part.CLOSE)) {
			graphics.requestCursor(CursorTypes.RESIZE_ALL);
		}
		boolean overPanel = PinnedHud.contains(mouseX, mouseY) && (menu == null || !menu.isOpen());
		PinnedHud.drawDragHint(graphics, overPanel);
		if (overPanel || PinnedHud.isDragging()) {
			graphics.requestCursor(CursorTypes.RESIZE_ALL);
		}
		WindowSettings.render(graphics, font);
		if (menu != null) {
			menu.render(graphics, font, mouseX, mouseY);
		}
		if (toast != null) {
			float t = Anim.progress(toast.start(), TOAST_MS);
			if (t >= 1.0f) {
				toast = null;
			} else {
				float alpha = t < 0.15f ? t / 0.15f : t > 0.6f ? 1.0f - (t - 0.6f) / 0.4f : 1.0f;
				int rise = Math.round(Anim.easeOutCubic(t) * 8);
				int width = font.width(toast.text()) + 8;
				int x = Math.max(2, Math.min(screen.width - width - 2, toast.x() - width / 2));
				int y = Math.max(2, toast.y() - 16 - rise);
				graphics.nextStratum();
				graphics.fill(x, y, x + width, y + 12, Draw.rgba(0x000000, Math.min(0.85f, Draw.panelAlpha() + 0.2f) * alpha));
				graphics.fill(x, y + 11, x + width, y + 12, Draw.rgba(toast.error() ? Draw.ERROR : Draw.accent(), alpha));
				graphics.text(font, toast.text(), x + 4, y + 2, Draw.rgba(Draw.TEXT, alpha), false);
			}
		}
	}

	private void showToast(Component text, int x, int y, boolean error) {
		toast = new Toast(text, x, y, System.currentTimeMillis(), error);
	}

	// right-click menu

	private void openMenu(GuiMessage message, int x, int y) {
		String plain = MessageExtras.plain(message);
		List<ContextMenu.Item> items = new ArrayList<>();
		items.add(item("chattoolkit.menu.copy", null, false, () -> copy(plain, x, y)));
		if (TextFormatter.hasFormatting(MessageExtras.content(message))) {
			items.add(item("chattoolkit.menu.copy_formatted", null, false, () -> copy(TextFormatter.toLegacy(MessageExtras.content(message)), x, y)));
		}
		items.add(item("chattoolkit.menu.paste", null, false, () -> pasteIntoChat(plain)));

		String sender = MessageAnalyzer.sender(plain);
		if (sender != null) {
			Component name = Component.literal(sender);
			items.add(item("chattoolkit.menu.copy_name", name, true, () -> copy(sender, x, y)));
			if (!MessageAnalyzer.isSelf(sender)) {
				items.add(item("chattoolkit.menu.message", name, false, () -> startPrivateMessage(sender)));
			}
			if (searchBar != null) {
				items.add(item("chattoolkit.menu.find_sender", name, false, () -> {
					focusSearch(false);
					searchBar.setValueAndSearch(sender);
				}));
			}
		}

		List<String> urls = MessageAnalyzer.urls(plain);
		String coords = MessageAnalyzer.coordinates(plain);
		boolean divider = true;
		if (!urls.isEmpty()) {
			String url = urls.getFirst();
			items.add(item("chattoolkit.menu.copy_link", null, divider, () -> copy(url, x, y)));
			divider = false;
		}
		if (coords != null) {
			items.add(item("chattoolkit.menu.copy_coords", Component.literal(coords), divider, () -> copy(coords, x, y)));
		}

		items.add(item("chattoolkit.menu.save_note", null, true, () -> {
			Note note = NoteStore.get().add(TextFormatter.toLegacy(MessageExtras.content(message)), null, null);
			showToast(Component.translatable("chattoolkit.toast.note_saved", note.id), x, y, false);
		}));
		if (SearchState.isActive()) {
			items.add(item("chattoolkit.menu.show_in_chat", null, false, () -> showInChat(message)));
		}
		items.add(item("chattoolkit.menu.remove", null, true, () -> {
			if (ChatHelper.removeMessage(message)) {
				showToast(Component.translatable("chattoolkit.toast.removed"), x, y, false);
			}
		}));

		Component header = ChatToolkitConfig.get().showTimestamps ? header(message) : null;
		Minecraft mc = Minecraft.getInstance();
		menu = new ContextMenu(message, header, items, x, y, screen.width, screen.height, mc.font);
		hovered = null;
	}

	private static ContextMenu.Item item(String key, @Nullable Component detail, boolean divider, Runnable action) {
		return new ContextMenu.Item(Component.translatable(key), detail, action, divider);
	}

	private static @Nullable Component header(GuiMessage message) {
		long time = MessageExtras.receivedAt(message);
		if (time <= 0) {
			return null;
		}
		String pattern = ChatToolkitConfig.get().clock24h ? "HH:mm:ss" : "h:mm:ss a";
		String clock = DateTimeFormatter.ofPattern(pattern, Locale.ROOT).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time));
		long ago = (System.currentTimeMillis() - time) / 1000;
		Component relative = ago < 45 ? Component.translatable("chattoolkit.time.now")
			: ago < 3600 ? Component.translatable("chattoolkit.time.minutes", Math.max(1, Math.round(ago / 60.0)))
			: ago < 86400 ? Component.translatable("chattoolkit.time.hours", ago / 3600)
			: Component.translatable("chattoolkit.time.days", ago / 86400);
		return Component.literal(clock + ", ").append(relative);
	}

	private void copy(String text, int x, int y) {
		ChatHelper.copyToClipboard(text);
		showToast(Component.translatable("chattoolkit.toast.copied"), x, y, false);
	}

	private void pasteIntoChat(String text) {
		focusInput();
		screen.insertText(text.replace('\n', ' '), false);
	}

	private void startPrivateMessage(String name) {
		EditBox input = input();
		if (input == null) {
			return;
		}
		focusInput();
		String current = input.getValue();
		input.setValue("/msg " + name + " " + (current.startsWith("/") ? "" : current));
		input.moveCursorToEnd(false);
	}

	private void showInChat(GuiMessage message) {
		if (searchBar != null) {
			searchBar.setValueSilently("");
		}
		SearchState.setQuery("");
		if (!TabManager.showsInMain(message)) {
			for (ChatTab tab : TabManager.tabs()) {
				if (!tab.popped && TabManager.belongsTo(message, tab)) {
					TabManager.select(tab);
					break;
				}
			}
		}
		ChatComponent chat = ChatHelper.chat();
		if (chat != null) {
			ChatComponentAccessor access = ChatHelper.access(chat);
			List<GuiMessage.Line> lines = access.chattoolkit$getTrimmedMessages();
			for (int i = 0; i < lines.size(); i++) {
				if (lines.get(i).parent() == message) {
					int perPage = chat.getLinesPerPage();
					int target = Math.max(0, Math.min(i - perPage / 3, lines.size() - perPage));
					access.chattoolkit$setScrollPos(target);
					break;
				}
			}
		}
		flashMessage = message;
		flashStart = System.currentTimeMillis();
		focusInput();
	}
}
