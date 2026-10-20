package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.enchantment.ModEnchantments;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Phase 10cGC on the real client with real input: falling from high up in Double Jump boots, one jump press makes the
 * player rise, and the server (vanilla's own fall-distance cap while rising) forgets the fall so far; a second press
 * in the same fall does nothing.
 */
public class DoubleJumpClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			ClientWorlds.waitForChunksRender(world);
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runOnServer(server -> {
				ItemStack boots = new ItemStack(Items.IRON_BOOTS);
				boots.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
						.getOrThrow(ModEnchantments.DOUBLE_JUMP), 1);
				player(server).setItemSlot(EquipmentSlot.FEET, boots);
			});
			world.getServer().runCommand("execute as @p at @s run tp @s ~ ~30 ~");
			Delivery.toClient(context);
			context.waitTicks(8);
			check(context.computeOnClient(client -> client.player.getDeltaMovement().y < -0.2),
					"the player isn't falling yet");

			jump(context);
			check(context.computeOnClient(client -> client.player.getDeltaMovement().y > 0.0),
					"one jump press in the air didn't lift the player");
			Delivery.toServer(context);
			check(world.getServer().computeOnServer(server -> player(server).fallDistance < 1.0),
					"the server still counts the fall from before the air jump");

			context.waitTicks(10);
			jump(context);
			check(context.computeOnClient(client -> client.player.getDeltaMovement().y < 0.0),
					"a second air jump in the same fall lifted the player");
		}
	}

	/** Jump held for two ticks: the client reads it as held, not as a click. */
	private static void jump(ClientGameTestContext context) {
		context.getInput().holdKeyFor(options -> options.keyJump, 2);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
