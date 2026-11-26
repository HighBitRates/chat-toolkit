package dev.highbitrate.chattoolkit.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.highbitrate.chattoolkit.ChatToolkit;
import dev.highbitrate.chattoolkit.config.ChatToolkitConfig;
import dev.highbitrate.chattoolkit.config.ConfigScreen;
import dev.highbitrate.chattoolkit.notes.NoteStore;
import dev.highbitrate.chattoolkit.ui.Draw;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Field;
import java.util.Map;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// Registers every command. Runs after other mods so that, when Client Commands is installed,
// our better /ccalc and /cnote can replace its versions (configurable)
public final class ToolkitCommands {
	private static final Identifier LATE_PHASE = Identifier.fromNamespaceAndPath(ChatToolkit.MOD_ID, "after_other_mods");
	private static final String CLIENT_COMMANDS_ID = "clientcommands";

	private ToolkitCommands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, LATE_PHASE);
		ClientCommandRegistrationCallback.EVENT.register(LATE_PHASE, (dispatcher, buildContext) -> registerAll(dispatcher));
	}

	public static boolean clientCommandsInstalled() {
		return FabricLoader.getInstance().isModLoaded(CLIENT_COMMANDS_ID);
	}

	// True when our /ccalc and /cnote are the ones in use
	public static boolean ownsCalcAndNote() {
		return !clientCommandsInstalled() || ChatToolkitConfig.get().overrideClientCommands;
	}

	private static void registerAll(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		if (ownsCalcAndNote()) {
			if (clientCommandsInstalled()) {
				removeRootCommand(dispatcher, "ccalc");
				removeRootCommand(dispatcher, "cnote");
			}
			dispatcher.register(CalcCommand.build("ccalc"));
			dispatcher.register(NoteCommand.buildSay("cnote"));
			CalcCommand.usage = "ccalc";
			NoteCommand.sayUsage = "cnote";
		} else {
			CalcCommand.usage = "ctk calc";
			NoteCommand.sayUsage = "ctk note";
		}
		dispatcher.register(NoteCommand.buildManage("cnotes"));
		dispatcher.register(TimerCommand.build("ctimer"));
		dispatcher.register(CoordsCommand.build("ccoords"));

		LiteralCommandNode<FabricClientCommandSource> root = dispatcher.register(literal("chattoolkit")
			.executes(ctx -> openSettings())
			.then(literal("settings").executes(ctx -> openSettings()))
			.then(literal("help").executes(ctx -> help(ctx.getSource())))
			.then(literal("reload").executes(ctx -> reload(ctx.getSource())))
			.then(literal("calc")
				.then(argument("expression", StringArgumentType.greedyString())
					.executes(ctx -> CalcCommand.evaluate(ctx.getSource(), StringArgumentType.getString(ctx, "expression")))))
			.then(literal("note")
				.then(argument("text", StringArgumentType.greedyString())
					.executes(ctx -> NoteCommand.say(ctx.getSource(), StringArgumentType.getString(ctx, "text"))))));
		dispatcher.register(literal("ctk").executes(ctx -> openSettings()).redirect(root));
	}

	// Brigadier can't remove nodes, so we reach into its maps. Only used to replace Client Commands' versions
	@SuppressWarnings("unchecked")
	private static void removeRootCommand(CommandDispatcher<FabricClientCommandSource> dispatcher, String name) {
		CommandNode<FabricClientCommandSource> root = dispatcher.getRoot();
		if (root.getChild(name) == null) {
			return;
		}
		try {
			for (String fieldName : new String[]{"children", "literals", "arguments"}) {
				Field field = CommandNode.class.getDeclaredField(fieldName);
				field.setAccessible(true);
				((Map<String, ?>) field.get(root)).remove(name);
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			ChatToolkit.LOGGER.warn("Couldn't replace /{} from Client Commands; both versions will be merged", name, e);
		}
	}

	private static int openSettings() {
		ChatToolkit.openScreenNextTick(() -> new ConfigScreen(null));
		return Command.SINGLE_SUCCESS;
	}

	private static int reload(FabricClientCommandSource source) {
		ChatToolkitConfig.load();
		NoteStore.get().load();
		source.sendFeedback(Component.empty().append(Msg.tr("chattoolkit.reloaded", Msg.GRAY)));
		return Command.SINGLE_SUCCESS;
	}

	private static int help(FabricClientCommandSource source) {
		String calc = CalcCommand.usage;
		String note = NoteCommand.sayUsage;
		MutableComponent msg = Component.literal("Chat Toolkit").withStyle(s -> s.withColor(Draw.accent()).withBold(true))
			.append(Msg.text(" v" + ChatToolkit.version(), Msg.DARK_GRAY));
		msg.append("\n").append(feature("chattoolkit.help.search"));
		msg.append("\n").append(feature("chattoolkit.help.menu"));
		msg.append("\n").append(command("/" + calc + " ", "chattoolkit.help.calc"));
		msg.append("\n").append(command("/" + note + " ", "chattoolkit.help.note"));
		msg.append("\n").append(command("/cnotes", "chattoolkit.help.notes"));
		msg.append("\n").append(command("/ctimer ", "chattoolkit.help.timer"));
		msg.append("\n").append(command("/ccoords", "chattoolkit.help.coords"));
		msg.append("\n").append(command("/ctk", "chattoolkit.help.settings"));
		if (clientCommandsInstalled()) {
			msg.append("\n").append(Msg.tr(ownsCalcAndNote() ? "chattoolkit.help.cc_replaced" : "chattoolkit.help.cc_kept", Msg.DARK_GRAY));
		}
		source.sendFeedback(msg);
		return Command.SINGLE_SUCCESS;
	}

	private static MutableComponent feature(String key) {
		return Component.literal(" - ").withColor(Draw.accent()).append(Component.translatable(key).withColor(Msg.GRAY));
	}

	private static MutableComponent command(String command, String key) {
		return Component.literal(" - ").withColor(Draw.accent())
			.append(Component.literal(command.strip()).setStyle(Style.EMPTY
				.withColor(Msg.WHITE)
				.withClickEvent(new ClickEvent.SuggestCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.translatable("chattoolkit.try_it")))))
			.append(Msg.text(" - ", Msg.DARK_GRAY))
			.append(Component.translatable(key).withColor(Msg.GRAY));
	}

	static Minecraft mc() {
		return Minecraft.getInstance();
	}
}
