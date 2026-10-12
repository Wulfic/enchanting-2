package com.wulfnet.enchanting2.cost;

import com.wulfnet.enchanting2.config.CostsSection;
import com.wulfnet.enchanting2.config.CursesSection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * DESIGN §5 and §5a: one cost model for the table, the anvil and the grindstone. Pure: callers resolve stacks, tags and
 * config into a {@link CostRequest}, a {@link PricingTable} and an {@code isCurse} test.
 *
 * <pre>
 * enchant_cost = Σ over each enchantment added or raised: base_cost (if new) + cost_per_level × levels charged
 *              + Σ over each X with other_levels_surcharge s: s × O_out (X new) or s × (O_out − O_in)
 *              then × (100 − curse discount %) / 100, rounded up
 * total        = enchant_cost + rename + repair
 * lapis        = min(enchant_cost, 64), table only
 * </pre>
 * {@code O} is the other non-curse levels on the item. Charging {@code s × O_out} when X is new makes X's lifetime
 * surcharge {@code s × O_final} however the item was built, so applying Mending first doesn't dodge it.
 * Curses cost nothing themselves and never count in {@code O}; their value is the discount.
 */
public final class CostModel {
	public static final int MAX_LAPIS = 64;

	private CostModel() {
	}

	public static CostBreakdown price(CostRequest request, PricingTable pricing, Predicate<String> isCurse,
			CostsSection costs, CursesSection curses) {
		Objects.requireNonNull(request, "request");
		Objects.requireNonNull(pricing, "pricing");
		Objects.requireNonNull(isCurse, "isCurse");
		Objects.requireNonNull(costs, "costs");
		Objects.requireNonNull(curses, "curses");

		List<CostBreakdown.Line> lines = new ArrayList<>();
		long enchantCost = enchantLines(request.before(), request.after(), pricing, isCurse, lines);

		int rawPercent = 0;
		for (Map.Entry<String, Integer> e : request.after().entrySet()) {
			if (isCurse.test(e.getKey())) {
				rawPercent += pricing.get(e.getKey()).discountAt(e.getValue());
			}
		}
		int percent = Math.min(rawPercent, curses.maxDiscountPercent());
		if (percent > 0 && enchantCost > 0) {
			long discounted = ceilPercent(enchantCost, 100 - percent);
			lines.add(new CostBreakdown.Line(CostBreakdown.Kind.CURSE_DISCOUNT, "", rawPercent, percent,
					discounted - enchantCost));
			enchantCost = discounted;
		}

		long total = enchantCost;
		if (request.renamed()) {
			lines.add(new CostBreakdown.Line(CostBreakdown.Kind.RENAME, "", 0, 0, costs.renameCost()));
			total += costs.renameCost();
		}
		if (request.repairMaterials() > 0) {
			long amount = (long) costs.repairCostPerMaterial() * request.repairMaterials();
			lines.add(new CostBreakdown.Line(CostBreakdown.Kind.REPAIR, "", 0, request.repairMaterials(), amount));
			total += amount;
		} else if (request.combineRepair()) {
			lines.add(new CostBreakdown.Line(CostBreakdown.Kind.REPAIR, "", 0, 0, costs.repairCombineCost()));
			total += costs.repairCombineCost();
		}

		int enchant = CostBreakdown.saturate(enchantCost);
		int levels = CostBreakdown.saturate(total);
		int lapis = request.station() == CostRequest.Station.TABLE ? Math.min(enchant, MAX_LAPIS) : 0;
		boolean free = request.creative() && costs.creativeFree();
		// Vanilla never refuses a creative player as too expensive, free or not.
		boolean tooExpensive = !request.creative() && costs.hasMaximum() && levels > costs.maximumOperationCost();
		return new CostBreakdown(lines, enchant, levels, lapis, free, tooExpensive);
	}

