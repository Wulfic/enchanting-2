package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.anvil.InvalidBookMode;
import com.wulfnet.enchanting2.config.AnvilSection;
import com.wulfnet.enchanting2.config.CommonConfig;
import com.wulfnet.enchanting2.config.ServerConfigHolder;
import com.wulfnet.enchanting2.config.ServerConfigs;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Phase 11c (owner 2026-10-13): anvils never wear out by default, from use or from a fall; {@code vanilla_durability}
 * brings vanilla's chips back. Uses go through a real menu on a placed anvil, so vanilla's own 12% roll runs.
 */
public class AnvilWearGameTest {
	/** 0.88^200 ≈ 8e-12: with vanilla's 12% chance, 200 uses that never chip mean the guard is broken. */
	private static final int USES = 200;
	private static final BlockPos ANVIL = new BlockPos(1, 1, 1);

	private static CommonConfig durability(boolean vanilla) {
		CommonConfig d = CommonConfig.defaults();
		return new CommonConfig(d.experience(), d.allowTogether(), d.costs(), d.curses(),
				new AnvilSection(InvalidBookMode.APPLY_VALID, vanilla, 1), d.table(), d.fortune(), d.beheading(),
				d.smelting(), d.silkTouch(), d.lastStand(), d.mobs());
	}

	/** Runs {@code body} with {@code config} on the server, then puts the previous one back, even if it throws. */
	private static void withConfig(GameTestHelper helper, CommonConfig config, Runnable body) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerConfigHolder holder = (ServerConfigHolder) server;
		CommonConfig previous = ServerConfigs.get(server);
		holder.enchanting2$setConfig(config);
		try {
			body.run();
		} finally {
			holder.enchanting2$setConfig(previous);
		}
	}

	/** Renames a stick on the anvil at {@link #ANVIL} {@link #USES} times, or until the anvil isn't plain any more. */
	private static void useRepeatedly(GameTestHelper helper) {
		ServerPlayer player = AnvilGameTest.player(helper, 1_000_000);
		BlockPos pos = helper.absolutePos(ANVIL);
		AnvilMenu menu = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos));
		for (int i = 0; i < USES && helper.getBlockState(ANVIL).is(Blocks.ANVIL); i++) {
			menu.getSlot(AnvilMenu.INPUT_SLOT).set(new ItemStack(Items.STICK));
			menu.setItemName("stick " + i);
			menu.clicked(AnvilMenu.RESULT_SLOT, 0, ClickType.PICKUP, player);
			helper.assertTrue(menu.getCarried().is(Items.STICK), Component.literal("use " + i + " took no result"));
			menu.setCarried(ItemStack.EMPTY);
		}
	}

	@GameTest
	public void aDefaultAnvilNeverWearsFromUse(GameTestHelper helper) {
		helper.setBlock(ANVIL, Blocks.ANVIL);
		withConfig(helper, durability(false), () -> useRepeatedly(helper));
		helper.assertBlockPresent(Blocks.ANVIL, ANVIL);
		helper.succeed();
	}

	@GameTest
	public void vanillaDurabilityChipsTheAnvil(GameTestHelper helper) {
		helper.setBlock(ANVIL, Blocks.ANVIL);
		withConfig(helper, durability(true), () -> useRepeatedly(helper));
		helper.assertTrue(!helper.getBlockState(ANVIL).is(Blocks.ANVIL),
				Component.literal("vanilla durability never chipped the anvil in " + USES + " uses"));
		helper.succeed();
	}

	/** A 40-block fall: vanilla's chance is 0.05 + 0.05 × 39, so it always chips unless we stop it. */
	private static BlockState landFromFortyBlocks(GameTestHelper helper) {
		helper.setBlock(ANVIL, Blocks.ANVIL);
		FallingBlockEntity falling = FallingBlockEntity.fall(helper.getLevel(), helper.absolutePos(ANVIL),
				Blocks.ANVIL.defaultBlockState());
		falling.setHurtsEntities(2.0F, 40);
		falling.causeFallDamage(40.0, 1.0F, helper.getLevel().damageSources().fall());
		BlockState landed = falling.getBlockState();
		falling.discard();
		return landed;
	}

	@GameTest
	public void aDefaultAnvilNeverWearsFromAFall(GameTestHelper helper) {
		withConfig(helper, durability(false), () -> helper.assertTrue(landFromFortyBlocks(helper).is(Blocks.ANVIL),
				Component.literal("a fall chipped a default anvil")));
		helper.succeed();
	}

	@GameTest
	public void vanillaDurabilityChipsAFallingAnvil(GameTestHelper helper) {
		withConfig(helper, durability(true), () -> helper.assertTrue(
				landFromFortyBlocks(helper).is(Blocks.CHIPPED_ANVIL),
				Component.literal("a 40-block fall didn't chip the anvil with vanilla durability on")));
		helper.succeed();
	}
}
