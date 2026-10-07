package com.wulfnet.enchanting2.grindstone;

import com.wulfnet.enchanting2.config.CommonConfig;
import com.wulfnet.enchanting2.config.CostsSection;
import com.wulfnet.enchanting2.cost.CostModel;
import com.wulfnet.enchanting2.cost.PricingTable;
import com.wulfnet.enchanting2.grindstone.GrindstoneOutcome.Status;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * DESIGN §6 grindstone on the server. Lists what the player can pick for the current inputs and works out the result
 * of the pick. Extract and Cleanse are priced by {@code CostModel}; Disenchant and Repair are vanilla's own result
 * ({@code vanillaResult}), free as in vanilla. Either slot can hold the source. Never mutates its inputs.
 *
 * <p>A plain book only ever does Extract: vanilla's slots never accepted one, so it has no vanilla action.
 */
public final class GrindstoneCalculator {
	private GrindstoneCalculator() {
	}

	/**
	 * @param choice        the player's pick, if any; nothing is produced without one (owner, 2026-10-07)
	 * @param creative      the player has infinite materials (as at the anvil)
	 * @param vanillaResult vanilla's {@code GrindstoneMenu.computeResult(top, bottom)}
	 */
	public static GrindstoneOutcome compute(ItemStack slot0, ItemStack slot1, Optional<GrindstoneChoice> choice,
			boolean creative, CommonConfig config, PricingTable pricing, BinaryOperator<ItemStack> vanillaResult) {
		Objects.requireNonNull(slot0, "slot0");
		Objects.requireNonNull(slot1, "slot1");
		Objects.requireNonNull(choice, "choice");
		Objects.requireNonNull(config, "config");
		Objects.requireNonNull(pricing, "pricing");
		Objects.requireNonNull(vanillaResult, "vanillaResult");
		if (slot0.isEmpty() && slot1.isEmpty()) {
			return GrindstoneOutcome.refused(Status.NONE, List.of(), choice);
		}
		if (slot0.isEmpty() || slot1.isEmpty()) {
			return single(slot0, slot1, choice, creative, config, pricing, vanillaResult);
		}
		if (isPlainBook(slot1) && EnchantmentHelper.hasAnyEnchantments(slot0)) {
			return extract(slot0, slot1, 0, choice, creative, config, pricing);
		}
		if (isPlainBook(slot0) && EnchantmentHelper.hasAnyEnchantments(slot1)) {
			return extract(slot1, slot0, 1, choice, creative, config, pricing);
		}
		return repair(slot0, slot1, choice, vanillaResult);
	}

	/** One input: Disenchant (vanilla) and one Cleanse row per curse. */
	private static GrindstoneOutcome single(ItemStack slot0, ItemStack slot1, Optional<GrindstoneChoice> choice,
			boolean creative, CommonConfig config, PricingTable pricing, BinaryOperator<ItemStack> vanillaResult) {
		ItemStack source = slot0.isEmpty() ? slot1 : slot0;
		Entries entries = Entries.of(source);
		if (entries.sorted.isEmpty()) {
			return GrindstoneOutcome.refused(Status.NONE, List.of(), choice);
		}
		if (source.getCount() > 1) {
			return GrindstoneOutcome.refused(Status.STACKED_ITEM, List.of(), choice);
		}

		List<GrindstoneOption> options = new ArrayList<>();
		// Vanilla gives a curse-only item a "result" that changes nothing but its prior-work cost; not worth a row.
		ItemStack disenchanted = entries.hasNonCurse() ? vanillaResult.apply(slot0, slot1) : ItemStack.EMPTY;
		if (!disenchanted.isEmpty()) {
			options.add(new GrindstoneOption(GrindstoneChoice.of(GrindstoneAction.DISENCHANT), 0, 0, false, Status.OK));
		}
		for (Object2IntMap.Entry<Holder<Enchantment>> e : entries.sorted) {
			String id = idOf(e.getKey());
			if (entries.isCurse(id)) {
				int cost = CostModel.cleanseCost(entries.levels, id, pricing, entries::isCurse, config.curses());
				options.add(priced(GrindstoneChoice.of(GrindstoneAction.CLEANSE, id), e.getIntValue(), cost, creative,
						config.costs()));
			}
		}
		options.sort(PANEL_ORDER);

		Optional<GrindstoneOption> picked = pick(options, choice);
		if (picked.isEmpty() || !picked.get().ok()) {
			return refusal(options, choice, picked);
		}
		GrindstoneOption option = picked.get();
		ItemStack result;
		if (option.choice().action() == GrindstoneAction.DISENCHANT) {
			result = disenchanted;
		} else {
			result = source.copy();
			Holder<Enchantment> curse = entries.holder(option.choice().enchantment().orElseThrow());
			EnchantmentHelper.updateEnchantments(result, m -> m.set(curse, 0));
		}
		return new GrindstoneOutcome(Status.OK, options, choice, result, ItemStack.EMPTY, ItemStack.EMPTY,
				option.levelsCharged());
	}

