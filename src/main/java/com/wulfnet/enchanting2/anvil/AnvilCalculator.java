package com.wulfnet.enchanting2.anvil;

import com.wulfnet.enchanting2.anvil.AnvilOutcome.Entry;
import com.wulfnet.enchanting2.anvil.AnvilOutcome.Status;
import com.wulfnet.enchanting2.anvil.TransferRules.Fate;
import com.wulfnet.enchanting2.anvil.TransferRules.Source;
import com.wulfnet.enchanting2.config.CommonConfig;
import com.wulfnet.enchanting2.cost.CostBreakdown;
import com.wulfnet.enchanting2.cost.CostModel;
import com.wulfnet.enchanting2.cost.CostRequest;
import com.wulfnet.enchanting2.cost.PricingTable;
import com.wulfnet.enchanting2.enchantment.EnchantRules;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;

/**
 * DESIGN §6 Anvil on the server: {@code EnchantRules} decides each enchantment, {@link TransferRules} what happens to
 * it, {@code CostModel} the price. Repair and rename keep vanilla's mechanics with their own flat costs; the prior-work
 * {@code repair_cost} component is never read or written (owner, decisions.md 2026-10-07 "Anvil"). Never mutates its
 * inputs.
 */
public final class AnvilCalculator {
	private AnvilCalculator() {
	}

	/**
	 * @param itemName the name typed into the anvil, as vanilla's {@code AnvilMenu.itemName} (null = never typed)
	 * @param creative the player has infinite materials (vanilla's test for creative at the anvil)
	 */
	public static AnvilOutcome compute(ItemStack left, ItemStack right, @Nullable String itemName, boolean creative,
			CommonConfig config, PricingTable pricing) {
		Objects.requireNonNull(left, "left");
		Objects.requireNonNull(right, "right");
		Objects.requireNonNull(config, "config");
		Objects.requireNonNull(pricing, "pricing");
		// A plain book is enchanted at the table (Phase 7.4); vanilla's anvil gives no result for it either.
		if (left.isEmpty() || left.is(Items.BOOK) || !EnchantmentHelper.canStoreEnchantments(left)) {
			return AnvilOutcome.none();
		}

		ItemStack result = left.copy();
		int materials = 0;
		boolean combineRepair = false;
		Source source = null;
		if (!right.isEmpty()) {
			if (result.isDamageableItem() && left.isValidRepairItem(right)) {
				materials = repairWithMaterials(result, right);
				if (materials == 0) {
					return AnvilOutcome.none();
				}
			} else if (right.has(DataComponents.STORED_ENCHANTMENTS)) {
				source = Source.BOOK;
			} else if (right.is(left.getItem()) && result.isDamageableItem()) {
				source = Source.ITEM;
				combineRepair = repairByCombining(result, right);
			} else {
				return AnvilOutcome.none();
			}
		}

		ItemEnchantments before = EnchantmentHelper.getEnchantmentsForCrafting(left);
		ItemEnchantments after = before;
		List<Entry> entries = new ArrayList<>();
		ItemStack rightAfterTake = materials > 0 ? shrunk(right, materials) : right.copy();
		if (source != null) {
			// Taking the result empties or replaces the whole right slot, so a stack of books or items would lose the
			// rest. Vanilla enchanted books don't stack; this only guards against other mods' stacking changes.
			if (right.getCount() > 1) {
				return AnvilOutcome.refused(Status.STACKED_ITEM, Optional.empty(), List.of());
			}
			InvalidBookMode mode = config.anvil().invalidBookMode();
			ItemEnchantments.Mutable kept = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
			boolean applied = false;
			boolean refused = false;
			for (Object2IntMap.Entry<Holder<Enchantment>> e : inApplyOrder(EnchantmentHelper.getEnchantmentsForCrafting(right))) {
				Holder<Enchantment> enchantment = e.getKey();
				int level = e.getIntValue();
				EnchantRules.Result check = EnchantRules.check(left, enchantment, level, after, config.allowTogether());
				Fate fate = TransferRules.decide(source, mode, check.verdict());
				switch (fate) {
					case APPLY -> {
						after = EnchantRules.withEnchantment(after, enchantment, level);
						applied = true;
					}
					case KEEP_ON_BOOK -> kept.set(enchantment, level);
					case REFUSE -> refused = true;
					case CONSUME_DOMINATED, LOSE -> {
					}
				}
				entries.add(new Entry(idOf(enchantment), level, check.verdict(),
						check.conflictsWith().map(AnvilCalculator::idOf), fate));
			}
			if (refused) {
				return AnvilOutcome.refused(Status.REFUSED, Optional.empty(), entries);
			}
			if (applied && left.getCount() > 1) {
				return AnvilOutcome.refused(Status.STACKED_ITEM, Optional.empty(), entries);
			}
			// The right-hand stack is used only if something came from it: an applied entry, or its durability.
			// A book that gave nothing (say, renaming with a book in the slot) goes back untouched in every mode.
			if (applied || combineRepair) {
				if (source == Source.BOOK && !kept.keySet().isEmpty()) {
					rightAfterTake = right.copy();
					EnchantmentHelper.setEnchantments(rightAfterTake, kept.toImmutable());
				} else {
					rightAfterTake = ItemStack.EMPTY;
				}
			}
		}

		boolean renamed = rename(result, left, itemName);
		boolean enchanted = !after.equals(before);
		if (!enchanted && materials == 0 && !combineRepair && !renamed) {
			return source == null ? AnvilOutcome.none() : AnvilOutcome.refused(Status.NOTHING_TO_DO, Optional.empty(),
					entries);
		}

		Map<String, Boolean> curses = new HashMap<>();
		CostRequest request = new CostRequest(CostRequest.Station.ANVIL, levelsById(before, curses),
				levelsById(after, curses), renamed, materials, combineRepair, creative);
		CostBreakdown breakdown = CostModel.price(request, pricing, id -> curses.getOrDefault(id, false), config.costs(),
				config.curses());
		if (breakdown.tooExpensive()) {
			return AnvilOutcome.refused(Status.TOO_EXPENSIVE, Optional.of(breakdown), entries);
		}
		if (enchanted) {
			EnchantmentHelper.setEnchantments(result, after);
		}
		return new AnvilOutcome(Status.OK, result, rightAfterTake, materials, Optional.of(breakdown), entries);
	}

