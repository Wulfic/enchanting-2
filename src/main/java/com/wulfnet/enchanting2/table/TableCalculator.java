package com.wulfnet.enchanting2.table;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.config.CommonConfig;
import com.wulfnet.enchanting2.cost.CostBreakdown;
import com.wulfnet.enchanting2.cost.CostModel;
import com.wulfnet.enchanting2.cost.CostRequest;
import com.wulfnet.enchanting2.cost.PricingTable;
import com.wulfnet.enchanting2.enchantment.EnchantRules;
import com.wulfnet.enchanting2.table.TableOutcome.Invalid;
import com.wulfnet.enchanting2.table.TableOutcome.Status;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * DESIGN §6 enchanting table on the server. Lists every offered enchantment applicable to the item, checks the player's
 * picks, prices them with {@code CostModel} and builds the enchanted item. Picks are checked in ID order against the
 * item so far, so the later of two conflicting picks is the one refused. One invalid pick means no enchant at all: the
 * server never applies a subset the player didn't pick. Never mutates its inputs; the caller logs anything it throws
 * and shows no result.
 */
public final class TableCalculator {
	private TableCalculator() {
	}

	/**
	 * @param lapisCount   lapis in the table's second slot
	 * @param shelves      valid bookshelves around the table, as vanilla counts them (more than 15 counts as 15)
	 * @param playerLevels the player's XP levels
	 * @param creative     the player has infinite materials (as at the anvil)
	 * @param pool         the offered enchantments ({@link TablePool#members}), any order
	 */
	public static TableOutcome compute(ItemStack target, int lapisCount, int shelves, TableSelection selection,
			int playerLevels, boolean creative, CommonConfig config, PricingTable pricing,
			List<Holder<Enchantment>> pool) {
		Objects.requireNonNull(target, "target");
		Objects.requireNonNull(selection, "selection");
		Objects.requireNonNull(config, "config");
		Objects.requireNonNull(pricing, "pricing");
		Objects.requireNonNull(pool, "pool");
		int counted = Math.clamp(shelves, 0, TablePower.MAX_SHELVES);
		if (target.isEmpty() || target.getCount() != 1) {
			return TableOutcome.none(counted);
		}
		if (target.is(Items.BOOK) && EnchantmentHelper.hasAnyEnchantments(target)) {
			// Only a command makes one. Enchanting it would leave entries in both components, so leave it alone.
			Enchanting2.LOGGER.debug("Table refuses a plain book that already holds enchantments: {}", target);
			return TableOutcome.none(counted);
		}
		int tier = TablePower.tier(counted);
		ItemEnchantments existing = EnchantmentHelper.getEnchantmentsForCrafting(target);

		// Applicability is EnchantRules' call (every entry for a book); level 1 is never above a max.
		Map<String, Holder<Enchantment>> listed = new LinkedHashMap<>();
		pool.stream().sorted(Comparator.comparing(Holder::getRegisteredName))
				.filter(h -> EnchantRules.check(target, h, 1, existing, config.allowTogether())
						.verdict() != EnchantRules.Verdict.NOT_APPLICABLE)
				.forEach(h -> listed.put(h.getRegisteredName(), h));
		if (listed.isEmpty()) {
			return new TableOutcome(Status.NOTHING_TO_ENCHANT, List.of(), Optional.empty(), Optional.empty(),
					ItemStack.EMPTY, counted);
		}

		ItemEnchantments after = existing;
		Optional<Invalid> invalid = Optional.empty();
		Map<String, Verdict> picked = new HashMap<>();
		for (Map.Entry<String, Integer> pick : selection.levels().entrySet()) {
			String id = pick.getKey();
			int level = pick.getValue();
			Holder<Enchantment> enchantment = listed.get(id);
			Verdict verdict = enchantment == null
					? new Verdict(TableRow.Status.NOT_OFFERED, Optional.empty())
					: pickVerdict(target, enchantment, level, existing.getLevel(enchantment), after, tier, config);
			picked.put(id, verdict);
			if (verdict.status == TableRow.Status.OK) {
				after = EnchantRules.withEnchantment(after, enchantment, level);
			} else if (invalid.isEmpty()) {
				invalid = Optional.of(new Invalid(id, level, verdict.status, verdict.conflictsWith));
			}
		}

		List<TableRow> rows = new ArrayList<>();
		for (Holder<Enchantment> enchantment : listed.values()) {
			String id = enchantment.getRegisteredName();
			int current = existing.getLevel(enchantment);
			int max = enchantment.value().getMaxLevel();
			int selected = selection.levels().getOrDefault(id, 0);
			Verdict verdict = selected > 0 ? picked.get(id)
					: unpickedVerdict(target, enchantment, current, after, tier, config);
			rows.add(new TableRow(id, current, max, TablePower.maxSelectable(max, tier), selected, verdict.status,
					verdict.conflictsWith));
		}

		if (selection.isEmpty()) {
			return new TableOutcome(Status.NO_SELECTION, rows, Optional.empty(), Optional.empty(), ItemStack.EMPTY,
					counted);
		}
		if (invalid.isPresent()) {
			return new TableOutcome(Status.INVALID_PICK, rows, invalid, Optional.empty(), ItemStack.EMPTY, counted);
		}

		Map<String, Holder<Enchantment>> holders = new HashMap<>(listed);
		for (Holder<Enchantment> held : existing.keySet()) {
			holders.put(held.getRegisteredName(), held);
		}
		CostBreakdown cost = CostModel.price(CostRequest.table(levelsById(existing), levelsById(after), creative),
				pricing, id -> holders.containsKey(id) && holders.get(id).is(EnchantmentTags.CURSE), config.costs(),
				config.curses());
		Status status;
		if (cost.tooExpensive()) {
			status = Status.TOO_EXPENSIVE;
		} else if (playerLevels < cost.levelsCharged()) {
			status = Status.INSUFFICIENT_LEVELS;
		} else if (lapisCount < cost.lapisCharged()) {
			status = Status.INSUFFICIENT_LAPIS;
		} else {
			status = Status.OK;
		}
		if (status != Status.OK) {
			return new TableOutcome(status, rows, Optional.empty(), Optional.of(cost), ItemStack.EMPTY, counted);
		}
		ItemStack result = target.is(Items.BOOK) ? target.transmuteCopy(Items.ENCHANTED_BOOK) : target.copy();
		EnchantmentHelper.setEnchantments(result, after);
		return new TableOutcome(Status.OK, rows, Optional.empty(), Optional.of(cost), result, counted);
	}

