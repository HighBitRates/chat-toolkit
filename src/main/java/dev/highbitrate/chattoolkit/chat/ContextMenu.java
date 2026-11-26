package dev.highbitrate.chattoolkit.chat;

import com.mojang.blaze3d.platform.InputConstants;
import dev.highbitrate.chattoolkit.ui.Anim;
import dev.highbitrate.chattoolkit.ui.Draw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;

// The right-click menu for a chat message (and for tabs, with no message). Square, translucent, fades and scales in from the cursor
public final class ContextMenu {
	// One row. detail is shown dimmed on the right (e.g. the player name)
	public record Item(Component label, @Nullable Component detail, Runnable action, boolean dividerBefore) {
	}

	private static final int ITEM_HEIGHT = 13;
	private static final int PAD_X = 7;
	private static final int PAD_Y = 3;
	private static final int HEADER_HEIGHT = 13;
	private static final int DIVIDER = 5;
	private static final int MIN_WIDTH = 118;
	private static final float OPEN_MS = 130.0f;
	private static final float CLOSE_MS = 90.0f;

	private final @Nullable GuiMessage target;
	private final @Nullable Component header;
	private final List<Item> items;
	private final int[] itemTops;
	private final Anim[] hover;
	private final int originX;
	private final int originY;
	private final int x;
	private final int y;
	private final int width;
	private final int height;
	private final long openedAt = System.currentTimeMillis();
	private long closedAt = -1;
	private int mouseIndex = -1;
	private int keyboardIndex = -1;

	public ContextMenu(@Nullable GuiMessage target, @Nullable Component header, List<Item> items, int mouseX, int mouseY, int screenWidth, int screenHeight, Font font) {
		this.target = target;
		this.header = header;
		this.items = List.copyOf(items);
		this.itemTops = new int[this.items.size()];
		this.hover = new Anim[this.items.size()];

		int w = MIN_WIDTH;
		if (header != null) {
			w = Math.max(w, font.width(header) + PAD_X * 2);
		}
		int h = PAD_Y + (header != null ? HEADER_HEIGHT + 1 : 0);
		for (int i = 0; i < this.items.size(); i++) {
			Item item = this.items.get(i);
			int rowWidth = font.width(item.label()) + PAD_X * 2 + 4;
			if (item.detail() != null) {
				rowWidth += 10 + font.width(item.detail());
			}
			w = Math.max(w, rowWidth);
			if (item.dividerBefore() && i > 0) {
				h += DIVIDER;
			}
			itemTops[i] = h;
			h += ITEM_HEIGHT;
			hover[i] = new Anim(0, 24);
		}
		h += PAD_Y;

		this.width = Math.min(w, screenWidth - 8);
		this.height = h;
		int px = mouseX + 2;
		if (px + this.width > screenWidth - 2) {
			px = mouseX - this.width - 2;
		}
		int py = mouseY + 2;
		if (py + this.height > screenHeight - 2) {
			py = mouseY - this.height - 2;
		}
		this.x = Math.max(2, px);
		this.y = Math.max(2, py);
		this.originX = mouseX;
		this.originY = mouseY;
	}

	public @Nullable GuiMessage target() {
		return target;
	}

	public boolean isOpen() {
		return closedAt < 0;
	}

	public boolean isFinished() {
		return closedAt >= 0 && Anim.progress(closedAt, CLOSE_MS) >= 1.0f;
	}

	public void close() {
		if (closedAt < 0) {
			closedAt = System.currentTimeMillis();
		}
	}

	// 0..1 visibility, including the open and close animations
	public float alpha() {
		float open = Anim.easeOutCubic(Anim.progress(openedAt, OPEN_MS));
		if (closedAt >= 0) {
			open *= 1.0f - Anim.progress(closedAt, CLOSE_MS);
		}
		return open;
	}

