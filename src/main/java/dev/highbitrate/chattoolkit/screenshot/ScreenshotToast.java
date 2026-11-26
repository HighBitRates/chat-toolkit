package dev.highbitrate.chattoolkit.screenshot;

import com.mojang.blaze3d.Blaze3D;
import com.mojang.blaze3d.platform.NativeImage;
import dev.highbitrate.chattoolkit.ChatToolkit;
import dev.highbitrate.chattoolkit.config.ChatToolkitConfig;
import dev.highbitrate.chattoolkit.ui.Anim;
import dev.highbitrate.chattoolkit.ui.Draw;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

// A little card in the top right after you take a screenshot, instead of the chat message.
// Slides in, stays for a few seconds (longer while your mouse is on it), then slides out.
// Open the chat to click its buttons.
public final class ScreenshotToast implements HudElement {
	private static final Identifier ID = Identifier.fromNamespaceAndPath(ChatToolkit.MOD_ID, "screenshot_toast");
	private static final Identifier THUMB = Identifier.fromNamespaceAndPath(ChatToolkit.MOD_ID, "screenshot_thumbnail");
	private static final int W = 178;
	private static final int H = 40;
	private static final int MARGIN = 6;
	private static final int THUMB_W = 44;
	private static final int THUMB_H = 32;
	private static final float SLIDE_MS = 260.0f;

	private enum Button { OPEN, COPY, FOLDER }

	private static @Nullable File file;
	private static int imageW;
	private static int imageH;
	private static boolean thumbReady;
	private static long shownAt;
	// how long it has been on screen, not counting time with the mouse over it
	private static float held;
	private static long lastFrame;
	private static long leavingAt = -1;
	private static @Nullable Component status;
	private static long statusAt;
	private static int mouseX = -1;
	private static int mouseY = -1;

	public static void register() {
		HudElementRegistry.addLast(ID, new ScreenshotToast());
	}

	public static boolean enabled() {
		return ChatToolkitConfig.get().screenshotToast;
	}

