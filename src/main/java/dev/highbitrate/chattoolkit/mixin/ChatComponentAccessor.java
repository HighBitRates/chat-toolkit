package dev.highbitrate.chattoolkit.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
	@Accessor("allMessages")
	List<GuiMessage> chattoolkit$getAllMessages();

	@Accessor("trimmedMessages")
	List<GuiMessage.Line> chattoolkit$getTrimmedMessages();

	@Accessor("chatScrollbarPos")
	int chattoolkit$getScrollPos();

	@Accessor("chatScrollbarPos")
	void chattoolkit$setScrollPos(int pos);
}
