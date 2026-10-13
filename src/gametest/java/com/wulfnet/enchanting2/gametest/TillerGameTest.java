package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.enchantment.ModEnchantments;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 10cEC: Tiller through a survival player's real hoe use ({@code ItemStack.useOn}) on a 7×7 dirt floor, with
 * blocks mixed in that must stay, and a hoe about to break.
 */
public class TillerGameTest {
	private static final BlockPos CENTRE = new BlockPos(3, 1, 3);

	private static ItemStack hoe(GameTestHelper helper, int level) {
		ItemStack stack = new ItemStack(Items.DIAMOND_HOE);
		if (level > 0) {
			stack.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
					.getOrThrow(ModEnchantments.TILLER), level);
		}
		return stack;
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 6; x++) {
			for (int z = 0; z <= 6; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT);
			}
		}
	}

	/** A survival player tills {@link #CENTRE} from above with {@code hoe}; returns the hoe as it is after. */
	private static ItemStack till(GameTestHelper helper, ItemStack hoe) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.setPos(helper.absoluteVec(new Vec3(3.5, 2.0, 3.5)));
		player.setItemInHand(InteractionHand.MAIN_HAND, hoe);
		BlockPos at = helper.absolutePos(CENTRE);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(at).add(0, 0.5, 0), Direction.UP, at, false);
		player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
		return player.getMainHandItem();
	}

	private static int farmland(GameTestHelper helper) {
		int count = 0;
		for (int x = 0; x <= 6; x++) {
			for (int z = 0; z <= 6; z++) {
				count += helper.getBlockState(new BlockPos(x, 1, z)).is(Blocks.FARMLAND) ? 1 : 0;
			}
		}
		return count;
	}

	@GameTest
	public void tillerOneTillsTheThreeByThreeButOnlyTillableOpenBlocks(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(new BlockPos(2, 1, 3), Blocks.GRASS_BLOCK);
		helper.setBlock(new BlockPos(4, 1, 4), Blocks.COARSE_DIRT);
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.STONE);
		ItemStack hoe = till(helper, hoe(helper, 1));
		helper.assertBlockPresent(Blocks.FARMLAND, new BlockPos(2, 1, 3));
		helper.assertBlockPresent(Blocks.COARSE_DIRT, new BlockPos(4, 1, 4));
		helper.assertBlockPresent(Blocks.DIRT, new BlockPos(2, 1, 2));
		helper.assertBlockPresent(Blocks.DIRT, new BlockPos(1, 1, 3));
		helper.assertValueEqual(7, farmland(helper), Component.literal("farmland after Tiller I"));
		helper.assertValueEqual(7, hoe.getDamageValue(), Component.literal("durability: 1 per tilled block"));
		helper.succeed();
	}

	@GameTest
	public void tillerThreeTillsSevenBySevenAndAPlainHoeOne(GameTestHelper helper) {
		floor(helper);
		till(helper, hoe(helper, 3));
		helper.assertValueEqual(49, farmland(helper), Component.literal("farmland after Tiller III"));
		floor(helper);
		till(helper, hoe(helper, 0));
		helper.assertValueEqual(1, farmland(helper), Component.literal("farmland after a plain hoe"));
		helper.succeed();
	}

	/** 4 uses left: the centre and the 3 nearest, then the hoe breaks and tilling stops. */
	@GameTest
	public void tillerStopsWhenTheHoeBreaks(GameTestHelper helper) {
		floor(helper);
		ItemStack worn = hoe(helper, 2);
		worn.setDamageValue(worn.getMaxDamage() - 4);
		ItemStack after = till(helper, worn);
		helper.assertValueEqual(4, farmland(helper), Component.literal("farmland before the hoe broke"));
		helper.assertTrue(after.isEmpty(), Component.literal("the hoe didn't break"));
		helper.succeed();
	}
}
