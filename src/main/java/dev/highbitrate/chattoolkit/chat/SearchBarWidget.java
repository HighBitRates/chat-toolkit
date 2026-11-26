package dev.highbitrate.chattoolkit.chat;

import com.mojang.blaze3d.platform.InputConstants;
import dev.highbitrate.chattoolkit.config.ChatToolkitConfig;
import dev.highbitrate.chattoolkit.ui.Anim;
import dev.highbitrate.chattoolkit.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

// The search box under the chat. Drawn like the vanilla chat input so it doesn't stand out.
// Focus is owned by ChatScreenController, this class just draws and edits the text.
public final class SearchBarWidget extends AbstractWidget {
	public static final int HEIGHT = 12;
	private static final int TOGGLE_W = 14;
	private static final String[] TOGGLES = {"Aa", "W", ".*"};
	private static final String[] TOGGLE_TIPS = {"chattoolkit.search.toggle.case", "chattoolkit.search.toggle.word", "chattoolkit.search.toggle.regex"};
	private static final int TEXT_COLOR = 0xE0E0E0;
	private static final int HINT_COLOR = 0x808080;
	private static final int OFF_COLOR = 0x707070;
	// low opacity black, so the hint sits quietly in the bar
	private static final int PLACEHOLDER = 0x8C000000;

	private final Font font;
	private final EditBox field;
	private final ChatScreenController controller;
	private final Anim shown = new Anim(1, 16);
	private final long openedAt = System.currentTimeMillis();
	private boolean silent;

	public SearchBarWidget(ChatScreenController controller, Font font) {
		super(0, 0, 200, HEIGHT, Component.translatable("chattoolkit.search.narration"));
		this.controller = controller;
		this.font = font;
		field = new EditBox(font, 0, 0, 100, 10, Component.translatable("chattoolkit.search.narration"));
		field.setBordered(false);
		field.setMaxLength(200);
		field.setTextColor(TEXT_COLOR);
		field.setResponder(text -> {
			if (!silent) {
				SearchState.setQuery(text);
			}
		});
	}

	public void layout(int screenWidth, int screenHeight) {
		// flush with the left edge of the screen and the right edge of the chat background, like the chat box
		int right = ChatToolkitConfig.get().searchWidth == ChatToolkitConfig.SearchWidth.FULL
			? screenWidth
			: ChatGeometry.screenRight();
		right = Math.min(Math.max(right, 120), screenWidth);
		setX(0);
		setY(screenHeight - 37 - ChatLift.get());
		setWidth(right);
		setHeight(HEIGHT);
		field.setX(getX() + 4);
		field.setY(getY() + 2);
		field.setWidth(Math.max(20, textRight() - field.getX()));
	}

	private int togglesX() {
		return getRight() - TOGGLES.length * TOGGLE_W;
	}

	// where the typed text has to stop so it doesn't run under the counter / x / toggles
	private int textRight() {
		int x = togglesX() - 4;
		Component counter = counter();
		if (counter != null) {
			x -= font.width(counter) + 4;
		}
		if (!field.getValue().isEmpty()) {
			x -= 10;
		}
		return x;
	}

	private int clearX() {
		Component counter = counter();
		return togglesX() - 4 - (counter == null ? 0 : font.width(counter) + 4) - 8;
	}

	private Component counter() {
		String error = SearchState.error();
		if (error != null) {
			return Component.translatable(error.equals("too slow") ? "chattoolkit.search.too_slow" : "chattoolkit.search.invalid_regex");
		}
		if (!SearchState.isActive()) {
			return null;
		}
		int n = SearchState.matchCount();
		if (n == 0) {
			return Component.translatable("chattoolkit.search.none");
		}
		return n == 1 ? Component.translatable("chattoolkit.search.one") : Component.translatable("chattoolkit.search.many", n);
	}

	private int toggleAt(double mx, double my) {
		if (my < getY() || my >= getBottom() || mx < togglesX() || mx >= getRight()) {
			return -1;
		}
		return Math.min(TOGGLES.length - 1, (int) ((mx - togglesX()) / TOGGLE_W));
	}

	private boolean overClear(double mx, double my) {
		int x = clearX();
		return !field.getValue().isEmpty() && mx >= x - 1 && mx < x + 7 && my >= getY() && my < getBottom();
	}

	// hidden while typing a command so it doesn't get in the way of the command suggestions
	public boolean isShown() {
		return visible && (!controller.inputValue().startsWith("/") || controller.isSearchFocused() || !field.getValue().isEmpty());
	}

	public String value() {
		return field.getValue();
	}

	public void setValueSilently(String value) {
		silent = true;
		field.setValue(value);
		silent = false;
	}

	public void setValueAndSearch(String value) {
		field.setValue(value);
		SearchState.setQuery(value);
	}

	public void selectAll() {
		field.moveCursorToEnd(false);
		field.setHighlightPos(0);
	}

	@Override
	public boolean isFocused() {
		return controller.isSearchFocused();
	}

