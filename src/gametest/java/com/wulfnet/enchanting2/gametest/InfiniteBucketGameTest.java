package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.acquisition.AcquisitionRules;
import com.wulfnet.enchanting2.anvil.AnvilCalculator;
import com.wulfnet.enchanting2.anvil.AnvilOutcome;
import com.wulfnet.enchanting2.bucket.InfiniteBuckets;
import com.wulfnet.enchanting2.config.CommonConfig;
import com.wulfnet.enchanting2.config.EnchantmentOverrides;
import com.wulfnet.enchanting2.cost.PricingTable;
import com.wulfnet.enchanting2.enchantment.EnchantRules;
import com.wulfnet.enchanting2.enchantment.ModEnchantments;
import com.wulfnet.enchanting2.table.TableCalculator;
import com.wulfnet.enchanting2.table.TableOutcome;
import com.wulfnet.enchanting2.table.TablePool;
import com.wulfnet.enchanting2.table.TableRow;
import com.wulfnet.enchanting2.table.TableSelection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * DESIGN §6 "Infinite buckets" (Phase 10gC). Every emptying path through vanilla's own entry points: a survival mock
 * player's {@code useItem} (hand), {@code useItemOn} (cauldron), and a powered dispenser.
 */
public class InfiniteBucketGameTest {
	/** The floor the player pours onto; the fluid lands on top of it at {@link #POUR}. */
	private static final BlockPos FLOOR = new BlockPos(2, 1, 2);
	private static final BlockPos POUR = FLOOR.above();

	private static void check(GameTestHelper helper, boolean condition, String message) {
		helper.assertTrue(condition, Component.literal(message));
	}

	private static Holder<Enchantment> get(GameTestHelper helper, ResourceKey<Enchantment> key) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	private static ItemStack with(GameTestHelper helper, Item item, Map<ResourceKey<Enchantment>, Integer> levels) {
		ItemStack stack = new ItemStack(item);
		levels.forEach((key, level) -> stack.enchant(get(helper, key), level));
		return stack;
	}

	private static ItemStack infinite(GameTestHelper helper, Item bucket) {
		return with(helper, bucket, Map.of(Enchantments.INFINITY, 1));
	}

	private static ItemStack book(GameTestHelper helper, Map<ResourceKey<Enchantment>, Integer> levels) {
		ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
		ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		levels.forEach((key, level) -> stored.set(get(helper, key), level));
		EnchantmentHelper.setEnchantments(book, stored.toImmutable());
		return book;
	}

	/** A survival player standing over {@link #FLOOR}, looking straight down at it. */
	private static ServerPlayer pourer(GameTestHelper helper) {
		helper.setBlock(FLOOR, Blocks.STONE);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Vec3 above = helper.absoluteVec(Vec3.atBottomCenterOf(POUR.above()));
		player.snapTo(above.x, above.y, above.z, 0, 90);
		return player;
	}

	/** Pours what's in the main hand onto {@link #FLOOR} the way a right click does, then clears the fluid. */
	private static void pour(GameTestHelper helper, ServerPlayer player) {
		player.gameMode.useItem(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
		check(helper, !helper.getBlockState(POUR).getFluidState().isEmpty(), "nothing was poured");
		helper.setBlock(POUR, Blocks.AIR);
	}

	@GameTest
	public void tenPoursLeaveOneFullEnchantedBucketEach(GameTestHelper helper) {
		for (Item bucket : InfiniteBuckets.BUCKETS) {
			ServerPlayer player = pourer(helper);
			player.setItemInHand(InteractionHand.MAIN_HAND, infinite(helper, bucket));
			for (int i = 0; i < 10; i++) {
				pour(helper, player);
			}
			ItemStack held = player.getMainHandItem();
			check(helper, held.is(bucket) && held.getCount() == 1, bucket + " became " + held);
			check(helper, InfiniteBuckets.isInfinite(held), bucket + " lost Infinity: " + held);
			helper.assertValueEqual(10, held.getDamageValue(), Component.literal(bucket + " damage after 10 pours"));
			check(helper, !player.getInventory().contains(new ItemStack(Items.BUCKET)), "an empty bucket appeared");
		}
		helper.succeed();
	}

	@GameTest
	public void aPlainBucketStillEmptiesAndIsNeverDamaged(GameTestHelper helper) {
		ServerPlayer player = pourer(helper);
		ItemStack plain = new ItemStack(Items.WATER_BUCKET);
		check(helper, plain.getMaxDamage() == InfiniteBuckets.USES, "a water bucket has no 256-use durability");
		player.setItemInHand(InteractionHand.MAIN_HAND, plain);
		pour(helper, player);
		ItemStack held = player.getMainHandItem();
		check(helper, held.is(Items.BUCKET), "a plain water bucket became " + held);
		check(helper, !held.isDamaged(), "the empty bucket is damaged: " + held);
		helper.succeed();
	}

	@GameTest
	public void theLastUseDestroysIt(GameTestHelper helper) {
		ServerPlayer player = pourer(helper);
		ItemStack bucket = infinite(helper, Items.LAVA_BUCKET);
		bucket.setDamageValue(InfiniteBuckets.USES - 1);
		player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
		pour(helper, player);
		check(helper, player.getMainHandItem().isEmpty(), "the bucket survived its last use: " + player.getMainHandItem());
		check(helper, !player.getInventory().contains(new ItemStack(Items.BUCKET)), "it left an empty bucket behind");
		helper.succeed();
	}

	@GameTest
	public void unbreakingSkipsUses(GameTestHelper helper) {
		ServerPlayer player = pourer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND,
				with(helper, Items.WATER_BUCKET, Map.of(Enchantments.INFINITY, 1, Enchantments.UNBREAKING, 3)));
		int pours = 40;
		for (int i = 0; i < pours; i++) {
			pour(helper, player);
		}
		// Unbreaking III skips 3 uses in 4; all 40 counting has odds (1/4)^40.
		int damage = player.getMainHandItem().getDamageValue();
		check(helper, damage < pours, "Unbreaking III skipped no use in " + pours + " pours");
		helper.succeed();
	}

