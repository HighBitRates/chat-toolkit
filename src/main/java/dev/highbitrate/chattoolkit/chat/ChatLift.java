package dev.highbitrate.chattoolkit.chat;

import dev.highbitrate.chattoolkit.config.ChatToolkitConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

// Moves the chat up in survival so it (and the search bar) sit above the hearts/armor/food
// instead of on top of them. In creative there's nothing to dodge so it stays put.
public final class ChatLift {
	private static float current;
	private static long lastFrame;

	private ChatLift() {
	}

	// how many pixels the chat is moved up right now
	public static int get() {
		return Math.round(current);
	}

	// called once per frame before the chat draws, eases towards the target height
	public static void update() {
		int target = target();
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 1 : Math.min(0.1f, (now - lastFrame) / 1.0e9f);
		lastFrame = now;
		if (!ChatToolkitConfig.get().animations) {
			current = target;
			return;
		}
		current += (target - current) * (1 - (float) Math.exp(-dt * 14));
		if (Math.abs(target - current) < 0.05f) {
			current = target;
		}
	}

	private static int target() {
		ChatToolkitConfig config = ChatToolkitConfig.get();
		int lift = config.chatOffset;
		if (!config.liftChat) {
			return lift;
		}
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || mc.gameMode == null) {
			return lift;
		}

		int width = mc.getWindow().getGuiScaledWidth();
		int height = mc.getWindow().getGuiScaledHeight();
		int chatRight = ChatGeometry.screenRight();
		int top = statusBarsTop(mc, player, width, height, chatRight);

		// the lowest thing we draw is the search bar (bottom at height - 25), or the chat itself (height - 40)
		int lowest = config.searchBar ? height - 25 : height - 40;
		return lift + Math.max(0, lowest - (top - 2));
	}

	// Top edge of whatever status bars sit under the chat. Same maths as Hud#extractPlayerHealth.
	private static int statusBarsTop(Minecraft mc, Player player, int width, int height, int chatRight) {
		int top = height - 22;
		int left = width / 2 - 91;
		int right = width / 2 + 91;
		boolean survival = mc.gameMode.canHurtPlayer();

		if (survival && chatRight > left) {
			float maxHealth = Math.max((float) player.getAttributeValue(Attributes.MAX_HEALTH), player.getHealth());
			int absorption = Mth.ceil(player.getAbsorptionAmount());
			int rows = Mth.ceil((maxHealth + absorption) / 2.0f / 10.0f);
			int rowHeight = Math.max(10 - (rows - 2), 3);
			int hearts = height - 39 - (rows - 1) * rowHeight;
			if (player.getArmorValue() > 0) {
				hearts -= 10;
			}
			top = Math.min(top, hearts);
		}

		// food, air and mount health are on the right half, only matters with a really wide chat
		if (chatRight > right - 81) {
			if (survival) {
				top = Math.min(top, height - 39);
				if (player.isUnderWater() || player.getAirSupply() < player.getMaxAirSupply()) {
					top = Math.min(top, height - 49);
				}
			}
			if (player.getVehicle() instanceof LivingEntity mount && mount.showVehicleHealth()) {
				int rows = Mth.ceil(mount.getMaxHealth() / 2.0f / 10.0f);
				top = Math.min(top, height - 39 - (Math.min(rows, 3) - 1) * 10);
			}
		}
		return top;
	}
}