	private record Verdict(TableRow.Status status, Optional<String> conflictsWith) {
		static final Verdict OK = new Verdict(TableRow.Status.OK, Optional.empty());

		static Verdict of(TableRow.Status status) {
			return new Verdict(status, Optional.empty());
		}
	}

	/** A picked row: is {@code level} a valid pick on the item as built so far? */
	private static Verdict pickVerdict(ItemStack target, Holder<Enchantment> enchantment, int level, int current,
			ItemEnchantments after, int tier, CommonConfig config) {
		int max = enchantment.value().getMaxLevel();
		if (level > max) {
			return Verdict.of(TableRow.Status.ABOVE_MAX);
		}
		if (level <= current) {
			return Verdict.of(TableRow.Status.ALREADY_AT_LEVEL);
		}
		if (TablePool.needsFullPower(enchantment, config.table()) && !TablePower.fullPower(tier)) {
			return Verdict.of(TableRow.Status.NEEDS_FULL_POWER);
		}
		if (level > TablePower.maxSelectable(max, tier)) {
			return Verdict.of(TableRow.Status.NEEDS_POWER);
		}
		return rules(target, enchantment, level, after, config);
	}

	/** An unpicked row: could its next level be picked now? */
	private static Verdict unpickedVerdict(ItemStack target, Holder<Enchantment> enchantment, int current,
			ItemEnchantments after, int tier, CommonConfig config) {
		int max = enchantment.value().getMaxLevel();
		if (current >= max) {
			return Verdict.of(TableRow.Status.MAXED);
		}
		if (TablePool.needsFullPower(enchantment, config.table()) && !TablePower.fullPower(tier)) {
			return Verdict.of(TableRow.Status.NEEDS_FULL_POWER);
		}
		if (current + 1 > TablePower.maxSelectable(max, tier)) {
			return Verdict.of(TableRow.Status.NEEDS_POWER);
		}
		return rules(target, enchantment, current + 1, after, config);
	}

	/** {@code EnchantRules} after the table's own checks, which already ruled out everything but a conflict. */
	private static Verdict rules(ItemStack target, Holder<Enchantment> enchantment, int level, ItemEnchantments after,
			CommonConfig config) {
		EnchantRules.Result result = EnchantRules.check(target, enchantment, level, after, config.allowTogether());
		return switch (result.verdict()) {
			case OK -> Verdict.OK;
			case CONFLICT -> new Verdict(TableRow.Status.CONFLICT,
					result.conflictsWith().map(Holder::getRegisteredName));
			case INVALID_LEVEL, ABOVE_MAX, NOT_APPLICABLE, ALREADY_AT_LEVEL -> throw new IllegalStateException(
					"EnchantRules said " + result.verdict() + " for " + enchantment.getRegisteredName() + " " + level
							+ " after the table's checks passed");
		};
	}

	private static Map<String, Integer> levelsById(ItemEnchantments enchantments) {
		Map<String, Integer> levels = new HashMap<>();
		for (Object2IntMap.Entry<Holder<Enchantment>> e : enchantments.entrySet()) {
			levels.put(e.getKey().getRegisteredName(), e.getIntValue());
		}
		return levels;
	}
}
