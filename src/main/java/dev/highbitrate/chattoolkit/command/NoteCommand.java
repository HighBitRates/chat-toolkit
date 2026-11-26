package dev.highbitrate.chattoolkit.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.highbitrate.chattoolkit.chat.ChatHelper;
import dev.highbitrate.chattoolkit.config.ChatToolkitConfig;
import dev.highbitrate.chattoolkit.notes.Note;
import dev.highbitrate.chattoolkit.notes.NoteStore;
import dev.highbitrate.chattoolkit.text.TextFormatter;
import dev.highbitrate.chattoolkit.ui.Draw;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// /cnote prints a formatted note only you can see (like Client Commands' version, but with colours,
// markdown, links and a save button). /cnotes keeps notes between sessions, pins them to the HUD,
// and can stamp them with your coordinates
public final class NoteCommand {
	private static final int PAGE_SIZE = 8;
	private static final int PREVIEW_LENGTH = 60;

	public static String sayUsage = "cnote";

	private NoteCommand() {
	}

	// /cnote <text>

	public static LiteralArgumentBuilder<FabricClientCommandSource> buildSay(String name) {
		return literal(name)
			.executes(ctx -> sayHelp(ctx.getSource()))
			.then(argument("text", StringArgumentType.greedyString()).executes(ctx -> say(ctx.getSource(), StringArgumentType.getString(ctx, "text"))));
	}