	void syncFocus(boolean focused) {
		if (field.isFocused() != focused) {
			field.setFocused(focused);
		}
		if (focused) {
			// make sure SDL is still sending us typed characters, the chat input can switch that off when it loses focus
			Minecraft.getInstance().textInputManager().startTextInput(field);
		}
	}

	public boolean fieldKeyPressed(KeyEvent event) {
		return field.keyPressed(event);
	}

	public boolean fieldCharTyped(CharacterEvent event) {
		return field.charTyped(event);
	}

	@Override
	public boolean isMouseOver(double mx, double my) {
		return isShown() && mx >= getX() && mx < getRight() && my >= getY() && my < getBottom();
	}

	// Called by the controller before vanilla sees the click. Returns true if the click was on the bar.
	boolean handleClick(MouseButtonEvent event) {
		if (!isMouseOver(event.x(), event.y())) {
			return false;
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			if (!field.getValue().isEmpty()) {
				setValueAndSearch("");
				click();
			}
			return true;
		}
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
			return true;
		}

		int toggle = toggleAt(event.x(), event.y());
		if (toggle >= 0) {
			ChatToolkitConfig config = ChatToolkitConfig.get();
			switch (toggle) {
				case 0 -> config.caseSensitive = !config.caseSensitive;
				case 1 -> config.wholeWords = !config.wholeWords;
				default -> config.regex = !config.regex;
			}
			ChatToolkitConfig.save();
			SearchState.rebuild();
			click();
			return true;
		}
		if (overClear(event.x(), event.y())) {
			setValueAndSearch("");
			click();
			return true;
		}

		field.setFocused(true);
		if (event.x() >= field.getX() && event.x() < field.getRight()) {
			field.mouseClicked(event, false);
		} else {
			field.moveCursorToEnd(false);
		}
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return handleClick(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return field.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return field.charTyped(event);
	}

	@Override
	public void playDownSound(SoundManager soundManager) {
	}

	private static void click() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.6f, 0.35f));
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		Minecraft mc = Minecraft.getInstance();
		layout(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());

		float fadeIn = Anim.easeOutCubic(Anim.progress(openedAt, 180));
		float alpha = fadeIn * shown.target(isShown() ? 1 : 0).get();
		if (alpha < 0.01f) {
			return;
		}

		int x0 = getX();
		int y0 = getY();
		int x1 = getRight();
		int y1 = getBottom();
		boolean error = SearchState.error() != null;

		int bg = Draw.chatBackground();
		g.fill(x0, y0, x1, y1, Draw.argb(Math.round((bg >>> 24) * alpha), bg));

		if (field.getValue().isEmpty() && !isFocused()) {
			int hint = Draw.argb(Math.round((PLACEHOLDER >>> 24) * alpha), PLACEHOLDER);
			g.text(font, Component.translatable("chattoolkit.search.placeholder"), field.getX(), field.getY(), hint, false);
		}
		field.setTextColor(Draw.rgba(error ? Draw.ERROR : TEXT_COLOR, alpha));
		field.extractRenderState(g, mouseX, mouseY, partialTick);

		Component counter = counter();
		if (counter != null) {
			int color = error || SearchState.matchCount() == 0 ? 0xD08080 : HINT_COLOR;
			g.text(font, counter, togglesX() - 4 - font.width(counter), y0 + 2, Draw.rgba(color, alpha), false);
		}
		if (!field.getValue().isEmpty()) {
			boolean hover = overClear(mouseX, mouseY);
			Draw.cross(g, clearX(), y0 + 3, Draw.rgba(hover ? 0xFFFFFF : HINT_COLOR, alpha));
		}

		ChatToolkitConfig config = ChatToolkitConfig.get();
		boolean[] on = {config.caseSensitive, config.wholeWords, config.regex};
		int hovered = isMouseOver(mouseX, mouseY) ? toggleAt(mouseX, mouseY) : -1;
		for (int i = 0; i < TOGGLES.length; i++) {
			int x = togglesX() + i * TOGGLE_W;
			if (on[i]) {
				g.fill(x, y0, x + TOGGLE_W, y1, Draw.rgba(0xFFFFFF, 0.18f * alpha));
			} else if (i == hovered) {
				g.fill(x, y0, x + TOGGLE_W, y1, Draw.rgba(0xFFFFFF, 0.08f * alpha));
			}
			int color = on[i] ? 0xFFFFFF : i == hovered ? 0xA0A0A0 : OFF_COLOR;
			g.text(font, TOGGLES[i], x + (TOGGLE_W - font.width(TOGGLES[i]) + 1) / 2, y0 + 2, Draw.rgba(color, alpha), false);
			if (i == hovered) {
				Component state = Component.translatable(on[i] ? "chattoolkit.on" : "chattoolkit.off");
				g.setTooltipForNextFrame(font, Component.translatable(TOGGLE_TIPS[i], state), mouseX, mouseY);
			}
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, Component.translatable("chattoolkit.search.narration_value", field.getValue()));
	}
}
