package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.enchantment.ModEnchantments;
import com.wulfnet.enchanting2.tool.Replant;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 10cEB: Replant through a survival player's real {@code destroyBlock} on crops on farmland, plus its seed rule
 * with drops chosen by the test, since a real crop's seed count is random.
 */
public class ReplantGameTest {
	private static final BlockPos CROP = new BlockPos(2, 2, 2);

	@SafeVarargs
	private static ItemStack hoe(GameTestHelper helper, ResourceKey<Enchantment>... keys) {
		ItemStack stack = new ItemStack(Items.DIAMOND_HOE);
		for (ResourceKey<Enchantment> key : keys) {
			stack.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key), 1);
		}
		return stack;
	}

	private static void check(GameTestHelper helper, boolean condition, String message) {
		helper.assertTrue(condition, Component.literal(message));
	}

	private static ServerPlayer farmer(GameTestHelper helper, ItemStack hoe) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.setPos(helper.absoluteVec(new Vec3(0.5, 1.0, 0.5)));
		player.setItemInHand(InteractionHand.MAIN_HAND, hoe);
		return player;
	}

	/** {@code crop} at its {@code age} on farmland at {@link #CROP}. */
	private static void plant(GameTestHelper helper, Block crop, int age) {
		helper.setBlock(CROP.below(), Blocks.FARMLAND);
		helper.setBlock(CROP, ((CropBlock) crop).getStateForAge(age));
	}

	private static int dropped(GameTestHelper helper, Item item) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(CROP)).inflate(2.0))
				.stream().filter(entity -> entity.getItem().is(item)).mapToInt(entity -> entity.getItem().getCount())
				.sum();
	}

	private static void breakCrop(GameTestHelper helper, ServerPlayer player) {
		check(helper, player.gameMode.destroyBlock(helper.absolutePos(CROP)), "break refused");
	}

	@GameTest
	public void replantPlantsAMatureCropAgainAndLeavesAnImmatureOneBroken(GameTestHelper helper) {
		ServerPlayer player = farmer(helper, hoe(helper, ModEnchantments.REPLANT));
		plant(helper, Blocks.WHEAT, 7);
		breakCrop(helper, player);
		BlockState wheat = helper.getBlockState(CROP);
		check(helper, wheat.is(Blocks.WHEAT) && ((CropBlock) Blocks.WHEAT).getAge(wheat) == 0,
				"mature wheat wasn't planted again: " + wheat);
		check(helper, dropped(helper, Items.WHEAT) == 1, "the harvest didn't drop its wheat");

		plant(helper, Blocks.CARROTS, 7);
		breakCrop(helper, player);
		check(helper, helper.getBlockState(CROP).is(Blocks.CARROTS), "mature carrots weren't planted again");

		plant(helper, Blocks.WHEAT, 3);
		breakCrop(helper, player);
		check(helper, helper.getBlockState(CROP).isAir(), "an immature crop was planted again");

		ServerPlayer plain = farmer(helper, hoe(helper));
		plant(helper, Blocks.WHEAT, 7);
		breakCrop(helper, plain);
		check(helper, helper.getBlockState(CROP).isAir(), "a plain hoe planted again");
		helper.succeed();
	}

	/** Cocoa keeps its facing; with Magnetic too, the seed is taken before the rest goes to the inventory. */
	@GameTest
	public void replantKeepsCocoaOnItsLogAndRunsBeforeMagnetic(GameTestHelper helper) {
		ServerPlayer player = farmer(helper, hoe(helper, ModEnchantments.REPLANT, ModEnchantments.MAGNETIC));
		helper.setBlock(CROP.north(), Blocks.JUNGLE_LOG);
		BlockState cocoa = Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.FACING, Direction.NORTH)
				.setValue(CocoaBlock.AGE, 2);
		helper.setBlock(CROP, cocoa);
		breakCrop(helper, player);
		BlockState after = helper.getBlockState(CROP);
		check(helper, after.is(Blocks.COCOA) && after.getValue(CocoaBlock.AGE) == 0
				&& after.getValue(CocoaBlock.FACING) == Direction.NORTH, "cocoa wasn't planted back on its log: " + after);
		// Mature cocoa drops 3 beans: one replanted, two in the inventory, none on the ground.
		helper.assertValueEqual(2, player.getInventory().countItem(Items.COCOA_BEANS),
				Component.literal("cocoa beans in the inventory"));
		helper.assertValueEqual(0, dropped(helper, Items.COCOA_BEANS), Component.literal("cocoa beans on the ground"));
		helper.succeed();
	}

	/** Drops without a seed plant nothing; drops with two leave one. */
	@GameTest
	public void replantTakesASeedFromTheDropsAndNeverCreatesOne(GameTestHelper helper) {
		ServerPlayer player = farmer(helper, hoe(helper, ModEnchantments.REPLANT));
		BlockState mature = ((CropBlock) Blocks.WHEAT).getStateForAge(7);
		BlockPos at = helper.absolutePos(CROP);
		helper.setBlock(CROP.below(), Blocks.FARMLAND);

		Replant.around(mature, helper.getLevel(), at, player, player.getMainHandItem(),
				() -> Block.popResource(helper.getLevel(), at, new ItemStack(Items.WHEAT)));
		check(helper, helper.getBlockState(CROP).isAir(), "planted with no seed in the drops");

		Replant.around(mature, helper.getLevel(), at, player, player.getMainHandItem(),
				() -> Block.popResource(helper.getLevel(), at, new ItemStack(Items.WHEAT_SEEDS, 2)));
		check(helper, helper.getBlockState(CROP).is(Blocks.WHEAT), "not planted with two seeds in the drops");
		helper.assertValueEqual(1, dropped(helper, Items.WHEAT_SEEDS), Component.literal("seeds left on the ground"));
		helper.succeed();
	}
}
