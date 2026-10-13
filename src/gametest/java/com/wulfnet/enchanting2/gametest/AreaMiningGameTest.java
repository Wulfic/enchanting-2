package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.enchantment.ModEnchantments;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 10cEA: Area Mining through a survival player's real {@code destroyBlock}. Without a client break packet the
 * plane follows where the player looks. Each layout mixes in blocks that must stay, and the plain cases break one.
 */
public class AreaMiningGameTest {
	private static final BlockPos FLOOR_CENTRE = new BlockPos(2, 1, 2);
	private static final BlockPos HARDER = new BlockPos(1, 1, 1);
	private static final BlockPos WRONG_TOOL = new BlockPos(3, 1, 3);

	private static ItemStack pickaxe(GameTestHelper helper, boolean areaMining) {
		ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
		if (areaMining) {
			stack.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
					.getOrThrow(ModEnchantments.AREA_MINING), 1);
		}
		return stack;
	}

	private static void check(GameTestHelper helper, boolean condition, String message) {
		helper.assertTrue(condition, Component.literal(message));
	}

	/** A survival player at {@code at} looking with {@code yaw}/{@code pitch}, holding {@code tool}. */
	private static ServerPlayer miner(GameTestHelper helper, Vec3 at, float yaw, float pitch, ItemStack tool) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.snapTo(helper.absoluteVec(at), yaw, pitch);
		player.setItemInHand(InteractionHand.MAIN_HAND, tool);
		return player;
	}

	/** A 3×3 floor of stone at y 1 around the centre, with an obsidian and a dirt corner. */
	private static void floor(GameTestHelper helper) {
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
			}
		}
		helper.setBlock(HARDER, Blocks.OBSIDIAN);
		helper.setBlock(WRONG_TOOL, Blocks.DIRT);
	}

	private static ServerPlayer lookingDown(GameTestHelper helper, ItemStack tool) {
		return miner(helper, new Vec3(2.5, 3.0, 2.5), 0.0F, 90.0F, tool);
	}

	private static int stoneLeft(GameTestHelper helper) {
		int left = 0;
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				Block block = helper.getBlockState(new BlockPos(x, 1, z)).getBlock();
				left += block == Blocks.STONE ? 1 : 0;
			}
		}
		return left;
	}

	private static int cobblestone(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(FLOOR_CENTRE))
				.inflate(3.0)).stream().filter(item -> item.getItem().is(Items.COBBLESTONE))
				.mapToInt(item -> item.getItem().getCount()).sum();
	}

	@GameTest
	public void areaMiningTakesTheStoneAroundButNotHarderOrWrongToolBlocks(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = lookingDown(helper, pickaxe(helper, true));
		check(helper, player.gameMode.destroyBlock(helper.absolutePos(FLOOR_CENTRE)), "break refused");
		helper.assertValueEqual(0, stoneLeft(helper), Component.literal("stone left in the 3×3"));
		helper.assertBlockPresent(Blocks.OBSIDIAN, HARDER);
		helper.assertBlockPresent(Blocks.DIRT, WRONG_TOOL);
		helper.assertValueEqual(7, cobblestone(helper), Component.literal("cobblestone dropped"));
		helper.assertValueEqual(7, player.getMainHandItem().getDamageValue(), Component.literal("durability used"));
		helper.succeed();
	}

	@GameTest
	public void sneakingOrAPlainPickaxeBreaksOneBlock(GameTestHelper helper) {
		floor(helper);
		ServerPlayer sneaking = lookingDown(helper, pickaxe(helper, true));
		sneaking.setShiftKeyDown(true);
		check(helper, sneaking.gameMode.destroyBlock(helper.absolutePos(FLOOR_CENTRE)), "break refused");
		helper.assertValueEqual(6, stoneLeft(helper), Component.literal("stone left after a sneaking break"));

		helper.setBlock(FLOOR_CENTRE, Blocks.STONE);
		ServerPlayer plain = lookingDown(helper, pickaxe(helper, false));
		check(helper, plain.gameMode.destroyBlock(helper.absolutePos(FLOOR_CENTRE)), "break refused");
		helper.assertValueEqual(6, stoneLeft(helper), Component.literal("stone left after a plain break"));
		helper.succeed();
	}

	/** Looking north at a wall: the 3×3 across the north-south axis, in the wall. */
	@GameTest
	public void areaMiningFollowsTheFaceIntoAWall(GameTestHelper helper) {
		for (int x = 1; x <= 3; x++) {
			for (int y = 1; y <= 3; y++) {
				helper.setBlock(new BlockPos(x, y, 2), Blocks.STONE);
			}
		}
		helper.setBlock(new BlockPos(2, 2, 1), Blocks.STONE);
		ServerPlayer player = miner(helper, new Vec3(2.5, 1.0, 5.5), 180.0F, 0.0F, pickaxe(helper, true));
		check(helper, player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(2, 2, 2))), "break refused");
		for (int x = 1; x <= 3; x++) {
			for (int y = 1; y <= 3; y++) {
				helper.assertBlockNotPresent(Blocks.STONE, new BlockPos(x, y, 2));
			}
		}
		helper.assertBlockPresent(Blocks.STONE, new BlockPos(2, 2, 1));
		helper.succeed();
	}

	/** A claim mod refusing one block (Fabric's break event, as claim mods use) keeps that block. */
	@GameTest
	public void aRefusedBreakEventKeepsThatBlock(GameTestHelper helper) {
		floor(helper);
		BlockPos refused = helper.absolutePos(new BlockPos(3, 1, 2));
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> !pos.equals(refused));
		ServerPlayer player = lookingDown(helper, pickaxe(helper, true));
		check(helper, player.gameMode.destroyBlock(helper.absolutePos(FLOOR_CENTRE)), "break refused");
		helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 1, 2));
		helper.assertValueEqual(1, stoneLeft(helper), Component.literal("stone left (the refused one)"));
		helper.succeed();
	}
}