	/** An enchanted item or book plus plain books: one Extract row per entry, curses refused. */
	private static GrindstoneOutcome extract(ItemStack source, ItemStack books, int sourceSlot,
			Optional<GrindstoneChoice> choice, boolean creative, CommonConfig config, PricingTable pricing) {
		if (source.getCount() > 1) {
			return GrindstoneOutcome.refused(Status.STACKED_ITEM, List.of(), choice);
		}
		Entries entries = Entries.of(source);
		boolean onlyEntryOnABook = source.is(Items.ENCHANTED_BOOK) && entries.sorted.size() == 1;

		List<GrindstoneOption> options = new ArrayList<>();
		for (Object2IntMap.Entry<Holder<Enchantment>> e : entries.sorted) {
			String id = idOf(e.getKey());
			GrindstoneChoice row = GrindstoneChoice.of(GrindstoneAction.EXTRACT, id);
			int level = e.getIntValue();
			if (entries.isCurse(id)) {
				options.add(new GrindstoneOption(row, level, 0, false, Status.CURSE_NOT_EXTRACTABLE));
			} else if (onlyEntryOnABook) {
				options.add(new GrindstoneOption(row, level, 0, false, Status.NOTHING_TO_SPLIT));
			} else {
				int cost = CostModel.extractCost(id, level, pricing, entries::isCurse);
				options.add(priced(row, level, cost, creative, config.costs()));
			}
		}

		Optional<GrindstoneOption> picked = pick(options, choice);
		if (picked.isEmpty() || !picked.get().ok()) {
			return refusal(options, choice, picked);
		}
		GrindstoneOption option = picked.get();
		Holder<Enchantment> moved = entries.holder(option.choice().enchantment().orElseThrow());
		ItemStack result = EnchantmentHelper.createBook(new EnchantmentInstance(moved, option.level()));
		ItemStack sourceAfter = source.copy();
		EnchantmentHelper.updateEnchantments(sourceAfter, m -> m.set(moved, 0));
		ItemStack booksAfter = books.getCount() > 1 ? books.copyWithCount(books.getCount() - 1) : ItemStack.EMPTY;
		return new GrindstoneOutcome(Status.OK, options, choice, result, sourceSlot == 0 ? sourceAfter : booksAfter,
				sourceSlot == 0 ? booksAfter : sourceAfter, option.levelsCharged());
	}

