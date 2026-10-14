package com.wulfnet.enchanting2.gametest;

import com.warwa.elytraslot.ElytraSlotUtil;
import com.wulfnet.enchanting2.enchantment.ModEnchantments;
import com.wulfnet.enchanting2.gametest.mixin.LivingEntityInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 10cGA/GB: Speed and Flight through vanilla's own elytra physics, called directly (a mock player isn't ticked):
 * the glide formula iterated to its top speed, one firework rocket tick on a gliding player, and the server's elytra
 * tick for Flight's durability. Phase 11f: the same with the elytra in Elytra Slot's slot (the stub
 * {@link ElytraSlotUtil} and {@code ElytraSlotStubMixin}, which cancels vanilla's elytra tick as the mod does).
 */
public class ElytraGameTest {
	/** Long past settling: Speed V's drag loses 1/150 of the speed per tick. */
	private static final int SETTLE_TICKS = 2000;

	private static ItemStack elytra(GameTestHelper helper, int speed) {
		return elytra(helper, speed, 0);
	}

	private static ItemStack elytra(GameTestHelper helper, int speed, int flight) {
		ItemStack stack = new ItemStack(Items.ELYTRA);
		var enchantments = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		if (speed > 0) {
			stack.enchant(enchantments.getOrThrow(ModEnchantments.SPEED), speed);
		}
		if (flight > 0) {
			stack.enchant(enchantments.getOrThrow(ModEnchantments.FLIGHT), flight);
		}
		return stack;
	}

	/** The input a client holding only jump sends; the server reads it, the glide asks it. */
	private static final Input JUMP = new Input(false, false, false, false, true, false, false);

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

	/** A survival mock player climbing at 20°, holding jump or not. */
	private static ServerPlayer climber(GameTestHelper helper, ItemStack elytra, boolean jump) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.snapTo(helper.absoluteVec(new Vec3(2.5, 5.0, 2.5)), 0.0F, -20.0F);
		player.setItemSlot(EquipmentSlot.CHEST, elytra);
		player.setLastClientInput(jump ? JUMP : Input.EMPTY);
		return player;
	}

	/**
	 * Owner: holding jump while gliding gives steady thrust, V about one rocket. Climbing at 20° shows it: a glide alone
	 * slows to 0.38 blocks a tick there, a rocket holds 1.66 (vanilla's formulas, worked through in decisions.md).
	 */
	@GameTest
	public void flightFiveClimbsLikeARocketWhileJumpIsHeld(GameTestHelper helper) {
		double thrusting = topSpeed(climber(helper, elytra(helper, 0, 5), true));
		double coasting = topSpeed(climber(helper, elytra(helper, 0, 5), false));
		double plain = topSpeed(climber(helper, elytra(helper, 0, 0), true));
		check(helper, Math.abs(thrusting - 1.66) < 0.05, "Flight V's climbing speed: " + thrusting);
		check(helper, coasting < 0.5, "Flight V thrusts without jump: " + coasting);
		check(helper, Math.abs(plain - coasting) < 1.0E-9, "jump on a plain elytra pushes: " + plain + " vs " + coasting);
		double withSpeed = topSpeed(climber(helper, elytra(helper, 5, 5), true));
		check(helper, Math.abs(withSpeed / thrusting - 1.5) < 0.05, "Speed V scales Flight's thrust by "
				+ withSpeed / thrusting);
		double ten = topSpeed(climber(helper, elytra(helper, 0, 10), true));
		double twenty = topSpeed(climber(helper, elytra(helper, 0, 20), true));
		check(helper, Math.abs(twenty - ten) < 1.0E-9, "Flight XX flies unlike X (past the cap): " + twenty + " vs " + ten);
		helper.succeed();
	}

	/** The whole speed (climbing included) the glide settles at from rest. */
	private static double topSpeed(ServerPlayer player) {
		Vec3 velocity = Vec3.ZERO;
		for (int tick = 0; tick < SETTLE_TICKS; tick++) {
			velocity = ((LivingEntityInvoker) player).e2test$updateFallFlyingMovement(velocity);
		}
		return velocity.length();
	}

	/** Owner: 1 elytra durability per second of thrust; vanilla's own wear runs off its own counter. */
	@GameTest
	public void flightWearsTheElytraAPointPerSecondOfThrust(GameTestHelper helper) {
		helper.assertValueEqual(2, wearAfterFortyTicks(helper, true), Component.literal("durability for 2 s of thrust"));
		helper.assertValueEqual(0, wearAfterFortyTicks(helper, false), Component.literal("durability without thrust"));
		helper.succeed();
	}

	private static int wearAfterFortyTicks(GameTestHelper helper, boolean jump) {
		ServerPlayer player = climber(helper, elytra(helper, 0, 5), jump);
		glideFortyTicks(helper, player);
		return player.getItemBySlot(EquipmentSlot.CHEST).getDamageValue();
	}

	/**
	 * Forty server ticks of {@code player}'s AI step, where vanilla's elytra tick (and Flight's wear after it) runs. The
	 * step moves the player too, so it starts 40 blocks up: a glide without thrust lands from 5 in 38 ticks.
	 */
	private static void glideFortyTicks(GameTestHelper helper, ServerPlayer player) {
		player.snapTo(player.position().add(0.0, 40.0, 0.0), player.getYRot(), player.getXRot());
		player.startFallFlying();
		for (int tick = 0; tick < 40; tick++) {
			player.aiStep();
			check(helper, player.isFallFlying(), "the mock player stopped gliding after " + (tick + 1) + " ticks (on ground "
					+ player.onGround() + ", at " + player.position() + ", velocity " + player.getDeltaMovement() + ")");
		}
	}

	/** A climber with an iron chestplate on, and {@code elytra} in Elytra Slot's slot. */
	private static ServerPlayer slottedClimber(GameTestHelper helper, ItemStack elytra, boolean jump) {
		ServerPlayer player = climber(helper, new ItemStack(Items.IRON_CHESTPLATE), jump);
		ElytraSlotUtil.setAccessoryElytra(player, elytra);
		return player;
	}

	/** Owner, Phase 11f: an elytra in Elytra Slot's slot, a chestplate on, flies with Speed and Flight as in the chest. */
	@GameTest
	public void anElytraSlotElytraGetsSpeedAndFlight(GameTestHelper helper) {
		double chest = topSpeed(climber(helper, elytra(helper, 5, 5), true));
		double slotted = topSpeed(slottedClimber(helper, elytra(helper, 5, 5), true));
		double coasting = topSpeed(slottedClimber(helper, elytra(helper, 5, 5), false));
		check(helper, Math.abs(slotted - chest) < 1.0E-9, "slotted " + slotted + " vs chest " + chest);
		check(helper, slotted > 2.0 * coasting, "a slotted Flight V elytra doesn't thrust: " + slotted + " vs coasting "
				+ coasting);
		helper.succeed();
	}

	/**
	 * Phase 11f: Elytra Slot cancels vanilla's elytra tick, and Flight's wear still lands, on the slotted elytra (the
	 * chestplate untouched), written back into the slot.
	 */
	@GameTest
	public void flightWearsAnElytraSlotElytraThoughItsModCancelsTheElytraTick(GameTestHelper helper) {
		ServerPlayer player = slottedClimber(helper, elytra(helper, 0, 5), true);
		glideFortyTicks(helper, player);
		helper.assertValueEqual(2, ElytraSlotUtil.getAccessoryElytra(player).getDamageValue(),
				Component.literal("slotted elytra durability for 2 s of thrust"));
		helper.assertValueEqual(0, player.getItemBySlot(EquipmentSlot.CHEST).getDamageValue(),
				Component.literal("chestplate durability"));
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