	/**
	 * DESIGN §5a Cleanse: XP levels to remove {@code curseId} from an item holding {@code current}. The player repays the
	 * removed curse's own percent at its level (owner, 2026-10-07; per level, Phase 10e) of the undiscounted cost of the
	 * item's non-curse enchantments, and at least {@code removal_min_cost}. Creative is the caller's call
	 * ({@code creative_free}), as with Extract.
	 */
	public static int cleanseCost(Map<String, Integer> current, String curseId, PricingTable pricing,
			Predicate<String> isCurse, CursesSection curses) {
		Objects.requireNonNull(curses, "curses");
		if (!isCurse.test(curseId)) {
			throw new IllegalArgumentException(curseId + " isn't a curse; Cleanse only removes curses");
		}
		if (!current.containsKey(curseId)) {
			throw new IllegalArgumentException(curseId + " isn't on the item");
		}
		Map<String, Integer> enchantments = new TreeMap<>();
		current.forEach((id, level) -> {
			if (!isCurse.test(id)) {
				enchantments.put(id, level);
			}
		});
		CostRequest build = CostRequest.anvil(Map.of(), enchantments); // validates the levels
		long buildCost = enchantLines(build.before(), build.after(), pricing, isCurse, new ArrayList<>());
		long repaid = ceilPercent(buildCost, pricing.get(curseId).discountAt(current.get(curseId)));
		return CostBreakdown.saturate(Math.max(curses.removalMinCost(), repaid));
	}

	/**
	 * DESIGN §6 Extract: priced as adding the enchantment at its current level to a blank item, so no surcharge
	 * (Efficiency V = 5, Mending = 2). Curses can't be extracted.
	 */
	public static int extractCost(String id, int level, PricingTable pricing, Predicate<String> isCurse) {
		if (isCurse.test(id)) {
			throw new IllegalArgumentException(id + " is a curse; curses are cleansed, not extracted");
		}
		if (level < 1) {
			throw new IllegalArgumentException(id + " at level " + level);
		}
		return CostBreakdown.saturate(levelCost(pricing.get(id), 0, level));
	}

	/** The ENCHANT and SURCHARGE lines, added to {@code lines}; returns their sum. */
	private static long enchantLines(Map<String, Integer> before, Map<String, Integer> after, PricingTable pricing,
			Predicate<String> isCurse, List<CostBreakdown.Line> lines) {
		long sum = 0;
		for (Map.Entry<String, Integer> e : after.entrySet()) {
			String id = e.getKey();
			int to = e.getValue();
			int from = before.getOrDefault(id, 0);
			if (to == from) {
				continue; // unchanged, including EnchantRules' ALREADY_AT_LEVEL: free
			}
			long amount = isCurse.test(id) ? 0 : levelCost(pricing.get(id), from, to);
			lines.add(new CostBreakdown.Line(CostBreakdown.Kind.ENCHANT, id, from, to, amount));
			sum += amount;
		}

		long otherIn = nonCurseLevels(before, isCurse);
		long otherOut = nonCurseLevels(after, isCurse);
		for (Map.Entry<String, Integer> e : after.entrySet()) {
			String id = e.getKey();
			int surcharge = pricing.get(id).otherLevelsSurcharge();
			if (surcharge == 0 || isCurse.test(id)) {
				continue;
			}
			Integer fromLevel = before.get(id);
			long oIn = fromLevel == null ? 0 : otherIn - fromLevel;
			long oOut = otherOut - e.getValue();
			long amount = surcharge * (oOut - oIn);
			if (amount > 0) {
				lines.add(new CostBreakdown.Line(CostBreakdown.Kind.SURCHARGE, id, CostBreakdown.saturate(oIn),
						CostBreakdown.saturate(oOut), amount));
				sum += amount;
			}
		}
		return sum;
	}

	/** Base cost (new only) plus the levels charged by the upgrade mode. A new enchantment pays its result level. */
	private static long levelCost(Pricing p, int from, int to) {
		if (from == 0) {
			return p.baseCost() + (long) p.costPerLevel() * to;
		}
		return switch (p.upgradeMode()) {
			case LEVEL_DIFFERENCE -> (long) p.costPerLevel() * (to - from);
			case RESULT_LEVEL -> (long) p.costPerLevel() * to;
			case FIXED -> p.costPerLevel();
			case FREE -> 0;
		};
	}

	/** {@code ceil(value × percent / 100)} for non-negative values, exact in integers. */
	private static long ceilPercent(long value, int percent) {
		return Math.ceilDiv(value * percent, 100);
	}

	private static long nonCurseLevels(Map<String, Integer> levels, Predicate<String> isCurse) {
		long sum = 0;
		for (Map.Entry<String, Integer> e : levels.entrySet()) {
			if (!isCurse.test(e.getKey())) {
				sum += e.getValue();
			}
		}
		return sum;
	}
}
