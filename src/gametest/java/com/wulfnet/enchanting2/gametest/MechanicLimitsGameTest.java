package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.enchantment.MaxLevels;
import com.wulfnet.enchanting2.gametest.mixin.EntityInvoker;
import com.wulfnet.enchanting2.gametest.mixin.FishingHookAccessor;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * DESIGN §8's conditional rows and {@link MaxLevels#SAFE_MAX}, against the live game code (arithmetic in TODO.md 4D.1).
 * Each cap is tested against cap + 1: the cap must work and the next level must break. Levels above the registry max
 * are put on items directly, which vanilla's effect code accepts.
 */
public class MechanicLimitsGameTest {
	private static final int LURE_CAP = MaxLevels.SAFE_MAX.get("minecraft:lure");
	private static final int QUICK_CHARGE_CAP = MaxLevels.SAFE_MAX.get("minecraft:quick_charge");
	private static final int RIPTIDE_CAP = MaxLevels.SAFE_MAX.get("minecraft:riptide");
	/** A player's fall speed limit: gravity × 0.98 drag per tick, solved for the fixed point. */
	private static final double DRAG = 0.98;
	/** {@code handleMovePlayer} rejects a single move packet when {@code dist² − serverVelocity² > 100}. */
	private static final double MOVED_TOO_QUICKLY_SQR = 100.0;

	private static Holder<Enchantment> get(GameTestHelper helper, ResourceKey<Enchantment> key) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	private static ItemStack enchanted(GameTestHelper helper, ItemStack stack, ResourceKey<Enchantment> key, int level) {
		stack.enchant(get(helper, key), level);
		return stack;
	}

	/** {@code FishingRodItem#use}: seconds of reduction × 20, truncated. */
	private static int lureSpeed(GameTestHelper helper, ServerPlayer player, int level) {
		ItemStack rod = enchanted(helper, new ItemStack(Items.FISHING_ROD), Enchantments.LURE, level);
		return (int) (EnchantmentHelper.getFishingTimeReduction(helper.getLevel(), rod, player) * 20.0F);
	}

