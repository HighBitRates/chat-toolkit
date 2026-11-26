package dev.highbitrate.chattoolkit.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.highbitrate.chattoolkit.calc.CalcException;
import dev.highbitrate.chattoolkit.calc.CalcFormat;
import dev.highbitrate.chattoolkit.calc.Calculator;
import dev.highbitrate.chattoolkit.config.ChatToolkitConfig;
import dev.highbitrate.chattoolkit.ui.Draw;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// /ccalc: a calculator that gets the details right
public final class CalcCommand {
	public static final Calculator CALCULATOR = new Calculator();
	// The command people should type, e.g. "ccalc" or "ctk calc" when Client Commands keeps its own /ccalc
	public static String usage = "ccalc";

	private CalcCommand() {
	}

	public static LiteralArgumentBuilder<FabricClientCommandSource> build(String name) {
		return literal(name)
			.executes(ctx -> help(ctx.getSource(), name))
			.then(literal("help").executes(ctx -> help(ctx.getSource(), name)))
			.then(literal("vars").executes(ctx -> vars(ctx.getSource(), name)))
			.then(literal("history").executes(ctx -> history(ctx.getSource(), name)))
			.then(literal("clear").executes(ctx -> clear(ctx.getSource())))
			.then(argument("expression", StringArgumentType.greedyString())
				.suggests(CalcCommand::suggest)
				.executes(ctx -> evaluate(ctx.getSource(), StringArgumentType.getString(ctx, "expression"))));
	}

