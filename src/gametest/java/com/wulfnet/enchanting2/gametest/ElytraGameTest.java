package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.enchantment.ModEnchantments;
import com.wulfnet.enchanting2.gametest.mixin.LivingEntityInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 10cGA: Speed through vanilla's own elytra physics, called directly (a mock player isn't ticked): the glide
 * formula iterated to its top speed, and one firework rocket tick on a gliding player.
 */
public class ElytraGameTest {
	/** Long past settling: Speed V's drag loses 1/150 of the speed per tick. */
	private static final int SETTLE_TICKS = 2000;

	private static ItemStack elytra(GameTestHelper helper, int speed) {
		ItemStack stack = new ItemStack(Items.ELYTRA);
		if (speed > 0) {
			stack.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
					.getOrThrow(ModEnchantments.SPEED), speed);
		}
		return stack;
	}

	private static void check(GameTestHelper helper, boolean condition, String message) {
		helper.assertTrue(condition, Component.literal(message));
	}

	/** A mock player in {@code elytra}, facing south and looking 10° down, a shallow glide. */
	private static ServerPlayer flyer(GameTestHelper helper, ItemStack elytra) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.snapTo(helper.absoluteVec(new Vec3(2.5, 5.0, 2.5)), 0.0F, 10.0F);
		player.setItemSlot(EquipmentSlot.CHEST, elytra);
		return player;
	}

	/** The horizontal speed the glide settles at from rest. */
	private static double topGlideSpeed(ServerPlayer player) {
		Vec3 velocity = Vec3.ZERO;
		for (int tick = 0; tick < SETTLE_TICKS; tick++) {
			velocity = ((LivingEntityInvoker) player).e2test$updateFallFlyingMovement(velocity);
		}
		return velocity.horizontalDistance();
	}

	@GameTest
	public void speedFiveGlidesHalfAgainAsFastAndStopsAddingAtTen(GameTestHelper helper) {
		double plain = topGlideSpeed(flyer(helper, elytra(helper, 0)));
		double five = topGlideSpeed(flyer(helper, elytra(helper, 5)));
		check(helper, plain > 0.5, "the plain glide barely moves: " + plain);
		check(helper, Math.abs(five / plain - 1.5) < 0.03, "Speed V glides " + five / plain + "× as fast as plain");
		double ten = topGlideSpeed(flyer(helper, elytra(helper, 10)));
		double twenty = topGlideSpeed(flyer(helper, elytra(helper, 20)));
		check(helper, Math.abs(twenty - ten) < 1.0E-9, "Speed XX glides faster than X: " + twenty + " vs " + ten);
		helper.succeed();
	}

	/** From rest a rocket's first tick gives 0.85 × look; with Speed V, 0.85 × 1.5. */
	@GameTest
	public void speedFiveScalesARocketsBoost(GameTestHelper helper) {
		helper.assertValueEqual(true, Math.abs(rocketTick(helper, 0) - 0.85) < 1.0E-6,
				Component.literal("a plain rocket tick: " + rocketTick(helper, 0)));
		double five = rocketTick(helper, 5);
		check(helper, Math.abs(five - 0.85 * 1.5) < 1.0E-6, "a Speed V rocket tick: " + five);
		helper.succeed();
	}

	private static double rocketTick(GameTestHelper helper, int speed) {
		ServerPlayer player = flyer(helper, elytra(helper, speed));
		player.startFallFlying();
		player.setDeltaMovement(Vec3.ZERO);
		FireworkRocketEntity rocket = new FireworkRocketEntity(helper.getLevel(), new ItemStack(Items.FIREWORK_ROCKET),
				player);
		rocket.tick();
		return player.getDeltaMovement().length();
	}
}
