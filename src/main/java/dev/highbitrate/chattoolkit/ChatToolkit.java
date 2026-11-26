package dev.highbitrate.chattoolkit;

import com.mojang.blaze3d.platform.InputConstants;
import dev.highbitrate.chattoolkit.chat.ChatScreenController;
import dev.highbitrate.chattoolkit.command.ToolkitCommands;
import dev.highbitrate.chattoolkit.config.ChatToolkitConfig;
import dev.highbitrate.chattoolkit.config.ConfigScreen;
import dev.highbitrate.chattoolkit.hud.PinnedHud;
import dev.highbitrate.chattoolkit.tabs.TabStore;
import dev.highbitrate.chattoolkit.tabs.TabWindows;
import dev.highbitrate.chattoolkit.screenshot.ScreenshotToast;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.multiplayer.ServerData;
import dev.highbitrate.chattoolkit.notes.NoteStore;
import dev.highbitrate.chattoolkit.timer.TimerManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

public final class ChatToolkit implements ClientModInitializer {
	public static final String MOD_ID = "chattoolkit";
	public static final Logger LOGGER = LoggerFactory.getLogger("Chat Toolkit");

	private static @Nullable Supplier<Screen> pendingScreen;
	private static KeyMapping searchKey;
	private static KeyMapping settingsKey;

	@Override
	public void onInitializeClient() {
		ChatToolkitConfig.load();
		NoteStore.get().load();

		ChatScreenController.register();
		ToolkitCommands.register();
		PinnedHud.register();
		TabStore.load();
		TabWindows.register();
		ScreenshotToast.register();
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			ServerData server = client.getCurrentServer();
			TabStore.onJoin(server != null ? server.ip : null);
		});

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		int unbound = InputConstants.UNKNOWN.getValue();
		searchKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.chattoolkit.search_chat", InputConstants.Type.KEYBOARD, unbound, category));
		settingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.chattoolkit.settings", InputConstants.Type.KEYBOARD, unbound, category));

		ClientTickEvents.END_CLIENT_TICK.register(ChatToolkit::tick);
		LOGGER.info("Chat Toolkit {} ready", version());
	}

	private static void tick(Minecraft client) {
		TimerManager.tick();

		if (pendingScreen != null) {
			Supplier<Screen> supplier = pendingScreen;
			pendingScreen = null;
			client.gui.setScreen(supplier.get());
		}

		while (searchKey.consumeClick()) {
			if (client.player != null && client.gui.screen() == null) {
				ChatScreenController.requestSearchFocus();
				client.gui.openChatScreen(ChatComponent.ChatMethod.MESSAGE);
			}
		}
		while (settingsKey.consumeClick()) {
			if (client.gui.screen() == null) {
				client.gui.setScreen(new ConfigScreen(null));
			}
		}
	}

	// Opens a screen on the next tick (commands run while the chat screen is still closing)
	public static void openScreenNextTick(Supplier<Screen> screen) {
		pendingScreen = screen;
	}

	public static String version() {
		return FabricLoader.getInstance().getModContainer(MOD_ID)
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("dev");
	}
}
