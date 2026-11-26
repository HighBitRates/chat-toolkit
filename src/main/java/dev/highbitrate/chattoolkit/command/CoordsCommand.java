package dev.highbitrate.chattoolkit.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.highbitrate.chattoolkit.ui.Draw;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// /ccoords: your position with one-click copy/share/save, plus the Nether/Overworld equivalent
public final class CoordsCommand {

	private CoordsCommand() {
	}

	public static LiteralArgumentBuilder<FabricClientCommandSource> build(String name) {
		return literal(name).executes(ctx -> show(ctx.getSource()));
	}

	private static int show(FabricClientCommandSource source) {
		LocalPlayer player = source.getPlayer();
		int x = player.getBlockX();
		int y = player.getBlockY();
		int z = player.getBlockZ();
		ResourceKey<Level> dimension = player.level().dimension();
		String coords = x + " " + y + " " + z;
		String share = "X: " + x + " Y: " + y + " Z: " + z;
		Direction facing = player.getDirection();

		MutableComponent msg = Component.empty()
			.append(Msg.copyable(x + ", " + y + ", " + z, coords, Msg.WHITE, true))
			.append(Msg.text("  " + dimensionName(dimension) + ", ", Msg.GRAY))
			.append(Component.translatable("chattoolkit.coords.facing", Component.translatable("chattoolkit.direction." + facing.getName())).withColor(Msg.GRAY))
			.append(Msg.space())
			.append(Msg.button("chattoolkit.coords.copy", new ClickEvent.CopyToClipboard(coords), "chattoolkit.coords.copy.hover", coords))
			.append(Msg.button("chattoolkit.coords.share", new ClickEvent.SuggestCommand(share), "chattoolkit.coords.share.hover"))
			.append(Msg.button("chattoolkit.coords.note", new ClickEvent.RunCommand("cnotes here"), "chattoolkit.coords.note.hover"));

		if (dimension == Level.OVERWORLD) {
			String nether = Math.floorDiv(x, 8) + " " + y + " " + Math.floorDiv(z, 8);
			msg.append("\n  ").append(Msg.tr("chattoolkit.coords.in_nether", Msg.DARK_GRAY))
				.append(Msg.space()).append(Msg.copyable(Math.floorDiv(x, 8) + ", " + Math.floorDiv(z, 8), nether, Draw.accent(), false));
		} else if (dimension == Level.NETHER) {
			String overworld = (x * 8) + " " + y + " " + (z * 8);
			msg.append("\n  ").append(Msg.tr("chattoolkit.coords.in_overworld", Msg.DARK_GRAY))
				.append(Msg.space()).append(Msg.copyable((x * 8) + ", " + (z * 8), overworld, Draw.accent(), false));
		}
		source.sendFeedback(msg);
		return Command.SINGLE_SUCCESS;
	}

	private static String dimensionName(ResourceKey<Level> dimension) {
		if (dimension == Level.OVERWORLD) {
			return "Overworld";
		}
		if (dimension == Level.NETHER) {
			return "Nether";
		}
		if (dimension == Level.END) {
			return "The End";
		}
		return dimension.identifier().toString();
	}
}
