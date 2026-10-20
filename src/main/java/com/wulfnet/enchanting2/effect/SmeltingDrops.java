package com.wulfnet.enchanting2.effect;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.enchantment.StochasticRounding;
import com.wulfnet.enchanting2.port.LootTool;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * DESIGN §9 Smelting (owner, 2026-10-09): a tool with {@link ModEffects#SMELT_DROPS} smelts every drop of the block it
 * breaks that has a furnace recipe (logs become charcoal, iron ore's raw iron becomes ingots), except items in
 * {@link #EXCLUDED}, and grants the recipes' XP like a furnace, randomly rounded. Called from {@code FortuneDrops}'
 * listener after Fortune has multiplied, and never on Fortune's own Silk-free re-roll, which would otherwise see
 * smelted drops and decide "Silk Touch changed the drop". Silk Touch wins by default ({@link SmeltingSilkPriority}).
 * Acts on the broken block's own table only. Any error leaves the drops as they were.
 */
public final class SmeltingDrops {
	/** Drops that are never smelted. Shipped empty, for datapacks. */
	public static final TagKey<Item> EXCLUDED = TagKey.create(Registries.ITEM,
			Identifier.fromNamespaceAndPath(Enchanting2.MOD_ID, "smelting_excluded"));

	private SmeltingDrops() {
	}

	/** Mutates {@code drops} and may add XP orbs at the block. */
	public static void apply(Holder<LootTable> table, LootContext context, List<ItemStack> drops,
			SmeltingSilkPriority priority) {
		BlockState state = context.getOptionalParameter(LootContextParams.BLOCK_STATE);
		ItemStack tool = LootTool.of(context);
		Vec3 origin = context.getOptionalParameter(LootContextParams.ORIGIN);
		if (drops.isEmpty() || state == null || tool == null || tool.isEmpty() || origin == null
				|| !EnchantmentHelper.has(tool, ModEffects.SMELT_DROPS)) {
			return;
		}
		Optional<ResourceKey<LootTable>> ownTable = state.getBlock().getLootTable();
		if (ownTable.isEmpty() || !table.is(ownTable.get())) {
			return;
		}
		ServerLevel level = context.getLevel();
		BlockPos pos = BlockPos.containing(origin);
		try {
			if (priority == SmeltingSilkPriority.SILK_TOUCH && EnchantmentHelper.getItemEnchantmentLevel(
					level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH),
					tool) > 0) {
				Enchanting2.LOGGER.debug("Smelting on {} at {}: Silk Touch wins, nothing smelted", state, pos);
				return;
			}
			List<ItemStack> smelted = new ArrayList<>();
			float experience = 0;
			for (ItemStack drop : drops) {
				Optional<RecipeHolder<SmeltingRecipe>> recipe = drop.isEmpty() || drop.is(EXCLUDED) ? Optional.empty()
						: level.recipeAccess().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(drop), level);
				ItemStack result = recipe
						//? if >=26.1 {
						/*.map(r -> r.value().assemble(new SingleRecipeInput(drop)))
						*///?} else
						.map(r -> r.value().assemble(new SingleRecipeInput(drop), level.registryAccess()))
						.orElse(ItemStack.EMPTY);
				if (result.isEmpty()) {
					smelted.add(drop);
					continue;
				}
				addSplit(smelted, result, (long) result.getCount() * drop.getCount());
				experience += recipe.get().value().experience() * drop.getCount();
			}
			int orbs = StochasticRounding.round(experience, context.getRandom()::nextDouble);
			Enchanting2.LOGGER.debug("Smelting on {} at {}: {} -> {}, {} XP", state, pos, drops, smelted, orbs);
			drops.clear();
			drops.addAll(smelted);
			if (orbs > 0) {
				ExperienceOrb.award(level, origin, orbs);
			}
		} catch (RuntimeException e) {
			Enchanting2.LOGGER.error("Smelting failed for {} at {} in {}; the drops are left unsmelted", state, pos,
					level.dimension().identifier(), e);
		}
	}

	/** {@code total} of {@code result}'s item, split at its max stack size. */
	private static void addSplit(List<ItemStack> out, ItemStack result, long total) {
		long remaining = total;
		while (remaining > 0) {
			int count = (int) Math.min(remaining, result.getMaxStackSize());
			out.add(result.copyWithCount(count));
			remaining -= count;
		}
	}
}
