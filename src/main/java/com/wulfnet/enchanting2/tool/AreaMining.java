package com.wulfnet.enchanting2.tool;

import com.wulfnet.enchanting2.effect.ModEffects;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Area Mining (Phase 10cEA, DESIGN §9): breaking a block with the tool also breaks the 8 around it in the plane of the
 * face that was hit, each through the player's own {@code destroyBlock}, so claim mods' break events, durability and
 * every drop enchantment apply to each block. Only blocks no harder than the centre that the tool mines properly.
 */
public final class AreaMining {
	/** True while our extra breaks run, so they don't each start an area of their own. */
	private static final ThreadLocal<Boolean> EXTRA = ThreadLocal.withInitial(() -> false);

	private AreaMining() {
	}

	/**
	 * {@code player} breaks {@code pos} ({@code centre} runs vanilla's break and says whether it happened), then the
	 * area around it. {@code face} is the face the client said it hit; null falls back to where the player looks.
	 */
	public static boolean destroy(ServerPlayer player, ServerLevel level, BlockPos pos, @Nullable Direction face,
			Predicate<BlockPos> breakBlock) {
		if (EXTRA.get() || player.isShiftKeyDown()
				|| !EnchantmentHelper.has(player.getMainHandItem(), ModEffects.AREA_MINING)) {
			return breakBlock.test(pos);
		}
		float centreHardness = level.getBlockState(pos).getDestroySpeed(level, pos);
		if (!breakBlock.test(pos)) {
			return false;
		}
		Direction.Axis axis = face != null ? face.getAxis() : Direction.orderedByNearest(player)[0].getAxis();
		EXTRA.set(true);
		try {
			for (BlockPos around : plane(pos, axis)) {
				ItemStack tool = player.getMainHandItem();
				if (!EnchantmentHelper.has(tool, ModEffects.AREA_MINING)) {
					break;
				}
				if (minesWith(tool, level.getBlockState(around), level, around, centreHardness)) {
					breakBlock.test(around);
				}
			}
		} finally {
			EXTRA.set(false);
		}
		return true;
	}

	/** Whether {@code tool} takes {@code state} along: breakable, no harder than the centre, the tool's own block. */
	static boolean minesWith(ItemStack tool, BlockState state, ServerLevel level, BlockPos pos, float centreHardness) {
		if (state.isAir()) {
			return false;
		}
		float hardness = state.getDestroySpeed(level, pos);
		return hardness >= 0.0F && hardness <= centreHardness && tool.isCorrectToolForDrops(state);
	}

	/** The 8 positions around {@code centre} in the plane across {@code axis}. */
	public static List<BlockPos> plane(BlockPos centre, Direction.Axis axis) {
		List<BlockPos> around = new ArrayList<>(8);
		for (int a = -1; a <= 1; a++) {
			for (int b = -1; b <= 1; b++) {
				if (a == 0 && b == 0) {
					continue;
				}
				around.add(switch (axis) {
					case X -> centre.offset(0, a, b);
					case Y -> centre.offset(a, 0, b);
					case Z -> centre.offset(a, b, 0);
				});
			}
		}
		return around;
	}
}
