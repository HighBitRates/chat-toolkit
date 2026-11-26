package dev.highbitrate.chattoolkit.mixin;

import dev.highbitrate.chattoolkit.chat.MessageExtras;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Gives every chat message a wall-clock timestamp and a cached plain-text copy (used by search)
@Mixin(GuiMessage.class)
public abstract class GuiMessageMixin implements MessageExtras {
	@Unique
	private long chattoolkit$receivedAt;
	@Unique
	private String chattoolkit$plain;
	@Unique
	private int chattoolkit$tabMask;
	@Unique
	private int chattoolkit$tabVersion = -1;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void chattoolkit$stamp(CallbackInfo ci) {
		this.chattoolkit$receivedAt = System.currentTimeMillis();
	}

	@Override
	public long chattoolkit$receivedAt() {
		return this.chattoolkit$receivedAt;
	}

	@Override
	public String chattoolkit$plainText() {
		String plain = this.chattoolkit$plain;
		if (plain == null) {
			plain = ((GuiMessage) (Object) this).content().getString();
			this.chattoolkit$plain = plain;
		}
		return plain;
	}

	@Override
	public int chattoolkit$tabMask(int version) {
		return this.chattoolkit$tabVersion == version ? this.chattoolkit$tabMask : -1;
	}

	@Override
	public void chattoolkit$setTabMask(int version, int mask) {
		this.chattoolkit$tabVersion = version;
		this.chattoolkit$tabMask = mask;
	}
}