	public static int say(FabricClientCommandSource source, String text) {
		String time = time(System.currentTimeMillis());
		Component hover = Component.translatable("chattoolkit.note.hover", time);
		MutableComponent line = TextFormatter.parse(text).withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(hover)))
			.append(Msg.space())
			.append(Msg.button(Component.translatable("chattoolkit.note.save"),
				new ClickEvent.RunCommand("cnotes add " + text),
				Component.translatable("chattoolkit.note.save.hover")));
		source.sendFeedback(line);
		return Command.SINGLE_SUCCESS;
	}

	private static int sayHelp(FabricClientCommandSource source) {
		MutableComponent msg = Component.empty().append(Msg.tr("chattoolkit.note.help.title", Msg.WHITE).withStyle(s -> s.withBold(true)));
		msg.append("\n  ").append(example("/" + sayUsage + " &aGreen &#FF8800orange **bold** *italic* __underline__ ~~strike~~ ||magic||"));
		msg.append("\n  ").append(Msg.tr("chattoolkit.note.help.links", Msg.GRAY));
		msg.append("\n  ").append(Msg.tr("chattoolkit.note.help.saved", Msg.GRAY)).append(Msg.space()).append(example("/cnotes"));
		source.sendFeedback(msg);
		return Command.SINGLE_SUCCESS;
	}

	// /cnotes ...

	public static LiteralArgumentBuilder<FabricClientCommandSource> buildManage(String name) {
		return literal(name)
			.executes(ctx -> list(ctx.getSource(), 1, null))
			.then(literal("list")
				.executes(ctx -> list(ctx.getSource(), 1, null))
				.then(argument("page", IntegerArgumentType.integer(1)).executes(ctx -> list(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page"), null))))
			.then(literal("add").then(argument("text", StringArgumentType.greedyString())
				.executes(ctx -> add(ctx.getSource(), StringArgumentType.getString(ctx, "text"), false))))
			.then(literal("here")
				.executes(ctx -> add(ctx.getSource(), "", true))
				.then(argument("text", StringArgumentType.greedyString()).executes(ctx -> add(ctx.getSource(), StringArgumentType.getString(ctx, "text"), true))))
			.then(literal("remove").then(idArgument().executes(ctx -> remove(ctx.getSource(), id(ctx)))))
			.then(literal("delete").then(idArgument().executes(ctx -> remove(ctx.getSource(), id(ctx)))))
			.then(literal("pin").then(idArgument().executes(ctx -> pin(ctx.getSource(), id(ctx), true))))
			.then(literal("unpin").then(idArgument().executes(ctx -> pin(ctx.getSource(), id(ctx), false))))
			.then(literal("copy").then(idArgument().executes(ctx -> copy(ctx.getSource(), id(ctx)))))
			.then(literal("show").then(idArgument().executes(ctx -> show(ctx.getSource(), id(ctx)))))
			.then(literal("edit").then(idArgument()
				.executes(ctx -> editPrompt(ctx.getSource(), id(ctx)))
				.then(argument("text", StringArgumentType.greedyString()).executes(ctx -> edit(ctx.getSource(), id(ctx), StringArgumentType.getString(ctx, "text"))))))
			.then(literal("search").then(argument("query", StringArgumentType.greedyString())
				.executes(ctx -> list(ctx.getSource(), 1, StringArgumentType.getString(ctx, "query")))))
			.then(literal("clear")
				.executes(ctx -> clearPrompt(ctx.getSource()))
				.then(literal("confirm").executes(ctx -> clear(ctx.getSource()))));
	}

	private static RequiredArgumentBuilder<FabricClientCommandSource, Integer> idArgument() {
		return argument("id", IntegerArgumentType.integer(1)).suggests(NoteCommand::suggestIds);
	}

	private static int id(CommandContext<FabricClientCommandSource> ctx) {
		return IntegerArgumentType.getInteger(ctx, "id");
	}

	private static CompletableFuture<Suggestions> suggestIds(CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
		for (Note note : NoteStore.get().all()) {
			String preview = TextFormatter.stripCodes(note.text);
			builder.suggest(note.id, Component.literal(preview.length() > 40 ? preview.substring(0, 40) + "..." : preview));
		}
		return builder.buildFuture();
	}

	private static int add(FabricClientCommandSource source, String text, boolean withPosition) {
		String coords = null;
		String dimension = null;
		if (withPosition) {
			LocalPlayer player = Minecraft.getInstance().player;
			if (player != null) {
				coords = player.getBlockX() + " " + player.getBlockY() + " " + player.getBlockZ();
				dimension = player.level().dimension().identifier().toString();
			}
			if (text.isBlank()) {
				text = coords != null ? "&7Location " + coords : "";
			}
		}
		if (text.isBlank()) {
			source.sendError(Component.translatable("chattoolkit.note.empty"));
			return 0;
		}
		Note note = NoteStore.get().add(text, coords, dimension);
		MutableComponent msg = Component.empty()
			.append(Msg.tr("chattoolkit.note.saved", Msg.GRAY, note.id))
			.append(Msg.space())
			.append(TextFormatter.parse(text))
			.append(Msg.space())
			.append(Msg.button("chattoolkit.note.pin", new ClickEvent.RunCommand("cnotes pin " + note.id), "chattoolkit.note.pin.hover"))
			.append(Msg.space())
			.append(Msg.button("chattoolkit.note.undo", new ClickEvent.RunCommand("cnotes remove " + note.id), "chattoolkit.note.undo.hover"));
		source.sendFeedback(msg);
		return note.id;
	}

	private static int remove(FabricClientCommandSource source, int id) {
		Note note = NoteStore.get().byId(id);
		if (note == null || !NoteStore.get().remove(id)) {
			source.sendError(Component.translatable("chattoolkit.note.unknown", id));
			return 0;
		}
		source.sendFeedback(Component.empty().append(Msg.tr("chattoolkit.note.removed", Msg.GRAY, id)).append(Msg.space())
			.append(Msg.button("chattoolkit.note.restore", new ClickEvent.SuggestCommand("/cnotes add " + note.text), "chattoolkit.note.restore.hover")));
		return Command.SINGLE_SUCCESS;
	}

	private static int pin(FabricClientCommandSource source, int id, boolean pinned) {
		if (!NoteStore.get().setPinned(id, pinned)) {
			source.sendError(Component.translatable("chattoolkit.note.unknown", id));
			return 0;
		}
		String key = pinned
			? (ChatToolkitConfig.get().pinnedHud ? "chattoolkit.note.pinned" : "chattoolkit.note.pinned_hud_off")
			: "chattoolkit.note.unpinned";
		source.sendFeedback(Component.empty().append(Msg.tr(key, Msg.GRAY, id)));
		return Command.SINGLE_SUCCESS;
	}

	private static int copy(FabricClientCommandSource source, int id) {
		Note note = NoteStore.get().byId(id);
		if (note == null) {
			source.sendError(Component.translatable("chattoolkit.note.unknown", id));
			return 0;
		}
		ChatHelper.copyToClipboard(TextFormatter.stripCodes(note.text));
		source.sendFeedback(Component.empty().append(Msg.tr("chattoolkit.note.copied", Msg.GRAY, id)));
		return Command.SINGLE_SUCCESS;
	}

	private static int show(FabricClientCommandSource source, int id) {
		Note note = NoteStore.get().byId(id);
		if (note == null) {
			source.sendError(Component.translatable("chattoolkit.note.unknown", id));
			return 0;
		}
		source.sendFeedback(noteLine(note, true));
		return Command.SINGLE_SUCCESS;
	}

	private static int editPrompt(FabricClientCommandSource source, int id) {
		Note note = NoteStore.get().byId(id);
		if (note == null) {
			source.sendError(Component.translatable("chattoolkit.note.unknown", id));
			return 0;
		}
		source.sendFeedback(Component.empty()
			.append(Msg.tr("chattoolkit.note.edit.prompt", Msg.GRAY, id))
			.append(Msg.space())
			.append(Msg.button("chattoolkit.note.edit", new ClickEvent.SuggestCommand("/cnotes edit " + id + " " + note.text), "chattoolkit.note.edit.hover")));
		return Command.SINGLE_SUCCESS;
	}

	private static int edit(FabricClientCommandSource source, int id, String text) {
		if (!NoteStore.get().setText(id, text)) {
			source.sendError(Component.translatable("chattoolkit.note.unknown", id));
			return 0;
		}
		source.sendFeedback(Component.empty().append(Msg.tr("chattoolkit.note.edited", Msg.GRAY, id)).append(Msg.space()).append(TextFormatter.parse(text)));
		return Command.SINGLE_SUCCESS;
	}

	private static int clearPrompt(FabricClientCommandSource source) {
		int count = NoteStore.get().size();
		if (count == 0) {
			source.sendFeedback(Component.empty().append(Msg.tr("chattoolkit.note.none", Msg.GRAY)));
			return 0;
		}
		source.sendFeedback(Component.empty()
			.append(Msg.tr("chattoolkit.note.clear.confirm", Msg.GRAY, count))
			.append(Msg.space())
			.append(Msg.button(Component.translatable("chattoolkit.note.clear.yes").withColor(Msg.RED),
				new ClickEvent.RunCommand("cnotes clear confirm"), Component.translatable("chattoolkit.note.clear.hover"))));
		return Command.SINGLE_SUCCESS;
	}

	private static int clear(FabricClientCommandSource source) {
		int count = NoteStore.get().clear();
		source.sendFeedback(Component.empty().append(Msg.tr("chattoolkit.note.cleared", Msg.GRAY, count)));
		return count;
	}

	private static int list(FabricClientCommandSource source, int page, String query) {
		List<Note> notes = query == null ? NoteStore.get().all() : NoteStore.get().search(query);
		if (notes.isEmpty()) {
			MutableComponent msg = Component.empty().append(Msg.tr(query == null ? "chattoolkit.note.none" : "chattoolkit.note.no_match", Msg.GRAY, query));
			if (query == null) {
				msg.append(Msg.space()).append(example("/cnotes add Remember to restock the furnace"));
			}
			source.sendFeedback(msg);
			return 0;
		}
		int pages = (notes.size() + PAGE_SIZE - 1) / PAGE_SIZE;
		page = Math.min(page, pages);
		MutableComponent msg = Component.empty()
			.append(Msg.tr(query == null ? "chattoolkit.note.list.title" : "chattoolkit.note.list.search", Msg.WHITE, notes.size(), query).withStyle(s -> s.withBold(true)));
		if (pages > 1) {
			msg.append(Msg.tr("chattoolkit.note.list.page", Msg.DARK_GRAY, page, pages));
		}
		int from = (page - 1) * PAGE_SIZE;
		for (int i = from; i < Math.min(notes.size(), from + PAGE_SIZE); i++) {
			msg.append("\n ").append(noteLine(notes.get(i), false));
		}
		if (pages > 1 && query == null) {
			msg.append("\n ");
			if (page > 1) {
				msg.append(Msg.button(Component.translatable("chattoolkit.note.list.prev"),
					new ClickEvent.RunCommand("cnotes list " + (page - 1)), Component.translatable("chattoolkit.note.list.go", page - 1))).append(Msg.space());
			}
			if (page < pages) {
				msg.append(Msg.button(Component.translatable("chattoolkit.note.list.next"),
					new ClickEvent.RunCommand("cnotes list " + (page + 1)), Component.translatable("chattoolkit.note.list.go", page + 1)));
			}
		}
		source.sendFeedback(msg);
		return notes.size();
	}

	private static MutableComponent noteLine(Note note, boolean full) {
		MutableComponent hover = Component.empty()
			.append(Component.translatable("chattoolkit.note.info.id", note.id).withColor(Draw.accent()))
			.append("\n").append(Component.translatable("chattoolkit.note.info.created", dateTime(note.created)).withColor(Msg.GRAY))
			.append("\n").append(Component.translatable("chattoolkit.note.info.origin", note.origin).withColor(Msg.GRAY));
		if (note.coords != null) {
			hover.append("\n").append(Component.translatable("chattoolkit.note.info.coords", note.coords, prettyDimension(note.dimension)).withColor(Msg.GRAY));
		}
		hover.append("\n\n").append(TextFormatter.parse(note.text));

		String plain = TextFormatter.stripCodes(note.text);
		MutableComponent text = !full && plain.length() > PREVIEW_LENGTH
			? Component.literal(plain.substring(0, PREVIEW_LENGTH).stripTrailing() + "...")
			: TextFormatter.parse(note.text);

		MutableComponent line = Component.literal("#" + note.id + " ").setStyle(Style.EMPTY
				.withColor(note.pinned ? Draw.accent() : Msg.DARK_GRAY)
				.withHoverEvent(new HoverEvent.ShowText(hover)))
			.append(text.withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(hover))));
		if (note.coords != null) {
			line.append(Msg.space()).append(Msg.copyable(note.coords, note.coords, Msg.DARK_GRAY, false));
		}
		line.append(Msg.space())
			.append(Msg.button(Component.translatable(note.pinned ? "chattoolkit.note.unpin" : "chattoolkit.note.pin"),
				new ClickEvent.RunCommand("cnotes " + (note.pinned ? "unpin " : "pin ") + note.id),
				Component.translatable(note.pinned ? "chattoolkit.note.unpin.hover" : "chattoolkit.note.pin.hover")))
			.append(Msg.button(Component.translatable("chattoolkit.note.copy"), new ClickEvent.CopyToClipboard(plain), Component.translatable("chattoolkit.note.copy.hover")))
			.append(Msg.button(Component.translatable("chattoolkit.note.edit"), new ClickEvent.SuggestCommand("/cnotes edit " + note.id + " " + note.text), Component.translatable("chattoolkit.note.edit.hover")))
			.append(Msg.button(Component.translatable("chattoolkit.note.delete").withColor(0xD86B6B), new ClickEvent.RunCommand("cnotes remove " + note.id), Component.translatable("chattoolkit.note.remove.hover")));
		return line;
	}

	private static MutableComponent example(String command) {
		return Component.literal(command).setStyle(Style.EMPTY
			.withColor(Draw.accent())
			.withClickEvent(new ClickEvent.SuggestCommand(command))
			.withHoverEvent(new HoverEvent.ShowText(Component.translatable("chattoolkit.try_it"))));
	}

	private static String prettyDimension(String dimension) {
		if (dimension == null) {
			return "?";
		}
		return switch (dimension) {
			case "minecraft:overworld" -> "Overworld";
			case "minecraft:the_nether" -> "Nether";
			case "minecraft:the_end" -> "End";
			default -> dimension;
		};
	}

	static String time(long millis) {
		String pattern = ChatToolkitConfig.get().clock24h ? "HH:mm:ss" : "h:mm:ss a";
		return DateTimeFormatter.ofPattern(pattern, Locale.ROOT).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis));
	}

	static String dateTime(long millis) {
		String pattern = ChatToolkitConfig.get().clock24h ? "yyyy-MM-dd HH:mm" : "MMM d, yyyy h:mm a";
		return DateTimeFormatter.ofPattern(pattern, Locale.ROOT).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis));
	}
}
