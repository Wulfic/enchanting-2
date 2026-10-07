package com.wulfnet.enchanting2.cost;

import com.wulfnet.enchanting2.config.CostsSection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * DESIGN §5: one cost model for the table and the anvil. Pure: callers resolve stacks, tags and config into a
 * {@link CostRequest}, a {@link PricingTable} and an {@code isCurse} test.
 *
 * <pre>
 * enchant_cost = Σ over each enchantment added or raised: base_cost (if new) + cost_per_level × levels charged
 *              + Σ over each X with other_levels_surcharge s: s × O_out (X new) or s × (O_out − O_in)
 * total        = enchant_cost + rename + repair
 * lapis        = min(enchant_cost, 64), table only
 * </pre>
 * {@code O} is the other non-curse levels on the item. Charging {@code s × O_out} when X is new makes X's lifetime
 * surcharge {@code s × O_final} however the item was built, so applying Mending first doesn't dodge it.
 * Curses cost nothing themselves and never count in {@code O}; their value is the §5a discount.
 */
public final class CostModel {
	public static final int MAX_LAPIS = 64;

	private CostModel() {
	}

	public static CostBreakdown price(CostRequest request, PricingTable pricing, Predicate<String> isCurse,
			CostsSection costs) {
		Objects.requireNonNull(request, "request");
		Objects.requireNonNull(pricing, "pricing");
		Objects.requireNonNull(isCurse, "isCurse");
		Objects.requireNonNull(costs, "costs");

		List<CostBreakdown.Line> lines = new ArrayList<>();
		long enchantCost = 0;
		for (Map.Entry<String, Integer> e : request.after().entrySet()) {
			String id = e.getKey();
			int to = e.getValue();
			int from = request.before().getOrDefault(id, 0);
			if (to == from) {
				continue; // unchanged, including EnchantRules' ALREADY_AT_LEVEL: free
			}
			long amount = isCurse.test(id) ? 0 : levelCost(pricing.get(id), from, to);
			lines.add(new CostBreakdown.Line(CostBreakdown.Kind.ENCHANT, id, from, to, CostBreakdown.saturate(amount)));
			enchantCost += amount;
		}

		long otherIn = nonCurseLevels(request.before(), isCurse);
		long otherOut = nonCurseLevels(request.after(), isCurse);
		for (Map.Entry<String, Integer> e : request.after().entrySet()) {
			String id = e.getKey();
			int surcharge = pricing.get(id).otherLevelsSurcharge();
			if (surcharge == 0 || isCurse.test(id)) {
				continue;
			}
			Integer fromLevel = request.before().get(id);
			long oIn = fromLevel == null ? 0 : otherIn - fromLevel;
			long oOut = otherOut - e.getValue();
			long amount = surcharge * (oOut - oIn);
			if (amount > 0) {
				lines.add(new CostBreakdown.Line(CostBreakdown.Kind.SURCHARGE, id, CostBreakdown.saturate(oIn),
						CostBreakdown.saturate(oOut), CostBreakdown.saturate(amount)));
				enchantCost += amount;
			}
		}

		long total = enchantCost;
		if (request.renamed()) {
			lines.add(new CostBreakdown.Line(CostBreakdown.Kind.RENAME, "", 0, 0, costs.renameCost()));
			total += costs.renameCost();
		}
		if (request.repairMaterials() > 0) {
			long amount = (long) costs.repairCostPerMaterial() * request.repairMaterials();
			lines.add(new CostBreakdown.Line(CostBreakdown.Kind.REPAIR, "", 0, request.repairMaterials(),
					CostBreakdown.saturate(amount)));
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
