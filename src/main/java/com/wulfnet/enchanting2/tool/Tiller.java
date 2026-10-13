package com.wulfnet.enchanting2.tool;

import com.mojang.datafixers.util.Pair;
import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.effect.ModEffects;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Tiller (Phase 10cEC, DESIGN §9): tilling a block with the hoe also tills the square around it, 3×3 / 5×5 / 7×7 at
 * I / II / III. Only what vanilla tills into farmland (dirt, grass, dirt path, with air above), only where the player
 * may use the item and interact (spawn protection, adventure mode), 1 durability per block, nearest ring first, and it
 * stops when the hoe breaks.
 */
public final class Tiller {
	/** The largest radius any level or datapack value gets (17×17), so a raised max level can't till a field at once. */
	static final int MAX_RADIUS = 8;
	private static final Set<Block> TO_FARMLAND = Set.of(Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.DIRT_PATH);

	private Tiller() {
	}

	/** After vanilla's {@code useOn} returned {@code result} for {@code context}: the square around, if it tilled. */
	public static void afterUse(UseOnContext context, InteractionResult result) {
		Player player = context.getPlayer();
		if (!(context.getLevel() instanceof ServerLevel level) || player == null || !result.consumesAction()) {
			return;
		}
		BlockPos centre = context.getClickedPos();
		ItemStack hoe = context.getItemInHand();
		Pair<LevelBasedValue, Integer> till = EnchantmentHelper.getHighestLevel(hoe, ModEffects.TILL_RADIUS);
		if (till == null || !level.getBlockState(centre).is(Blocks.FARMLAND)) {
			return;
		}
		int radius = radius(till.getFirst().calculate(till.getSecond()));
		BlockState farmland = Blocks.FARMLAND.defaultBlockState();
		for (BlockPos pos : square(centre, radius)) {
			if (hoe.isEmpty()) {
				break;
			}
			if (!TO_FARMLAND.contains(level.getBlockState(pos).getBlock()) || !level.getBlockState(pos.above()).isAir()
					|| !player.mayUseItemAt(pos, context.getClickedFace(), hoe) || !level.mayInteract(player, pos)) {
				continue;
			}
			level.setBlock(pos, farmland, Block.UPDATE_ALL_IMMEDIATE);
			level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, farmland));
			hoe.hurtAndBreak(1, player, context.getHand().asEquipmentSlot());
		}
	}

	/** {@code value} as a whole radius in [0, {@link #MAX_RADIUS}]; a datapack's larger value is logged and capped. */
	static int radius(float value) {
		if (!Float.isFinite(value)) {
			Enchanting2.LOGGER.warn("Tiller radius is {}; ignoring it (a datapack's value?)", value);
			return 0;
		}
		int radius = Math.round(value);
		if (radius > MAX_RADIUS) {
			Enchanting2.LOGGER.warn("Tiller radius {} capped at {}", radius, MAX_RADIUS);
		}
		return Math.clamp(radius, 0, MAX_RADIUS);
	}

	/** The square of {@code radius} around {@code centre} on its level, the centre left out, nearest ring first. */
	static List<BlockPos> square(BlockPos centre, int radius) {
		List<BlockPos> around = new ArrayList<>();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx != 0 || dz != 0) {
					around.add(centre.offset(dx, 0, dz));
				}
			}
		}
		around.sort(Comparator.comparingInt(pos -> Math.max(Math.abs(pos.getX() - centre.getX()),
				Math.abs(pos.getZ() - centre.getZ()))));
		return around;
	}
}