	/** A 3×3, two-deep water pool walled with glass, open to the sky; returns the centre of its surface block. */
	private static Vec3 pool(GameTestHelper helper) {
		for (int x = 1; x <= 5; x++) {
			for (int z = 1; z <= 5; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.GLASS);
				for (int y = 1; y <= 2; y++) {
					boolean wall = x == 1 || x == 5 || z == 1 || z == 5;
					helper.setBlock(new BlockPos(x, y, z), wall ? Blocks.GLASS : Blocks.WATER);
				}
			}
		}
		return helper.absoluteVec(new Vec3(3.5, 2.5, 3.5));
	}

	/** A hook bobbing in the pool, owned by a mock player holding a rod (the hook discards itself otherwise). */
	private static FishingHookAccessor hookInPool(GameTestHelper helper, ServerPlayer player, int lureSpeed) {
		Vec3 surface = pool(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FISHING_ROD));
		player.snapTo(helper.absoluteVec(new Vec3(0.5, 3.0, 0.5)));
		FishingHook hook = new FishingHook(player, helper.getLevel(), 0, lureSpeed);
		hook.snapTo(surface.x, surface.y, surface.z);
		hook.setDeltaMovement(Vec3.ZERO);
		helper.getLevel().addFreshEntity(hook);
		return (FishingHookAccessor) hook;
	}

	@GameTest
	public void lureSpeedIsOneHundredTicksPerLevel(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertValueEqual(500, lureSpeed(helper, player, 5), Component.literal("Lure V speed (ticks)"));
		helper.assertValueEqual(600, lureSpeed(helper, player, 6), Component.literal("Lure VI speed (ticks)"));
		helper.assertValueEqual(5, LURE_CAP, Component.literal("Lure safe max (the bite tests below assume it)"));
		helper.succeed();
	}

	@GameTest(skyAccess = true, maxTicks = 1200)
	public void lureAtTheCapStillGetsABite(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		FishingHookAccessor hook = hookInPool(helper, player, lureSpeed(helper, player, LURE_CAP));
		helper.succeedWhen(() -> helper.assertTrue(hook.e2test$getNibble() > 0,
				Component.literal("no fish on a Lure " + LURE_CAP + " hook yet")));
	}

	@GameTest(skyAccess = true, maxTicks = 400)
	public void lureAboveTheCapNeverGetsABite(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		FishingHookAccessor hook = hookInPool(helper, player, lureSpeed(helper, player, LURE_CAP + 1));
		// A negative timer only comes from a re-roll, so it proves catchingFish ran rather than the hook doing nothing.
		AtomicBoolean rerolled = new AtomicBoolean();
		helper.onEachTick(() -> {
			if (hook.e2test$getTimeUntilLured() < 0) {
				rerolled.set(true);
			}
			helper.assertTrue(hook.e2test$getNibble() == 0 && hook.e2test$getTimeUntilHooked() == 0
					&& hook.e2test$getTimeUntilLured() <= 0, Component.literal("a Lure " + (LURE_CAP + 1) + " hook started a bite"));
		});
		helper.runAtTickTime(300, () -> {
			helper.assertTrue(rerolled.get(), Component.literal("catchingFish never re-rolled the Lure " + (LURE_CAP + 1) + " timer"));
			helper.succeed();
		});
	}

	@GameTest
	public void quickChargeAboveTheCapHasNoChargeTime(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		int atCap = CrossbowItem.getChargeDuration(
				enchanted(helper, new ItemStack(Items.CROSSBOW), Enchantments.QUICK_CHARGE, QUICK_CHARGE_CAP), player);
		int above = CrossbowItem.getChargeDuration(
				enchanted(helper, new ItemStack(Items.CROSSBOW), Enchantments.QUICK_CHARGE, QUICK_CHARGE_CAP + 1), player);
		helper.assertTrue(atCap > 0, Component.literal("Quick Charge " + QUICK_CHARGE_CAP + " charge ticks: " + atCap));
		helper.assertValueEqual(0, above, Component.literal("Quick Charge " + (QUICK_CHARGE_CAP + 1) + " charge ticks"));
		helper.succeed();
	}

	@GameTest(skyAccess = true)
	public void riptideFiveLaunchesWithinTheMovementCheck(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		pool(helper);
		player.snapTo(helper.absoluteVec(new Vec3(3.5, 1.0, 3.5)), 0.0F, 0.0F);
		helper.assertTrue(((EntityInvoker) player).e2test$updateInWaterStateAndDoFluidPushing() && player.isInWaterOrRain(),
				Component.literal("mock player is not in the pool's water"));
		ItemStack trident = enchanted(helper, new ItemStack(Items.TRIDENT), Enchantments.RIPTIDE, 5);
		player.setItemInHand(InteractionHand.MAIN_HAND, trident);
		player.setDeltaMovement(Vec3.ZERO);

		// Held for 20 ticks; releaseUsing needs at least 10. It also plays the level-capped riptide sound.
		helper.assertTrue(trident.getItem().releaseUsing(trident, helper.getLevel(), player,
				trident.getUseDuration(player) - 20), Component.literal("Riptide V release did nothing"));
		float strength = EnchantmentHelper.getTridentSpinAttackStrength(trident, player);
		helper.assertValueEqual(4.5F, strength, Component.literal("Riptide V strength"));
		// Looking straight ahead (yaw 0, pitch 0) the push is (0, 0, strength).
		double gained = player.getDeltaMovement().length();
		helper.assertTrue(Math.abs(gained - strength) < 1.0E-4,
				Component.literal("Riptide V velocity gain " + gained + ", expected " + strength));
		helper.assertTrue(player.isAutoSpinAttack(), Component.literal("Riptide V did not start the spin attack"));

		// Worst single launch with no server credit: along a terminal-velocity fall.
		double terminal = player.getGravity() * DRAG / (1.0 - DRAG);
		for (int level : new int[] {5, RIPTIDE_CAP, RIPTIDE_CAP + 1}) {
			float s = EnchantmentHelper.getTridentSpinAttackStrength(
					enchanted(helper, new ItemStack(Items.TRIDENT), Enchantments.RIPTIDE, level), player);
			double worst = (s + terminal) * (s + terminal);
			boolean passes = worst <= MOVED_TOO_QUICKLY_SQR;
			helper.assertValueEqual(level <= RIPTIDE_CAP, passes, Component.literal(
					"Riptide " + level + ": (" + s + " + " + terminal + ")² = " + worst + " vs " + MOVED_TOO_QUICKLY_SQR));
		}
		helper.succeed();
	}
}
