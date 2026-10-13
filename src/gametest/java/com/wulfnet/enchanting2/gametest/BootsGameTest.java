package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.enchantment.ModEnchantments;
import com.wulfnet.enchanting2.gametest.mixin.LivingEntityInvoker;
import com.wulfnet.enchanting2.gametest.mixin.MobAccessor;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Phase 10cB: Lava Walking and Snow Walker on real, ticked mobs (owner: mobs too). Husks stand in for players: they
 * don't burn in daylight, so fire seen on one came from the lava. Their goals are cleared so they stand still and
 * never jump out of the lava on their own. Each test checks the bare case too, so it can't pass vacuously.
 */
public class BootsGameTest {
	/** Where the lava or powder snow sits; the pool's floor and walls are stone around it. */
	private static final BlockPos POOL = new BlockPos(2, 1, 2);
	/** Standing on lava, the feet rest on {@code LiquidBlock}'s half-block stable shape. */
	private static final double LAVA_SURFACE = 0.5;
	private static final float HEALTH = 100.0F;
	/** The pool's columns: walkers on one side, bare husks on the other. */
	private static final int WALKER = 1;
	private static final int BARE = 3;

	private static Holder<Enchantment> get(GameTestHelper helper, ResourceKey<Enchantment> key) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	private static ItemStack enchanted(GameTestHelper helper, Item item, ResourceKey<Enchantment> key) {
		ItemStack stack = new ItemStack(item);
		stack.enchant(get(helper, key), 1);
		return stack;
	}

	private static ItemStack lavaBoots(GameTestHelper helper) {
		return enchanted(helper, Items.IRON_BOOTS, ModEnchantments.LAVA_WALKING);
	}

	private static ItemStack snowBoots(GameTestHelper helper) {
		return enchanted(helper, Items.IRON_BOOTS, ModEnchantments.SNOW_WALKER);
	}

	private static void check(GameTestHelper helper, boolean condition, String message) {
		helper.assertTrue(condition, Component.literal(message));
	}

	/** A 3×3 pool of {@code fill} one deep at {@link #POOL}'s height, with a stone floor and rim. */
	private static void pool(GameTestHelper helper, BlockState fill) {
		for (int x = 0; x <= 4; x++) {
			for (int z = 0; z <= 4; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
				boolean inside = Math.abs(x - POOL.getX()) <= 1 && Math.abs(z - POOL.getZ()) <= 1;
				helper.setBlock(new BlockPos(x, POOL.getY(), z), inside ? fill : Blocks.STONE.defaultBlockState());
			}
		}
	}