	/** Two items, neither a plain book: vanilla's combine, offered as one row when vanilla has a result. */
	private static GrindstoneOutcome repair(ItemStack slot0, ItemStack slot1, Optional<GrindstoneChoice> choice,
			BinaryOperator<ItemStack> vanillaResult) {
		if (isPlainBook(slot0) || isPlainBook(slot1)) {
			return GrindstoneOutcome.refused(Status.NONE, List.of(), choice);
		}
		ItemStack merged = vanillaResult.apply(slot0, slot1);
		if (merged.isEmpty()) {
			return GrindstoneOutcome.refused(Status.NONE, List.of(), choice);
		}
		List<GrindstoneOption> options = List.of(
				new GrindstoneOption(GrindstoneChoice.of(GrindstoneAction.REPAIR), 0, 0, false, Status.OK));
		Optional<GrindstoneOption> picked = pick(options, choice);
		if (picked.isEmpty()) {
			return refusal(options, choice, picked);
		}
		return new GrindstoneOutcome(Status.OK, options, choice, merged, ItemStack.EMPTY, ItemStack.EMPTY, 0);
	}

	private static final Comparator<GrindstoneOption> PANEL_ORDER = Comparator
			.comparing((GrindstoneOption o) -> o.choice().action())
			.thenComparing(o -> o.choice().enchantment().orElse(""));

	private static Optional<GrindstoneOption> pick(List<GrindstoneOption> options, Optional<GrindstoneChoice> choice) {
		return choice.flatMap(c -> options.stream().filter(o -> o.choice().equals(c)).findFirst());
	}

	private static GrindstoneOutcome refusal(List<GrindstoneOption> options, Optional<GrindstoneChoice> choice,
			Optional<GrindstoneOption> picked) {
		Status status;
		if (choice.isEmpty()) {
			status = Status.NO_CHOICE;
		} else {
			status = picked.map(GrindstoneOption::status).orElse(Status.NOT_AVAILABLE);
		}
		return GrindstoneOutcome.refused(status, options, choice);
	}

	/** Same rules as the anvil: creative is never too expensive, and pays nothing with {@code creative_free}. */
	private static GrindstoneOption priced(GrindstoneChoice row, int level, int cost, boolean creative,
			CostsSection costs) {
		boolean tooExpensive = !creative && costs.hasMaximum() && cost > costs.maximumOperationCost();
		return new GrindstoneOption(row, level, cost, creative && costs.creativeFree(),
				tooExpensive ? Status.TOO_EXPENSIVE : Status.OK);
	}

	/** A plain book: the only thing Extract writes onto. Never one that somehow holds enchantments. */
	public static boolean isPlainBook(ItemStack stack) {
		return stack.is(Items.BOOK) && !EnchantmentHelper.hasAnyEnchantments(stack);
	}

	private static String idOf(Holder<Enchantment> enchantment) {
		return enchantment.getRegisteredName();
	}

	/** A source's enchantments (stored ones for a book), in ID order so the rows never depend on component order. */
	private record Entries(List<Object2IntMap.Entry<Holder<Enchantment>>> sorted, Map<String, Integer> levels,
			Map<String, Holder<Enchantment>> holders) {
		static Entries of(ItemStack source) {
			ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(source);
			List<Object2IntMap.Entry<Holder<Enchantment>>> sorted = new ArrayList<>(enchantments.entrySet());
			sorted.sort(Comparator.comparing(e -> idOf(e.getKey())));
			Map<String, Integer> levels = new HashMap<>();
			Map<String, Holder<Enchantment>> holders = new HashMap<>();
			for (Object2IntMap.Entry<Holder<Enchantment>> e : sorted) {
				levels.put(idOf(e.getKey()), e.getIntValue());
				holders.put(idOf(e.getKey()), e.getKey());
			}
			return new Entries(sorted, levels, holders);
		}

		boolean isCurse(String id) {
			Holder<Enchantment> holder = holders.get(id);
			return holder != null && holder.is(EnchantmentTags.CURSE);
		}

		boolean hasNonCurse() {
			return holders.keySet().stream().anyMatch(Predicate.not(this::isCurse));
		}

		Holder<Enchantment> holder(String id) {
			return Objects.requireNonNull(holders.get(id), id);
		}
	}
}