	public boolean contains(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	private int itemAt(double mouseX, double mouseY) {
		if (!contains(mouseX, mouseY)) {
			return -1;
		}
		for (int i = 0; i < items.size(); i++) {
			int top = y + itemTops[i];
			if (mouseY >= top && mouseY < top + ITEM_HEIGHT) {
				return i;
			}
		}
		return -1;
	}

	// returns true if the click hit the menu. clicks outside just close it (and get eaten)
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!isOpen()) {
			return false;
		}
		int index = itemAt(mouseX, mouseY);
		if (index >= 0 && (button == InputConstants.MOUSE_BUTTON_LEFT || button == InputConstants.MOUSE_BUTTON_RIGHT)) {
			activate(index);
		} else if (!contains(mouseX, mouseY)) {
			close();
		}
		return true;
	}

	public boolean keyPressed(KeyEvent event) {
		if (!isOpen()) {
			return false;
		}
		if (event.isEscape()) {
			close();
			return true;
		}
		if (event.isDown() || event.isUp()) {
			int dir = event.isDown() ? 1 : -1;
			int start = keyboardIndex >= 0 ? keyboardIndex : mouseIndex >= 0 ? mouseIndex : (dir > 0 ? -1 : items.size());
			keyboardIndex = Math.floorMod(start + dir, items.size());
			return true;
		}
		if (event.isConfirmation() && keyboardIndex >= 0) {
			activate(keyboardIndex);
			return true;
		}
		// any other key: get out of the way
		close();
		return false;
	}

	public void mouseMoved(double mouseX, double mouseY) {
		int index = itemAt(mouseX, mouseY);
		if (index != mouseIndex) {
			mouseIndex = index;
			if (index >= 0) {
				keyboardIndex = -1;
			}
		}
	}

	private void activate(int index) {
		close();
		items.get(index).action().run();
	}

	public void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		float alpha = alpha();
		if (alpha <= 0.01f) {
			return;
		}
		if (isOpen()) {
			mouseMoved(mouseX, mouseY);
		}
		float scale = 0.93f + 0.07f * alpha;

		graphics.nextStratum();
		graphics.pose().pushMatrix();
		graphics.pose().translate(originX, originY);
		graphics.pose().scale(scale, scale);
		graphics.pose().translate(-originX, -originY);

		// noticeably more solid than other panels so the chat underneath doesn't bleed through
		float bg = Math.min(0.97f, Math.max(0.7f, Draw.panelAlpha() + 0.4f));
		graphics.fill(x, y, x + width, y + height, Draw.rgba(0x000000, bg * alpha));
		Draw.outline(graphics, x, y, x + width, y + height, Draw.rgba(Draw.WHITE, 0.10f * alpha));

		if (header != null) {
			graphics.text(font, header, x + PAD_X, y + PAD_Y + 2, Draw.rgba(Draw.TEXT_DIM, alpha), false);
			graphics.fill(x + 4, y + PAD_Y + HEADER_HEIGHT, x + width - 4, y + PAD_Y + HEADER_HEIGHT + 1, Draw.rgba(Draw.WHITE, 0.08f * alpha));
		}

		int active = keyboardIndex >= 0 ? keyboardIndex : mouseIndex;
		for (int i = 0; i < items.size(); i++) {
			Item item = items.get(i);
			int top = y + itemTops[i];
			if (item.dividerBefore() && i > 0) {
				int lineY = top - DIVIDER / 2 - 1;
				graphics.fill(x + 4, lineY, x + width - 4, lineY + 1, Draw.rgba(Draw.WHITE, 0.08f * alpha));
			}
			float h = hover[i].target(isOpen() && i == active ? 1 : 0).get();
			if (h > 0.01f) {
				graphics.fill(x + 1, top, x + width - 1, top + ITEM_HEIGHT, Draw.rgba(Draw.accent(), 0.22f * h * alpha));
				graphics.fill(x + 1, top, x + 3, top + ITEM_HEIGHT, Draw.rgba(Draw.accent(), 0.9f * h * alpha));
			}
			int textX = x + PAD_X + Math.round(2 * h);
			graphics.text(font, item.label(), textX, top + 3, Draw.rgba(Draw.lerpRgb(0xE6E6E6, Draw.TEXT, h), alpha), false);
			if (item.detail() != null) {
				int detailWidth = font.width(item.detail());
				graphics.text(font, item.detail(), x + width - PAD_X - detailWidth, top + 3, Draw.rgba(Draw.TEXT_FAINT, alpha), false);
			}
		}
		graphics.pose().popMatrix();
	}
}
