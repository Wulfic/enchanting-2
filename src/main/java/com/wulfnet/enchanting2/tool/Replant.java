package com.wulfnet.enchanting2.tool;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.effect.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

/**
 * Replant (Phase 10cEB, DESIGN §9): breaking a mature crop with the hoe takes one of its own seeds out of its drops
 * and plants it back at age 0. It never creates a seed: no seed in the drops, no replant. An immature crop breaks as
 * usual. Runs inside the tool's drops, ahead of Magnetic, so the seed is taken before anything else moves.
 */
public final class Replant {
	/** The crops Replant works on (datapacks can add any block with an {@code age} property). */
	public static final TagKey<Block> REPLANTABLE = TagKey.create(Registries.BLOCK,
			Identifier.fromNamespaceAndPath(Enchanting2.MOD_ID, "replantable"));

	/** The seed one break is looking for, and whether it found it. */
	private static final class Pending {
		private final Item seed;
		private boolean taken;

		private Pending(Item seed) {
			this.seed = seed;
		}
	}

	private static final ThreadLocal<@Nullable Pending> PENDING = new ThreadLocal<>();

	private Replant() {
	}

	/**
	 * Runs {@code drops} (vanilla's drops for {@code state} broken at {@code pos} by {@code breaker} with
	 * {@code tool}); with Replant on a mature replantable crop, takes one seed from them and plants the crop again.
	 */
	public static void around(BlockState state, Level level, BlockPos pos, @Nullable Entity breaker,
			@Nullable ItemStack tool, Runnable drops) {
		IntegerProperty age = matureAge(state);
		if (age == null || !(level instanceof ServerLevel) || !(breaker instanceof ServerPlayer) || tool == null
				|| !state.is(REPLANTABLE) || !EnchantmentHelper.has(tool, ModEffects.REPLANT)) {
			drops.run();
			return;
		}
		Pending pending = new Pending(state.getCloneItemStack(level, pos, false).getItem());
		Pending outer = PENDING.get();
		PENDING.set(pending);
		try {
			drops.run();
		} finally {
			PENDING.set(outer);
		}
		if (!pending.taken) {
			return;
		}
		BlockState young = state.setValue(age, 0);
		if (level.getBlockState(pos).isAir() && young.canSurvive(level, pos)) {
			level.setBlock(pos, young, Block.UPDATE_ALL);
		} else {
			Enchanting2.LOGGER.debug("Replant: {} can't go back at {}; its seed drops instead", young, pos);
			Block.popResource(level, pos, new ItemStack(pending.seed));
		}
	}

	/** {@code stack} with one seed taken out, if a replant is looking for it; else {@code stack} itself. */
	public static ItemStack takeSeed(ItemStack stack) {
		Pending pending = PENDING.get();
		if (pending == null || pending.taken || !stack.is(pending.seed)) {
			return stack;
		}
		pending.taken = true;
		ItemStack left = stack.copy();
		left.shrink(1);
		return left;
	}

	/** {@code state}'s {@code age} property when it's at its highest value, else null (immature, or not a crop). */
	static @Nullable IntegerProperty matureAge(BlockState state) {
		for (Property<?> property : state.getProperties()) {
			if (property instanceof IntegerProperty age && age.getName().equals("age")) {
				int max = age.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
				return state.getValue(age) == max ? age : null;
			}
		}
		return null;
	}
}