	public static int evaluate(FabricClientCommandSource source, String expression) {
		ChatToolkitConfig config = ChatToolkitConfig.get();
		CALCULATOR.setAngleUnit(config.calcAngles == ChatToolkitConfig.AngleMode.DEGREES ? Calculator.AngleUnit.DEGREES : Calculator.AngleUnit.RADIANS);
		try {
			Calculator.Result result = CALCULATOR.evaluate(expression);
			source.sendFeedback(resultLine(expression.strip(), result));
			double v = result.value();
			return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, Math.round(v)));
		} catch (CalcException e) {
			source.sendFeedback(errorLine(expression, e));
			return 0;
		}
	}

	private static MutableComponent resultLine(String expression, Calculator.Result result) {
		ChatToolkitConfig config = ChatToolkitConfig.get();
		double value = result.value();
		String pretty = CalcFormat.format(value, config.calcDecimals, config.calcSeparators);
		String plain = CalcFormat.format(value, config.calcDecimals, false);

		MutableComponent line = Component.empty();
		if (result.assignedTo() != null && expression.indexOf(';') < 0) {
			line.append(Msg.text(result.assignedTo(), Draw.accent()))
				.append(Msg.text(" = ", Msg.WHITE))
				.append(resultValue(pretty, plain))
				.append(Msg.text("  ", Msg.WHITE))
				.append(Msg.tr("chattoolkit.calc.saved", Msg.DARK_GRAY));
			return line;
		}
		line.append(Component.literal(expression).setStyle(Style.EMPTY
				.withColor(Msg.GRAY)
				.withClickEvent(new ClickEvent.SuggestCommand("/" + usage + " " + expression))
				.withHoverEvent(new HoverEvent.ShowText(Component.translatable("chattoolkit.calc.edit")))))
			.append(Msg.text(" = ", Msg.WHITE))
			.append(resultValue(pretty, plain));

		if (config.calcStacks && CalcFormat.isWhole(value) && value >= 64 && value <= 1e12) {
			line.append(Msg.text("  ", Msg.WHITE)).append(stackBreakdown((long) value));
		}
		return line;
	}

	private static MutableComponent resultValue(String pretty, String plain) {
		return Component.literal(pretty).setStyle(Style.EMPTY
			.withColor(Draw.accent())
			.withBold(true)
			.withInsertion(plain)
			.withClickEvent(new ClickEvent.CopyToClipboard(plain))
			.withHoverEvent(new HoverEvent.ShowText(Component.translatable("chattoolkit.calc.result.hover", plain))));
	}

	// "(3 SB + 2 st + 5)" with the long form and 16-stack counts on hover
	private static MutableComponent stackBreakdown(long count) {
		long[] parts = CalcFormat.stackParts(count, CalcFormat.STACK);
		StringBuilder shortForm = new StringBuilder();
		MutableComponent longForm = Component.empty();
		String[] shortUnits = {"SB", "st", ""};
		String[] longKeys = {"chattoolkit.calc.boxes", "chattoolkit.calc.stacks", "chattoolkit.calc.items"};
		boolean first = true;
		for (int i = 0; i < 3; i++) {
			if (parts[i] == 0) {
				continue;
			}
			if (!first) {
				shortForm.append(" + ");
				longForm.append(" + ");
			}
			shortForm.append(CalcFormat.group(Long.toString(parts[i]))).append(shortUnits[i].isEmpty() ? "" : " " + shortUnits[i]);
			longForm.append(Component.translatable(longKeys[i], CalcFormat.group(Long.toString(parts[i]))));
			first = false;
		}
		long[] sixteen = CalcFormat.stackParts(count, 16);
		String stacksDecimal = CalcFormat.format(count / 64.0, 2, true);
		Component hover = Component.empty()
			.append(Component.translatable("chattoolkit.calc.breakdown.title").withColor(Draw.accent()))
			.append("\n").append(longForm)
			.append("\n").append(Component.translatable("chattoolkit.calc.breakdown.decimal", stacksDecimal).withColor(Msg.GRAY))
			.append("\n").append(Component.translatable("chattoolkit.calc.breakdown.sixteen",
				CalcFormat.group(Long.toString(sixteen[0])), CalcFormat.group(Long.toString(sixteen[1])), sixteen[2]).withColor(Msg.GRAY));
		return Component.literal("(" + shortForm + ")").setStyle(Style.EMPTY
			.withColor(Msg.DARK_GRAY)
			.withHoverEvent(new HoverEvent.ShowText(hover)));
	}

	private static MutableComponent errorLine(String expression, CalcException e) {
		MutableComponent line = Component.empty();
		int pos = e.position();
		if (pos >= 0 && pos < expression.length()) {
			int end = pos + Character.charCount(expression.codePointAt(pos));
			line.append(Msg.text(expression.substring(0, pos), Msg.GRAY))
				.append(Component.literal(expression.substring(pos, end)).setStyle(Style.EMPTY.withColor(Msg.RED).withUnderlined(true).withBold(true)))
				.append(Msg.text(expression.substring(end), Msg.GRAY));
		} else {
			line.append(Msg.text(expression, Msg.GRAY));
			if (pos >= expression.length()) {
				line.append(Component.literal(" _").setStyle(Style.EMPTY.withColor(Msg.RED).withBold(true)));
			}
		}
		line.append("\n").append(Msg.text(e.getMessage(), Msg.RED));
		return line;
	}

	private static int help(FabricClientCommandSource source, String name) {
		Map<String, String> builtins = Calculator.describeBuiltins();
		MutableComponent msg = Component.empty().append(Msg.tr("chattoolkit.calc.help.title", Msg.WHITE).withStyle(s -> s.withBold(true)));
		msg.append("\n").append(helpLine("chattoolkit.calc.help.basics", usage + " 2^10 + 15%"));
		msg.append("\n").append(helpLine("chattoolkit.calc.help.minecraft", usage + " 3sb + 12st"));
		msg.append("\n").append(helpLine("chattoolkit.calc.help.vars", usage + " r = 5; pi r^2"));
		msg.append("\n").append(Component.translatable("chattoolkit.calc.help.functions").setStyle(Style.EMPTY
			.withColor(Msg.GRAY)
			.withHoverEvent(new HoverEvent.ShowText(Component.literal(builtins.get("functions")).withColor(Msg.WHITE)))));
		boolean degrees = ChatToolkitConfig.get().calcAngles == ChatToolkitConfig.AngleMode.DEGREES;
		msg.append("\n").append(Msg.tr(degrees ? "chattoolkit.calc.help.degrees" : "chattoolkit.calc.help.radians", Msg.DARK_GRAY));
		source.sendFeedback(msg);
		return Command.SINGLE_SUCCESS;
	}

	private static MutableComponent helpLine(String key, String example) {
		return Component.literal("  ").append(Component.translatable(key).withColor(Msg.GRAY))
			.append(Msg.space())
			.append(Component.literal("/" + example).setStyle(Style.EMPTY
				.withColor(Draw.accent())
				.withClickEvent(new ClickEvent.SuggestCommand("/" + example))
				.withHoverEvent(new HoverEvent.ShowText(Component.translatable("chattoolkit.try_it")))));
	}

	private static int vars(FabricClientCommandSource source, String name) {
		ChatToolkitConfig config = ChatToolkitConfig.get();
		MutableComponent msg = Component.empty().append(Msg.tr("chattoolkit.calc.vars.title", Msg.WHITE));
		Map<String, Double> vars = CALCULATOR.variables();
		if (vars.isEmpty()) {
			msg.append(Msg.space()).append(Msg.tr("chattoolkit.calc.vars.none", Msg.DARK_GRAY));
		}
		for (Map.Entry<String, Double> entry : vars.entrySet()) {
			String value = CalcFormat.format(entry.getValue(), config.calcDecimals, false);
			msg.append("\n  ").append(Component.literal(entry.getKey()).setStyle(Style.EMPTY
					.withColor(Draw.accent())
					.withClickEvent(new ClickEvent.SuggestCommand("/" + usage + " " + entry.getKey()))
					.withHoverEvent(new HoverEvent.ShowText(Component.translatable("chattoolkit.calc.vars.use")))))
				.append(Msg.text(" = ", Msg.GRAY))
				.append(Msg.copyable(CalcFormat.format(entry.getValue(), config.calcDecimals, config.calcSeparators), value, Msg.WHITE, false));
		}
		String ans = CalcFormat.format(CALCULATOR.ans(), config.calcDecimals, false);
		msg.append("\n  ").append(Msg.text("ans", Msg.DARK_GRAY)).append(Msg.text(" = ", Msg.DARK_GRAY)).append(Msg.copyable(ans, ans, Msg.GRAY, false));
		source.sendFeedback(msg);
		return Command.SINGLE_SUCCESS;
	}

	private static int history(FabricClientCommandSource source, String name) {
		ChatToolkitConfig config = ChatToolkitConfig.get();
		List<Calculator.HistoryEntry> history = CALCULATOR.history();
		MutableComponent msg = Component.empty().append(Msg.tr("chattoolkit.calc.history.title", Msg.WHITE));
		if (history.isEmpty()) {
			msg.append(Msg.space()).append(Msg.tr("chattoolkit.calc.history.none", Msg.DARK_GRAY));
		}
		for (int i = Math.min(history.size(), 10) - 1; i >= 0; i--) {
			Calculator.HistoryEntry entry = history.get(i);
			String plain = CalcFormat.format(entry.value(), config.calcDecimals, false);
			msg.append("\n  ").append(Component.literal(entry.expression()).setStyle(Style.EMPTY
					.withColor(Msg.GRAY)
					.withClickEvent(new ClickEvent.SuggestCommand("/" + usage + " " + entry.expression()))
					.withHoverEvent(new HoverEvent.ShowText(Component.translatable("chattoolkit.calc.edit")))))
				.append(Msg.text(" = ", Msg.DARK_GRAY))
				.append(Msg.copyable(CalcFormat.format(entry.value(), config.calcDecimals, config.calcSeparators), plain, Msg.WHITE, false));
		}
		source.sendFeedback(msg);
		return Command.SINGLE_SUCCESS;
	}

	private static int clear(FabricClientCommandSource source) {
		CALCULATOR.clear();
		source.sendFeedback(Component.empty().append(Msg.tr("chattoolkit.calc.cleared", Msg.GRAY)));
		return Command.SINGLE_SUCCESS;
	}

	// Suggests function, constant, unit and variable names for the word being typed
	private static CompletableFuture<Suggestions> suggest(CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
		String remaining = builder.getRemaining();
		int end = remaining.length();
		int start = end;
		while (start > 0 && (Character.isLetterOrDigit(remaining.charAt(start - 1)) || remaining.charAt(start - 1) == '_')) {
			start--;
		}
		while (start < end && Character.isDigit(remaining.charAt(start))) {
			start++;
		}
		if (start >= end) {
			return builder.buildFuture();
		}
		String prefix = remaining.substring(start, end);
		SuggestionsBuilder offset = builder.createOffset(builder.getStart() + start);
		for (String completion : CALCULATOR.completions(prefix)) {
			if (!completion.equalsIgnoreCase(prefix)) {
				offset.suggest(completion);
			}
		}
		return offset.buildFuture();
	}
}
