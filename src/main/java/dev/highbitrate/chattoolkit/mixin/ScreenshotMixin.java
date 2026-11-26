package dev.highbitrate.chattoolkit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.highbitrate.chattoolkit.screenshot.ScreenshotToast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.io.File;
import java.util.function.Consumer;

// When you press F2, show our card instead of the "Saved screenshot as ..." chat line.
// Failures still go to chat so you know something went wrong.
@Mixin(Screenshot.class)
public abstract class ScreenshotMixin {
	@WrapOperation(
		method = "grab(Lnet/minecraft/client/Minecraft;Z)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/Screenshot;grab(Ljava/io/File;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V"
		),
		require = 0
	)
	private static void chattoolkit$toastInstead(File workDir, RenderTarget target, Consumer<Component> callback, Operation<Void> original) {
		Minecraft mc = Minecraft.getInstance();
		original.call(workDir, target, (Consumer<Component>) message -> {
			File saved = ScreenshotToast.enabled() ? chattoolkit$savedFile(message) : null;
			if (saved != null) {
				mc.execute(() -> ScreenshotToast.show(saved));
			} else {
				callback.accept(message);
			}
		});
	}

	// "Saved screenshot as <file>": the file name part carries a click event that opens it
	@Unique
	private static @Nullable File chattoolkit$savedFile(Component message) {
		if (!(message.getContents() instanceof TranslatableContents contents) || !"screenshot.success".equals(contents.getKey())) {
			return null;
		}
		for (Object arg : contents.getArgs()) {
			if (arg instanceof Component part && part.getStyle().getClickEvent() instanceof ClickEvent.OpenFile open) {
				return new File(open.path());
			}
		}
		return null;
	}
}
