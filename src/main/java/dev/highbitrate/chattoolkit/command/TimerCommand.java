package dev.highbitrate.chattoolkit.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.highbitrate.chattoolkit.timer.TimerManager;
import dev.highbitrate.chattoolkit.ui.Draw;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// /ctimer 5m Check the furnace. Pings you in chat (with a sound) when the time is up
public final class TimerCommand {
	private static final String[] EXAMPLES = {"30s", "1m", "2m30s", "5m", "10m", "20m", "1h"};

	private TimerCommand() {
	}

	public static LiteralArgumentBuilder<FabricClientCommandSource> build(String name) {
		return literal(name)
			.executes(ctx -> list(ctx.getSource()))
			.then(literal("list").executes(ctx -> list(ctx.getSource())))
			.then(literal("cancel")
				.then(literal("all").executes(ctx -> cancelAll(ctx.getSource())))
				.then(argument("id", IntegerArgumentType.integer(1)).suggests(TimerCommand::suggestIds)
					.executes(ctx -> cancel(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "id")))))
			.then(argument("duration", StringArgumentType.word()).suggests(TimerCommand::suggestDurations)
				.executes(ctx -> start(ctx.getSource(), StringArgumentType.getString(ctx, "duration"), ""))
				.then(argument("label", StringArgumentType.greedyString())
					.executes(ctx -> start(ctx.getSource(), StringArgumentType.getString(ctx, "duration"), StringArgumentType.getString(ctx, "label")))));
	}

	private static int start(FabricClientCommandSource source, String duration, String label) {
		long millis;
		try {
			millis = TimerManager.parseDuration(duration);
		} catch (IllegalArgumentException e) {
			source.sendError(Component.translatable("chattoolkit.timer.bad_duration", duration));
			return 0;
		}
		TimerManager.Timer timer = TimerManager.start(millis, label.strip());
		MutableComponent msg = Component.empty()
			.append(Msg.tr("chattoolkit.timer.started", Msg.GRAY, Component.literal(TimerManager.formatLong(millis)).withColor(Draw.accent())));
		if (!timer.label().isEmpty()) {
			msg.append(Msg.text(" - " + timer.label(), Msg.WHITE));
		}
		msg.append(Msg.space()).append(Msg.button("chattoolkit.timer.cancel", new ClickEvent.RunCommand("ctimer cancel " + timer.id()), "chattoolkit.timer.cancel.hover"));
		source.sendFeedback(msg);
		return timer.id();
	}

	private static int list(FabricClientCommandSource source) {
		List<TimerManager.Timer> timers = TimerManager.active();
		if (timers.isEmpty()) {
			source.sendFeedback(Component.empty().append(Msg.tr("chattoolkit.timer.none", Msg.GRAY))
				.append(Msg.space()).append(Component.literal("/ctimer 5m Check the furnace").withStyle(s -> s
					.withColor(Draw.accent())
					.withClickEvent(new ClickEvent.SuggestCommand("/ctimer 5m Check the furnace")))));
			return 0;
		}
		MutableComponent msg = Component.empty().append(Msg.tr("chattoolkit.timer.list", Msg.WHITE, timers.size()));
		for (TimerManager.Timer timer : timers) {
			msg.append("\n  ").append(Msg.text("#" + timer.id() + " ", Msg.DARK_GRAY))
				.append(Msg.text(TimerManager.formatClock(timer.remaining()), Draw.accent()))
				.append(Msg.text(timer.label().isEmpty() ? "" : "  " + timer.label(), Msg.WHITE))
				.append(Msg.space())
				.append(Msg.button("chattoolkit.timer.cancel", new ClickEvent.RunCommand("ctimer cancel " + timer.id()), "chattoolkit.timer.cancel.hover"));
		}
		source.sendFeedback(msg);
		return timers.size();
	}

	private static int cancel(FabricClientCommandSource source, int id) {
		if (!TimerManager.cancel(id)) {
			source.sendError(Component.translatable("chattoolkit.timer.unknown", id));
			return 0;
		}
		source.sendFeedback(Component.empty().append(Msg.tr("chattoolkit.timer.cancelled", Msg.GRAY, id)));
		return Command.SINGLE_SUCCESS;
	}

	private static int cancelAll(FabricClientCommandSource source) {
		int count = TimerManager.cancelAll();
		source.sendFeedback(Component.empty().append(Msg.tr("chattoolkit.timer.cancelled_all", Msg.GRAY, count)));
		return count;
	}

	private static CompletableFuture<Suggestions> suggestDurations(CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
		String typed = builder.getRemaining();
		for (String example : EXAMPLES) {
			if (example.startsWith(typed)) {
				builder.suggest(example);
			}
		}
		if (!typed.isEmpty() && typed.chars().allMatch(Character::isDigit)) {
			builder.suggest(typed + "s");
			builder.suggest(typed + "m");
		}
		return builder.buildFuture();
	}

	private static CompletableFuture<Suggestions> suggestIds(CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
		for (TimerManager.Timer timer : TimerManager.active()) {
			builder.suggest(timer.id(), Component.literal(TimerManager.formatClock(timer.remaining()) + " " + timer.label()));
		}
		return builder.buildFuture();
	}
}