	/**
	 * A husk in {@code boots} and nothing else, with no goals, at height {@code y} over the pool's column {@code x}
	 * (one husk per column, so they don't push each other). 100 health, so 40 ticks of lava can't kill it.
	 */
	private static Husk husk(GameTestHelper helper, int x, double y, ItemStack boots) {
		Husk husk = helper.spawn(EntityType.HUSK, new Vec3(x + 0.5, y, POOL.getZ() + 0.5));
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			husk.setItemSlot(slot, ItemStack.EMPTY);
		}
		husk.setItemSlot(EquipmentSlot.FEET, boots);
		((MobAccessor) husk).e2test$getGoalSelector().removeAllGoals(goal -> true);
		husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HEALTH);
		husk.setHealth(HEALTH);
		return husk;
	}

	private static double surfaceY(GameTestHelper helper) {
		return helper.absolutePos(POOL).getY() + LAVA_SURFACE;
	}

	@GameTest(maxTicks = 100)
	public void lavaWalkingStandsOnTheSurfaceAtGroundSpeedAndStillBurns(GameTestHelper helper) {
		pool(helper, Blocks.LAVA.defaultBlockState());
		Husk walker = husk(helper, WALKER, POOL.getY() + 1.0, lavaBoots(helper));
		Husk bare = husk(helper, BARE, POOL.getY() + 1.0, new ItemStack(Items.IRON_BOOTS));
		helper.runAfterDelay(40, () -> {
			double surface = surfaceY(helper);
			check(helper, bare.getY() < surface - 0.1, "a husk in plain boots didn't sink: y " + bare.getY());
			check(helper, Math.abs(walker.getY() - surface) < 1.0E-3 && walker.onGround(),
					"the walker isn't standing on the surface: y " + walker.getY() + ", surface " + surface);
			check(helper, walker.isInLava(), "the walker's feet aren't in the lava");
			check(helper, walker.isOnFire() && walker.getHealth() < HEALTH,
					"standing on lava doesn't burn (owner: it still burns)");
			LivingEntityInvoker walking = (LivingEntityInvoker) walker;
			check(helper, !walking.e2test$shouldTravelInFluid(Blocks.LAVA.defaultBlockState().getFluidState()),
					"the walker moves with lava's drag, not at ground speed");
			check(helper, ((LivingEntityInvoker) bare).e2test$shouldTravelInFluid(
					Blocks.LAVA.defaultBlockState().getFluidState()), "a sunk husk doesn't swim in lava");
			helper.succeed();
		});
	}

	/**
	 * Owner, 2026-10-12: the surface is a floor, so a fall onto it hurts as one; Fire Resistance hides the burn. The
	 * drop starts inside the 8-high test structure (a husk spawned above it landed on its ceiling).
	 */
	@GameTest(maxTicks = 100)
	public void lavaWalkingTakesFallDamageLandingOnLava(GameTestHelper helper) {
		pool(helper, Blocks.LAVA.defaultBlockState());
		Husk walker = husk(helper, WALKER, POOL.getY() + 6.0, lavaBoots(helper));
		walker.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200));
		helper.runAfterDelay(40, () -> {
			check(helper, Math.abs(walker.getY() - surfaceY(helper)) < 1.0E-3,
					"the walker fell through the surface: y " + walker.getY());
			// 5.5 blocks down less the safe 3 is 2.5: 2 or 3 depending on rounding, never 0.
			float lost = HEALTH - walker.getHealth();
			check(helper, lost >= 2.0F && lost <= 3.0F, "a 5.5-block fall onto lava took " + lost + " health");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void sneakingSinksALavaWalkerWhoThenSwims(GameTestHelper helper) {
		pool(helper, Blocks.LAVA.defaultBlockState());
		Husk walker = husk(helper, WALKER, POOL.getY() + 1.0, lavaBoots(helper));
		walker.setShiftKeyDown(true);
		helper.runAfterDelay(40, () -> {
			check(helper, walker.getY() < surfaceY(helper) - 0.1, "a sneaking walker didn't sink: y " + walker.getY());
			walker.setShiftKeyDown(false);
		});
		// Under the surface, standing up again doesn't make the lava floor ground: it swims, with lava's physics.
		helper.runAfterDelay(42, () -> {
			check(helper, ((LivingEntityInvoker) walker).e2test$shouldTravelInFluid(
					Blocks.LAVA.defaultBlockState().getFluidState()), "a sunk walker walks the lava floor at ground speed");
			helper.succeed();
		});
	}

	/**
	 * The collision shape directly, for each lava level: a walker stands on flowing lava too (agent, 2026-10-12),
	 * never on water, never while sneaking, and only from above the shape. The world above {@link #POOL} is air.
	 */
	@GameTest
	public void lavaWalkingStandsOnFlowingLavaButNotWater(GameTestHelper helper) {
		helper.setBlock(POOL, Blocks.AIR);
		helper.setBlock(POOL.above(), Blocks.AIR);
		Husk walker = husk(helper, WALKER, POOL.getY() + 0.6, lavaBoots(helper));
		Husk bare = husk(helper, BARE, POOL.getY() + 0.6, new ItemStack(Items.IRON_BOOTS));
		walker.setNoGravity(true);
		bare.setNoGravity(true);
		BlockPos at = helper.absolutePos(POOL);
		for (int level = 0; level <= 7; level++) {
			BlockState lava = Blocks.LAVA.defaultBlockState().setValue(LiquidBlock.LEVEL, level);
			check(helper, !lava.getCollisionShape(helper.getLevel(), at, CollisionContext.of(walker)).isEmpty(),
					"a walker can't stand on lava at level " + level);
			check(helper, lava.getCollisionShape(helper.getLevel(), at, CollisionContext.of(bare)).isEmpty(),
					"a bare husk stands on lava at level " + level);
		}
		BlockState water = Blocks.WATER.defaultBlockState();
		check(helper, water.getCollisionShape(helper.getLevel(), at, CollisionContext.of(walker)).isEmpty(),
				"Lava Walking stands on water");
		walker.setShiftKeyDown(true);
		check(helper, Blocks.LAVA.defaultBlockState().getCollisionShape(helper.getLevel(), at,
				CollisionContext.of(walker)).isEmpty(), "a sneaking walker still stands on lava");
		helper.succeed();
	}

	@GameTest
	public void snowWalkerIsTheLeatherBootsCheckOnAnyBoots(GameTestHelper helper) {
		Husk husk = husk(helper, WALKER, POOL.getY() + 1.0, new ItemStack(Items.IRON_BOOTS));
		husk.setNoGravity(true);
		check(helper, !PowderSnowBlock.canEntityWalkOnPowderSnow(husk), "plain iron boots walk on powder snow");
		check(helper, husk.canFreeze(), "a husk in plain iron boots can't freeze");
		husk.setItemSlot(EquipmentSlot.MAINHAND, enchanted(helper, Items.IRON_BOOTS, ModEnchantments.SNOW_WALKER));
		check(helper, !PowderSnowBlock.canEntityWalkOnPowderSnow(husk), "Snow Walker boots held in a hand count");
		husk.setItemSlot(EquipmentSlot.FEET, enchanted(helper, Items.IRON_BOOTS, ModEnchantments.SNOW_WALKER));
		check(helper, PowderSnowBlock.canEntityWalkOnPowderSnow(husk), "Snow Walker boots sink in powder snow");
		check(helper, !husk.canFreeze(), "Snow Walker boots still freeze");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void snowWalkerStaysOnTopOfPowderSnow(GameTestHelper helper) {
		pool(helper, Blocks.POWDER_SNOW.defaultBlockState());
		Husk walker = husk(helper, WALKER, POOL.getY() + 1.0, snowBoots(helper));
		Husk bare = husk(helper, BARE, POOL.getY() + 1.0, new ItemStack(Items.IRON_BOOTS));
		helper.runAfterDelay(40, () -> {
			double top = helper.absolutePos(POOL).getY() + 1.0;
			check(helper, bare.getY() < top - 0.1, "a husk in plain boots didn't sink: y " + bare.getY());
			check(helper, Math.abs(walker.getY() - top) < 1.0E-3, "the walker sank: y " + walker.getY());
			helper.succeed();
		});
	}
}
