package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.combat.LastStand;
import com.wulfnet.enchanting2.enchantment.ModEnchantments;
import com.wulfnet.enchanting2.gametest.mixin.LocalPlayerInvoker;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.HitResult;

/**
 * Phase 10cCC on the real client with real input: holding "use" raises a shield and clicking "attack" on a cow in
 * front. With a plain shield vanilla swallows the click; with Shield Bash the click attacks and the server bashes, and
 * Steady Stance takes away the walking slowdown the client applies. A cow, as the test world may be peaceful. Then
 * Last Stand's cooldown, set on the server, reaching the client's HUD (Phase 10cFB).
 */
public class ShieldClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getClientWorld().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("time set day");
			// Facing south, looking a little down at a still cow two blocks away.
			world.getServer().runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 20");
			world.getServer().runCommand("execute as @p at @s run summon minecraft:cow ~ ~ ~2 {NoAI:1b}");
			world.getServer().runOnServer(server -> player(server).setItemSlot(EquipmentSlot.OFFHAND,
					new ItemStack(Items.SHIELD)));
			context.waitTicks(5);

			raise(context);
			check(context.computeOnClient(client -> ((LocalPlayerInvoker) client.player)
					.e2test$itemUseSpeedMultiplier() < 0.5F), "a plain shield doesn't slow the player");
			click(context);
			check(world.getServer().computeOnServer(server -> cow(server).getHealth() == cow(server).getMaxHealth()),
					"an attack click while blocking with a plain shield hit the cow");
			context.getInput().releaseKey(options -> options.keyUse);
			context.waitTicks(2);

			world.getServer().runOnServer(server -> {
				ItemStack shield = new ItemStack(Items.SHIELD);
				var enchantments = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
				shield.enchant(enchantments.getOrThrow(ModEnchantments.STEADY_STANCE), 1);
				shield.enchant(enchantments.getOrThrow(ModEnchantments.SHIELD_BASH), 3);
				player(server).setItemSlot(EquipmentSlot.OFFHAND, shield);
			});
			context.waitTicks(2);
			raise(context);
			check(context.computeOnClient(client -> ((LocalPlayerInvoker) client.player)
					.e2test$itemUseSpeedMultiplier() == 1.0F), "Steady Stance still slows the player");
			click(context);
			MobEffectInstance slowness = world.getServer().computeOnServer(
					server -> cow(server).getEffect(MobEffects.SLOWNESS));
			check(slowness != null && slowness.getAmplifier() == 3, "Shield Bash III didn't slow the cow: " + slowness);
			check(world.getServer().computeOnServer(server -> {
				ServerPlayer player = player(server);
				return !player.isUsingItem() && player.getCooldowns().isOnCooldown(player.getOffhandItem());
			}), "the shield is still up after the bash");
			context.getInput().releaseKey(options -> options.keyUse);
			context.waitTick();

			lastStandCooldownReachesTheHud(context, world);
		}
	}

	/** Phase 10cFB: a Last Stand cooldown set on the server reaches the client's player, which the HUD draws. */
	private static void lastStandCooldownReachesTheHud(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			ItemStack chestplate = new ItemStack(Items.IRON_CHESTPLATE);
			chestplate.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
					.getOrThrow(ModEnchantments.LAST_STAND), 1);
			player.setItemSlot(EquipmentSlot.CHEST, chestplate);
			player.setAttached(LastStand.READY_AT, player.level().getGameTime() + 5432);
		});
		context.waitTicks(3);
		long remaining = context.computeOnClient(client -> LastStand.remaining(client.player,
				client.level.getGameTime()));
		check(remaining > 5000 && remaining <= 5432, "the client's Last Stand cooldown: " + remaining);
		context.takeScreenshot("enchanting2-last-stand-hud");
	}

	/** Holds "use" until the shield is past its block delay, with the crosshair on the cow. */
	private static void raise(ClientGameTestContext context) {
		context.getInput().holdKey(options -> options.keyUse);
		context.waitTicks(8);
		check(context.computeOnClient(client -> client.player.isUsingItem()), "holding use didn't raise the shield");
		check(context.computeOnClient(client -> client.hitResult != null
				&& client.hitResult.getType() == HitResult.Type.ENTITY), "the crosshair isn't on the cow");
	}

	/** One "attack" click, and time for the attack to reach the server. */
	private static void click(ClientGameTestContext context) {
		context.getInput().pressKey(options -> options.keyAttack);
		context.waitTicks(3);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static Cow cow(MinecraftServer server) {
		ServerPlayer player = player(server);
		return player.level().getEntitiesOfClass(Cow.class, player.getBoundingBox().inflate(6.0)).getFirst();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
