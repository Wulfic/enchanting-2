package com.wulfnet.enchanting2.fortune;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.config.CommonConfig;
import com.wulfnet.enchanting2.config.ServerConfigs;
import com.wulfnet.enchanting2.effect.SmeltingDrops;
import com.wulfnet.enchanting2.spawner.CollectedSpawners;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * DESIGN §6 Fortune on all mining tools: multiplies every drop of a natural block in its tool's
 * {@code fortune_multiplies} tag, after the block's own loot table has run (Fortune has no effect components; its
 * vanilla bonus lives in loot tables). Acts only on the broken block's own table, never on chests, mobs or nested
 * tables. The facts go to {@link FortuneRule}; the chunk's marks and the Silk-free re-roll are only fetched when
 * needed. Any error leaves the drops as vanilla made them.
 */
public final class FortuneDrops {
	/** Our own Silk-free re-roll fires this event again; that roll must come back untouched. */
	private static final ThreadLocal<Boolean> REROLLING = ThreadLocal.withInitial(() -> false);

	private FortuneDrops() {
	}

	public static void register() {
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			if (!REROLLING.get()) {
				CommonConfig config = ServerConfigs.get(context.getLevel().getServer());
				// Before Fortune, so the hard exclusion below is what keeps a collected spawner single.
				CollectedSpawners.addDrop(table, context, drops, config.silkTouch().collectSpawners());
				apply(table, context, drops, config.fortune().silkFortuneMode());
				// Smelting after Fortune (owner, 2026-10-09), and here rather than in its own listener so it never
				// sees the re-roll: smelted re-roll drops would always read as "Silk Touch changed the drop".
				SmeltingDrops.apply(table, context, drops, config.smelting().silkPriority());
			}
		});
	}

	/** The event handler with {@code mode} given rather than read from the server config. Mutates {@code drops}. */
	public static void apply(Holder<LootTable> table, LootContext context, List<ItemStack> drops, SilkFortuneMode mode) {
		BlockState state = context.getOptionalParameter(LootContextParams.BLOCK_STATE);
		ItemStack tool = context.getOptionalParameter(LootContextParams.TOOL);
		Vec3 origin = context.getOptionalParameter(LootContextParams.ORIGIN);
		if (drops.isEmpty() || state == null || tool == null || tool.isEmpty() || origin == null) {
			return;
		}
		Optional<ResourceKey<LootTable>> ownTable = state.getBlock().getLootTable();
		if (ownTable.isEmpty() || !table.is(ownTable.get())) {
			return;
		}
		ServerLevel level = context.getLevel();
		BlockPos pos = BlockPos.containing(origin);
		try {
			int fortune = EnchantmentHelper.getItemEnchantmentLevel(enchantment(level, Enchantments.FORTUNE), tool);
			if (fortune <= 0) {
				return;
			}
			boolean hasSilk = EnchantmentHelper.getItemEnchantmentLevel(enchantment(level, Enchantments.SILK_TOUCH), tool) > 0;
			FortuneRule.Decision decision = FortuneRule.decide(fortune, hasSilk, mode,
					FortuneTags.multipliesFor(tool, state), state.is(FortuneTags.SILK), excluded(state),
					() -> PlacedBlocks.origin(level, pos), () -> silkChangedDrop(table.value(), context, state, tool, drops));
			if (decision != FortuneRule.Decision.MULTIPLY) {
				Enchanting2.LOGGER.debug("Fortune {} on {} at {}: no bonus ({})", fortune, state, pos, decision);
				return;
			}
			int multiplier = FortuneRule.multiplier(fortune, context.getRandom()::nextInt);
			List<ItemStack> multiplied = multiply(drops, multiplier);
			Enchanting2.LOGGER.debug("Fortune {} on {} at {}: x{} -> {}", fortune, state, pos, multiplier, multiplied);
			drops.clear();
			drops.addAll(multiplied);
		} catch (RuntimeException e) {
			Enchanting2.LOGGER.error("Fortune bonus failed for {} at {} in {}; the drops are left as vanilla made them",
					state, pos, level.dimension().identifier(), e);
		}
	}

	/** Never multiplied whatever the tags say: ancient debris (DESIGN §6) and mob spawners (Silk Touch collects one). */
	public static boolean excluded(BlockState state) {
		return state.is(Blocks.ANCIENT_DEBRIS) || state.is(Blocks.SPAWNER);
	}

	/** Every stack times {@code multiplier}, split at the item's max stack size. */
	public static List<ItemStack> multiply(List<ItemStack> drops, int multiplier) {
		List<ItemStack> result = new ArrayList<>();
		for (ItemStack stack : drops) {
			long remaining = (long) stack.getCount() * multiplier;
			while (remaining > 0) {
				int count = (int) Math.min(remaining, stack.getMaxStackSize());
				result.add(stack.copyWithCount(count));
				remaining -= count;
			}
		}
		return result;
	}

	/**
	 * Owner, 2026-10-08: under SILK_WINS a tagged block still multiplies unless Silk Touch changed what it drops. Rolls
	 * the block's table again with a copy of the tool without Silk Touch and compares item types. Gravel below Fortune
	 * III is the one default-tag block where that varies per roll (vanilla's flint chance).
	 */
	private static boolean silkChangedDrop(LootTable table, LootContext context, BlockState state, ItemStack tool,
			List<ItemStack> silkDrops) {
		ItemStack withoutSilk = tool.copy();
		EnchantmentHelper.updateEnchantments(withoutSilk,
				enchantments -> enchantments.removeIf(holder -> holder.is(Enchantments.SILK_TOUCH)));
		LootParams params = new LootParams.Builder(context.getLevel())
				.withParameter(LootContextParams.ORIGIN, context.getParameter(LootContextParams.ORIGIN))
				.withParameter(LootContextParams.TOOL, withoutSilk)
				.withParameter(LootContextParams.BLOCK_STATE, state)
				.withOptionalParameter(LootContextParams.BLOCK_ENTITY,
						context.getOptionalParameter(LootContextParams.BLOCK_ENTITY))
				.withOptionalParameter(LootContextParams.THIS_ENTITY,
						context.getOptionalParameter(LootContextParams.THIS_ENTITY))
				.create(LootContextParamSets.BLOCK);
		List<ItemStack> plain;
		REROLLING.set(true);
		try {
			plain = table.getRandomItems(params);
		} finally {
			REROLLING.set(false);
		}
		return !items(plain).equals(items(silkDrops));
	}

	private static Set<Item> items(List<ItemStack> stacks) {
		return stacks.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::getItem).collect(Collectors.toSet());
	}

	private static Holder<Enchantment> enchantment(ServerLevel level, ResourceKey<Enchantment> key) {
		return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}
}
