package dev.highbitrate.chattoolkit.tabs;

import com.mojang.blaze3d.platform.InputConstants;
import dev.highbitrate.chattoolkit.text.TextFormatter;
import dev.highbitrate.chattoolkit.ui.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.List;

// A short explanation of chat tabs, opened from the tab editor or the main settings
public final class HowToUseScreen extends Screen {
	private static final int STEPS = 5;
	private final @Nullable Screen parent;
	private int x0, y0, x1, y1;

	public HowToUseScreen(@Nullable Screen parent) {
		super(Component.translatable("chattoolkit.tabs.help.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int w = Math.min(421, width - 16);
		int h = Math.min(272, height - 12);
		x0 = (width - w) / 2;
		y0 = (height - h) / 2;
		x1 = x0 + w;
		y1 = y0 + h;
	}

	private boolean overDone(double mx, double my) {
		return mx >= x1 - 68 && mx < x1 - 8 && my >= y1 - 17 && my < y1 - 5;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && overDone(event.x(), event.y())) {
			onClose();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		g.fill(x0, y0, x1, y1, Draw.rgba(0x000000, 0.7f));
		g.text(font, title, x0 + 8, y0 + 7, 0xFFFFFFFF, true);
		g.fill(x0, y0 + 20, x1, y0 + 21, Draw.rgba(0xFFFFFF, 0.16f));

		int y = y0 + 30;
		int textWidth = x1 - x0 - 40;
		for (int i = 1; i <= STEPS; i++) {
			g.fill(x0 + 10, y, x0 + 20, y + 10, Draw.rgba(0xFFFFFF, 0.2f));
			g.text(font, String.valueOf(i), x0 + 13, y + 1, 0xFFFFFFFF, true);
			g.text(font, Component.translatable("chattoolkit.tabs.help.step" + i), x0 + 28, y + 1, 0xFFFFFFFF, true);
			y += 12;
			List<FormattedCharSequence> lines = font.split(Component.translatable("chattoolkit.tabs.help.step" + i + ".text"), textWidth);
			for (FormattedCharSequence line : lines) {
				g.text(font, line, x0 + 28, y, 0xFFAAAAAA, false);
				y += 10;
			}
			y += 6;
		}

		// worked example
		g.fill(x0 + 10, y - 2, x1 - 10, y + 32, Draw.rgba(0xFFFFFF, 0.07f));
		Component example = Component.translatable("chattoolkit.tabs.help.example");
		g.text(font, example, x0 + 16, y + 1, 0xFF969696, false);
		g.text(font, "&8[&7OOC&8]&r", x0 + 20 + font.width(example), y + 1, 0xFFFFFFFF, true);
		Component catches = Component.translatable("chattoolkit.tabs.help.catches");
		g.text(font, catches, x0 + 16, y + 11, 0xFF969696, false);
		g.text(font, TextFormatter.parse("&8[&7OOC&8] &bKiko&f: anyone got spare iron"), x0 + 22 + font.width(catches), y + 11, 0xFFFFFFFF, true);
		g.text(font, Component.translatable("chattoolkit.tabs.help.tip"), x0 + 16, y + 21, 0xFF787878, false);

		g.fill(x0, y1 - 22, x1, y1 - 21, Draw.rgba(0xFFFFFF, 0.16f));
		boolean over = overDone(mouseX, mouseY);
		g.fill(x1 - 68, y1 - 17, x1 - 8, y1 - 5, Draw.rgba(0xFFFFFF, over ? 0.36f : 0.27f));
		Component done = Component.translatable("chattoolkit.tabs.help.done");
		g.text(font, done, x1 - 38 - font.width(done) / 2, y1 - 15, 0xFFFFFFFF, true);
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}
}
