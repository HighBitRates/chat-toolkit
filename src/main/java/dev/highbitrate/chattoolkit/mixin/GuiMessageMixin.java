package dev.highbitrate.chattoolkit.mixin;

import dev.highbitrate.chattoolkit.chat.MessageExtras;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Gives every chat message a wall-clock timestamp, a cached plain-text copy (used by search),
// its tab mask and, for stacked repeats, the original text and count
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
	@Unique
	private @Nullable Component chattoolkit$stackBase;
	@Unique
	private int chattoolkit$stackCount = 1;
	@Unique
	private long chattoolkit$stackFirst;

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
			plain = MessageExtras.content((GuiMessage) (Object) this).getString();
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

	@Override
	public @Nullable Component chattoolkit$stackBase() {
		return this.chattoolkit$stackBase;
	}

	@Override
	public int chattoolkit$stackCount() {
		return this.chattoolkit$stackCount;
	}

	@Override
	public long chattoolkit$stackFirst() {
		return this.chattoolkit$stackFirst != 0 ? this.chattoolkit$stackFirst : this.chattoolkit$receivedAt;
	}

	@Override
	public void chattoolkit$setStack(Component base, int count, long first) {
		this.chattoolkit$stackBase = base;
		this.chattoolkit$stackCount = count;
		this.chattoolkit$stackFirst = first;
		this.chattoolkit$plain = null;
	}
}
