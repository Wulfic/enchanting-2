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
 * Phase 10cGB on the real client with real input (Phase 11f, after the owner's "holding jump never speeds it up"):
 * gliding up at 20° in a Flight V elytra, the jump key held keeps the client's own player climbing at rocket speed,
 * where a glide alone slows below half a block a tick.
 */
public class FlightClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			ClientWorlds.waitForChunksRender(world);
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runOnServer(server -> {
				ItemStack elytra = new ItemStack(Items.ELYTRA);
				elytra.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
						.getOrThrow(ModEnchantments.FLIGHT), 5);
				player(server).setItemSlot(EquipmentSlot.CHEST, elytra);
			});
			world.getServer().runCommand("execute as @p at @s run tp @s ~ ~100 ~ 0 -20");
			context.waitTicks(8);
			context.getInput().holdKeyFor(options -> options.keyJump, 2);
			check(context.computeOnClient(client -> client.player.isFallFlying()), "a jump press didn't start the glide");

			context.getInput().holdKeyFor(options -> options.keyJump, 40);
			double thrusting = context.computeOnClient(client -> client.player.getDeltaMovement().length());
			check(thrusting > 1.2, "two seconds of jump held reached only " + thrusting + " blocks a tick");

			context.waitTicks(40);
			double coasting = context.computeOnClient(client -> client.player.getDeltaMovement().length());
			check(coasting < thrusting * 0.6, "letting go of jump still thrusts: " + coasting + " vs " + thrusting);
		}
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