	// Called on the main thread once the screenshot file is written
	public static void show(File saved) {
		Minecraft mc = Minecraft.getInstance();
		file = saved;
		thumbReady = false;
		shownAt = System.currentTimeMillis();
		lastFrame = shownAt;
		held = 0;
		leavingAt = -1;
		status = null;
		if (!ChatToolkitConfig.get().screenshotMuteSound) {
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_IN, 1.0f, 1.0f));
		}
		// read the picture off the main thread, then upload it as the thumbnail
		Util.ioPool().execute(() -> {
			try (InputStream in = Files.newInputStream(saved.toPath())) {
				NativeImage image = NativeImage.read(in);
				mc.execute(() -> {
					if (file != saved) {
						image.close();
						return;
					}
					imageW = image.getWidth();
					imageH = image.getHeight();
					mc.getTextureManager().register(THUMB, new DynamicTexture(() -> "Chat Toolkit screenshot", image));
					thumbReady = true;
				});
			} catch (IOException e) {
				ChatToolkit.LOGGER.warn("Couldn't load the screenshot thumbnail", e);
			}
		});
	}

	private static boolean chatOpen() {
		return Minecraft.getInstance().gui.screen() instanceof ChatScreen;
	}

	// slide offset 0 (fully in) .. 1 (fully out), or -1 when finished
	private static float slide() {
		long now = System.currentTimeMillis();
		if (leavingAt >= 0) {
			float t = Anim.clamp01((now - leavingAt) / SLIDE_MS);
			return t >= 1 ? -1 : Anim.easeInOutSine(t);
		}
		return 1.0f - Anim.easeOutCubic(Anim.clamp01((now - shownAt) / SLIDE_MS));
	}

	private static int left(int screenW, float slide) {
		return screenW - W - MARGIN + Math.round(slide * (W + MARGIN + 2));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		File current = file;
		if (current == null) {
			return;
		}
		long now = System.currentTimeMillis();
		Minecraft mc = Minecraft.getInstance();
		int sw = g.guiWidth();
		boolean hovered = chatOpen() && contains(mouseX, mouseY, sw);
		if (leavingAt < 0) {
			if (!hovered) {
				held += now - lastFrame;
			}
			if (held >= ChatToolkitConfig.get().screenshotToastSeconds * 1000.0f) {
				leavingAt = now;
			}
		}
		lastFrame = now;
		float slide = slide();
		if (slide < 0) {
			dismiss();
			return;
		}

		Font font = mc.font;
		int x = left(sw, slide);
		int y = MARGIN;
		g.nextStratum();
		g.fill(x, y, x + W, y + H, Draw.rgba(0x101012, 0.92f));
		Draw.outline(g, x, y, x + W, y + H, Draw.rgba(Draw.WHITE, 0.2f));

		// thumbnail, cropped to fill its box
		int tx = x + 4;
		int ty = y + 4;
		g.fill(tx, ty, tx + THUMB_W, ty + THUMB_H, 0xFF000000);
		if (thumbReady && imageW > 0 && imageH > 0) {
			float boxAspect = (float) THUMB_W / THUMB_H;
			float imgAspect = (float) imageW / imageH;
			int srcW = imageW;
			int srcH = imageH;
			if (imgAspect > boxAspect) {
				srcW = Math.round(imageH * boxAspect);
			} else {
				srcH = Math.round(imageW / boxAspect);
			}
			float u = (imageW - srcW) / 2.0f;
			float v = (imageH - srcH) / 2.0f;
			g.blit(RenderPipelines.GUI_TEXTURED, THUMB, tx, ty, u, v, THUMB_W, THUMB_H, srcW, srcH, imageW, imageH);
		}

		int textX = x + THUMB_W + 9;
		Component title = status != null && now - statusAt < 1500 ? status : Component.translatable("chattoolkit.screenshot.saved");
		g.text(font, title, textX, y + 5, Draw.rgba(Draw.TEXT, 1), false);
		String name = font.plainSubstrByWidth(current.getName(), W - THUMB_W - 14);
		g.text(font, Component.literal(name), textX, y + 15, Draw.rgba(0x828282, 1), false);

		int bx = textX;
		for (Button button : Button.values()) {
			Component label = label(button);
			int bw = font.width(label) + 6;
			boolean over = hovered && mouseX >= bx && mouseX < bx + bw && mouseY >= y + 26 && mouseY < y + 36;
			g.fill(bx, y + 26, bx + bw, y + 36, Draw.rgba(over ? Draw.accent() : Draw.WHITE, over ? 0.35f : 0.16f));
			g.text(font, label, bx + 3, y + 27, Draw.rgba(0xDCDCDC, 1), false);
			bx += bw + 3;
		}

		if (leavingAt < 0) {
			float left = 1.0f - held / (ChatToolkitConfig.get().screenshotToastSeconds * 1000.0f);
			g.fill(x, y + H - 1, x + Math.round(W * Anim.clamp01(left)), y + H, Draw.rgba(Draw.WHITE, 0.45f));
		}
	}

	private static Component label(Button button) {
		return Component.translatable("chattoolkit.screenshot." + button.name().toLowerCase(java.util.Locale.ROOT));
	}

	private static boolean contains(double mx, double my, int sw) {
		if (file == null || leavingAt >= 0) {
			return false;
		}
		int x = left(sw, Math.max(0, slide()));
		return mx >= x && mx < x + W && my >= MARGIN && my < MARGIN + H;
	}

	public static void setMouse(int x, int y) {
		mouseX = x;
		mouseY = y;
	}

	public static boolean contains(double mx, double my) {
		return contains(mx, my, Minecraft.getInstance().getWindow().getGuiScaledWidth());
	}

	// Clicks while the chat is open. Returns true if the click hit the card
	public static boolean mouseClicked(double mx, double my) {
		Minecraft mc = Minecraft.getInstance();
		int sw = mc.getWindow().getGuiScaledWidth();
		File current = file;
		if (current == null || !contains(mx, my, sw)) {
			return false;
		}
		Font font = mc.font;
		int x = left(sw, Math.max(0, slide()));
		int bx = x + THUMB_W + 9;
		for (Button button : Button.values()) {
			int bw = font.width(label(button)) + 6;
			if (mx >= bx && mx < bx + bw && my >= MARGIN + 26 && my < MARGIN + 36) {
				press(button, current);
				return true;
			}
			bx += bw + 3;
		}
		return true;
	}

	private static void press(Button button, File current) {
		Minecraft mc = Minecraft.getInstance();
		mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.6f, 0.35f));
		switch (button) {
			case OPEN -> Blaze3D.openPath(current.toPath());
			case FOLDER -> Blaze3D.openPath(current.getParentFile().toPath());
			case COPY -> {
				boolean copied;
				try {
					copied = ImageClipboard.copyPng(Files.readAllBytes(current.toPath()));
				} catch (IOException e) {
					copied = false;
				}
				if (!copied) {
					// at least give them the file's location
					mc.keyboardHandler.setClipboard(current.getAbsolutePath());
				}
				status = Component.translatable(copied ? "chattoolkit.screenshot.copied" : "chattoolkit.screenshot.copied_path");
				statusAt = System.currentTimeMillis();
				held = Math.min(held, 1000);
			}
		}
	}

	private static void dismiss() {
		file = null;
		if (thumbReady) {
			Minecraft.getInstance().getTextureManager().release(THUMB);
			thumbReady = false;
		}
	}
}
