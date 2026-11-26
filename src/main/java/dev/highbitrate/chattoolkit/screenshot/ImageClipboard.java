package dev.highbitrate.chattoolkit.screenshot;

import dev.highbitrate.chattoolkit.ChatToolkit;
import org.lwjgl.PointerBuffer;
import org.lwjgl.sdl.SDLClipboard;
import org.lwjgl.sdl.SDL_ClipboardCleanupCallback;
import org.lwjgl.sdl.SDL_ClipboardDataCallback;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

// Puts a picture on the system clipboard through SDL (the game's own window library), so it can be
// pasted straight into Discord and the like. Offered as PNG, and as BMP for Windows apps that want that.
public final class ImageClipboard {
	private static final String PNG = "image/png";
	private static final String BMP = "image/bmp";

	// SDL asks for the data whenever someone pastes, so it has to stay around until it's replaced
	private static final class Held {
		final byte[] png;
		ByteBuffer pngBuffer;
		ByteBuffer bmpBuffer;

		Held(byte[] png) {
			this.png = png;
		}

		long data(String mime, long sizeOut) {
			ByteBuffer buffer;
			if (BMP.equals(mime)) {
				if (bmpBuffer == null) {
					byte[] bmp = toBmp(png);
					if (bmp == null) {
						return 0;
					}
					bmpBuffer = MemoryUtil.memAlloc(bmp.length).put(bmp).flip();
				}
				buffer = bmpBuffer;
			} else {
				if (pngBuffer == null) {
					pngBuffer = MemoryUtil.memAlloc(png.length).put(png).flip();
				}
				buffer = pngBuffer;
			}
			MemoryUtil.memPutAddress(sizeOut, buffer.remaining());
			return MemoryUtil.memAddress(buffer);
		}

		void free() {
			if (pngBuffer != null) {
				MemoryUtil.memFree(pngBuffer);
				pngBuffer = null;
			}
			if (bmpBuffer != null) {
				MemoryUtil.memFree(bmpBuffer);
				bmpBuffer = null;
			}
		}
	}

	private ImageClipboard() {
	}

	// Must be called on the main thread. Returns false if the system clipboard didn't take it
	public static boolean copyPng(byte[] png) {
		Held held = new Held(png);
		// these stay allocated for the life of the game; there's one pair per copy, which is tiny
		SDL_ClipboardDataCallback data = SDL_ClipboardDataCallback.create((userdata, mimeType, size) ->
			held.data(MemoryUtil.memUTF8Safe(mimeType), size));
		SDL_ClipboardCleanupCallback cleanup = SDL_ClipboardCleanupCallback.create(userdata -> held.free());
		try (MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer types = stack.mallocPointer(2);
			types.put(stack.UTF8(PNG)).put(stack.UTF8(BMP)).flip();
			if (SDLClipboard.SDL_SetClipboardData(data, cleanup, 0L, types)) {
				return true;
			}
		} catch (Throwable e) {
			ChatToolkit.LOGGER.warn("Couldn't copy the screenshot to the clipboard", e);
		}
		return false;
	}

	private static byte[] toBmp(byte[] png) {
		try {
			BufferedImage source = ImageIO.read(new ByteArrayInputStream(png));
			if (source == null) {
				return null;
			}
			// the BMP writer won't take an image with alpha
			BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
			rgb.getGraphics().drawImage(source, 0, 0, null);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(rgb, "bmp", out);
			return out.toByteArray();
		} catch (IOException | RuntimeException e) {
			ChatToolkit.LOGGER.warn("Couldn't convert the screenshot for the clipboard", e);
			return null;
		}
	}
}