	@GameTest
	public void mendingRepairsItFromXp(GameTestHelper helper) {
		ServerPlayer player = pourer(helper);
		ItemStack bucket = with(helper, Items.WATER_BUCKET, Map.of(Enchantments.INFINITY, 1, Enchantments.MENDING, 1));
		bucket.setDamageValue(100);
		player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
		ExperienceOrb orb = new ExperienceOrb(helper.getLevel(), player.getX(), player.getY(), player.getZ(), 20);
		helper.getLevel().addFreshEntity(orb);
		orb.playerTouch(player);
		check(helper, player.getMainHandItem().getDamageValue() < 100,
				"Mending didn't repair the bucket: " + player.getMainHandItem().getDamageValue());
		helper.succeed();
	}

	@GameTest
	public void aCauldronKeepsItAndUsesDurability(GameTestHelper helper) {
		ServerPlayer player = pourer(helper);
		helper.setBlock(FLOOR, Blocks.CAULDRON);
		player.setItemInHand(InteractionHand.MAIN_HAND, infinite(helper, Items.WATER_BUCKET));
		BlockPos cauldron = helper.absolutePos(FLOOR);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(cauldron), Direction.UP, cauldron, false);
		player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
		check(helper, helper.getBlockState(FLOOR).is(Blocks.WATER_CAULDRON)
				&& helper.getBlockState(FLOOR).getValue(LayeredCauldronBlock.LEVEL) == 3, "the cauldron didn't fill");
		ItemStack held = player.getMainHandItem();
		check(helper, InfiniteBuckets.isInfinite(held) && held.getDamageValue() == 1, "after the cauldron: " + held);
		helper.succeed();
	}

	@GameTest
	public void aDispenserKeepsItAndUsesDurability(GameTestHelper helper) {
		BlockPos dispenser = new BlockPos(1, 2, 2);
		helper.setBlock(dispenser, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
		helper.getBlockEntity(dispenser, DispenserBlockEntity.class).setItem(0, infinite(helper, Items.LAVA_BUCKET));
		helper.setBlock(dispenser.below(), Blocks.REDSTONE_BLOCK);
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.LAVA, dispenser.east());
			ItemStack slot = helper.getBlockEntity(dispenser, DispenserBlockEntity.class).getItem(0);
			check(helper, InfiniteBuckets.isInfinite(slot) && slot.getDamageValue() == 1, "dispenser slot: " + slot);
		});
	}

	@GameTest
	public void netherEvaporationKeepsItFullAndUsesDurability(GameTestHelper helper) {
		ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
		check(helper, nether != null, "no Nether");
		// Far from anything else, at a height the Nether's roof never reaches.
		BlockPos floor = new BlockPos(30_000, 100, 30_000);
		nether.getChunk(floor);
		nether.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
		nether.setBlockAndUpdate(floor.above(), Blocks.AIR.defaultBlockState());
		nether.setBlockAndUpdate(floor.above(2), Blocks.AIR.defaultBlockState());
		try {
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.setGameMode(GameType.SURVIVAL);
			player.snapTo(floor.getX() + 0.5, floor.getY() + 2, floor.getZ() + 0.5, 0, 90);
			player.setItemInHand(InteractionHand.MAIN_HAND, infinite(helper, Items.WATER_BUCKET));
			player.gameMode.useItem(player, nether, player.getMainHandItem(), InteractionHand.MAIN_HAND);
			check(helper, nether.getBlockState(floor.above()).getFluidState().isEmpty(), "water stayed in the Nether");
			ItemStack held = player.getMainHandItem();
			check(helper, InfiniteBuckets.isInfinite(held) && held.getDamageValue() == 1, "after evaporating: " + held);
		} finally {
			nether.setBlockAndUpdate(floor, Blocks.AIR.defaultBlockState());
		}
		helper.succeed();
	}

	@GameTest
	public void anInfinityLavaBucketIsNeverFuel(GameTestHelper helper) {
		var fuel = helper.getLevel().fuelValues();
		check(helper, fuel.isFuel(new ItemStack(Items.LAVA_BUCKET)), "a plain lava bucket stopped being fuel");
		ItemStack infinite = infinite(helper, Items.LAVA_BUCKET);
		check(helper, !fuel.isFuel(infinite), "an Infinity lava bucket is fuel");
		helper.assertValueEqual(0, fuel.burnDuration(infinite), Component.literal("Infinity lava bucket burn time"));
		helper.succeed();
	}

	@GameTest
	public void noRecipeIngredientAcceptsAnInfinityBucket(GameTestHelper helper) {
		Ingredient water = Ingredient.of(Items.WATER_BUCKET);
		check(helper, water.test(new ItemStack(Items.WATER_BUCKET)), "a plain water bucket stopped matching");
		check(helper, !water.test(infinite(helper, Items.WATER_BUCKET)), "an Infinity water bucket matches");
		helper.succeed();
	}

	// --- Getting it on a bucket ------------------------------------------------------------------------------------

	private static TableOutcome table(GameTestHelper helper, ItemStack target) {
		AcquisitionRules rules = new AcquisitionRules(EnchantmentOverrides.defaults());
		return TableCalculator.compute(target, 64, 15, TableSelection.EMPTY, 1000, false, CommonConfig.defaults(),
				PricingTable.builtIn(),
				TablePool.members(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT), rules),
				rules);
	}

	private static List<String> offered(TableOutcome outcome) {
		return outcome.rows().stream().map(TableRow::id).sorted().toList();
	}

	@GameTest
	public void theTableOffersOnlyInfinityOnAPlainBucketAndNothingOnAnEmptyOne(GameTestHelper helper) {
		helper.assertValueEqual(List.of("minecraft:infinity"), offered(table(helper, new ItemStack(Items.WATER_BUCKET))),
				Component.literal("table rows for a water bucket"));
		helper.assertValueEqual(List.of(), offered(table(helper, new ItemStack(Items.BUCKET))),
				Component.literal("table rows for an empty bucket"));
		List<String> withInfinity = offered(table(helper, infinite(helper, Items.LAVA_BUCKET)));
		check(helper, withInfinity.contains("minecraft:unbreaking"), "no Unbreaking for an Infinity bucket: " + withInfinity);
		helper.succeed();
	}

	private static AnvilOutcome anvil(ItemStack left, ItemStack right) {
		return AnvilCalculator.compute(left, right, null, false, CommonConfig.defaults(), PricingTable.builtIn());
	}

	/** The guard: Mending bought for a plain bucket would be lost the first time it's poured. */
	@GameTest
	public void mendingAloneIsRefusedOnAPlainBucket(GameTestHelper helper) {
		AnvilOutcome outcome = anvil(new ItemStack(Items.WATER_BUCKET), book(helper, Map.of(Enchantments.MENDING, 1)));
		check(helper, outcome.result().isEmpty()
				|| EnchantmentHelper.getItemEnchantmentLevel(get(helper, Enchantments.MENDING), outcome.result()) == 0,
				"Mending went onto a plain bucket: " + outcome.result());
		AnvilOutcome empty = anvil(new ItemStack(Items.BUCKET), book(helper, Map.of(Enchantments.INFINITY, 1)));
		check(helper, empty.result().isEmpty() || !InfiniteBuckets.isInfinite(empty.result()),
				"Infinity went onto an empty bucket: " + empty.result());
		helper.succeed();
	}

	@GameTest
	public void aBookWithInfinityAndMendingAppliesInOneGo(GameTestHelper helper) {
		ItemStack book = book(helper, Map.of(Enchantments.INFINITY, 1, Enchantments.MENDING, 1));
		AnvilOutcome outcome = anvil(new ItemStack(Items.WATER_BUCKET), book);
		check(helper, outcome.ok(), "the anvil refused Infinity + Mending on a water bucket: " + outcome.status());
		ItemStack result = outcome.result();
		check(helper, InfiniteBuckets.isInfinite(result)
				&& EnchantmentHelper.getItemEnchantmentLevel(get(helper, Enchantments.MENDING), result) == 1,
				"result: " + result);
		check(helper, result.has(DataComponents.MAX_DAMAGE), "the result has no durability");
		// Mending sorts after Infinity anyway. An ID that sorts first (as Keep on Death and Fragility will, 10c/10e)
		// must still come after it, or the anvil would check it before Infinity is on the bucket.
		Holder<Enchantment> infinity = get(helper, Enchantments.INFINITY);
		Holder<Enchantment> reach = get(helper, ModEnchantments.REACH);
		helper.assertValueEqual(List.of(infinity, reach),
				Stream.of(reach, infinity).sorted(EnchantRules.APPLY_ORDER).toList(), Component.literal("apply order"));
		helper.succeed();
	}
}