	/** Vanilla's material repair: up to a quarter of max durability per item. Returns the number of items used. */
	private static int repairWithMaterials(ItemStack result, ItemStack materials) {
		int step = Math.min(result.getDamageValue(), result.getMaxDamage() / 4);
		int used = 0;
		while (step > 0 && used < materials.getCount()) {
			result.setDamageValue(result.getDamageValue() - step);
			used++;
			step = Math.min(result.getDamageValue(), result.getMaxDamage() / 4);
		}
		return used;
	}

	private static ItemStack shrunk(ItemStack stack, int by) {
		return stack.getCount() > by ? stack.copyWithCount(stack.getCount() - by) : ItemStack.EMPTY;
	}

	/** Vanilla's two-item repair: both durabilities plus 12% of max. Returns whether durability went up. */
	private static boolean repairByCombining(ItemStack result, ItemStack sacrifice) {
		int left = result.getMaxDamage() - result.getDamageValue();
		int right = sacrifice.getMaxDamage() - sacrifice.getDamageValue();
		int damage = Math.max(0, result.getMaxDamage() - (left + right + result.getMaxDamage() * 12 / 100));
		if (damage < result.getDamageValue()) {
			result.setDamageValue(damage);
			return true;
		}
		return false;
	}

	/** Vanilla's rename: a typed name that differs from the item's, or clearing a custom name. */
	private static boolean rename(ItemStack result, ItemStack left, @Nullable String itemName) {
		if (itemName != null && !StringUtil.isBlank(itemName)) {
			if (!itemName.equals(left.getHoverName().getString())) {
				result.set(DataComponents.CUSTOM_NAME, Component.literal(itemName));
				return true;
			}
			return false;
		}
		if (left.has(DataComponents.CUSTOM_NAME)) {
			result.remove(DataComponents.CUSTOM_NAME);
			return true;
		}
		return false;
	}

	/** Book and sacrifice entries in {@link EnchantRules#APPLY_ORDER}, so the outcome never depends on component order. */
	private static List<Object2IntMap.Entry<Holder<Enchantment>>> inApplyOrder(ItemEnchantments enchantments) {
		List<Object2IntMap.Entry<Holder<Enchantment>>> sorted = new ArrayList<>(enchantments.entrySet());
		sorted.sort(Comparator.comparing(Object2IntMap.Entry::getKey, EnchantRules.APPLY_ORDER));
		return sorted;
	}

	private static Map<String, Integer> levelsById(ItemEnchantments enchantments, Map<String, Boolean> curses) {
		Map<String, Integer> levels = new HashMap<>();
		for (Object2IntMap.Entry<Holder<Enchantment>> e : enchantments.entrySet()) {
			String id = idOf(e.getKey());
			levels.put(id, e.getIntValue());
			curses.put(id, e.getKey().is(EnchantmentTags.CURSE));
		}
		return levels;
	}

	private static String idOf(Holder<Enchantment> enchantment) {
		return enchantment.getRegisteredName();
	}
}
