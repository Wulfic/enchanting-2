package com.wulfnet.enchanting2.config;

import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;

/**
 * DESIGN §5: the costs that aren't per enchantment. Repair is vanilla without the prior-work penalty (owner,
 * 2026-10-07). The ranges are public so the config screen uses the same bounds as the loader.
 *
 * @param maximumOperationCost totals above this are "Too Expensive!"; {@link #NO_MAXIMUM} = no limit
 */
public record CostsSection(int renameCost, int repairCostPerMaterial, int repairCombineCost, int maximumOperationCost,
		boolean creativeFree) {
	public static final int MIN_COST = 0;
	public static final int MAX_COST = 1_000_000;
	public static final int NO_MAXIMUM = -1;

	public static final CostsSection DEFAULTS = new CostsSection(1, 1, 2, NO_MAXIMUM, true);

	public CostsSection {
		requireInRange("rename_cost", renameCost, MIN_COST, MAX_COST);
		requireInRange("repair_cost_per_material", repairCostPerMaterial, MIN_COST, MAX_COST);
		requireInRange("repair_combine_cost", repairCombineCost, MIN_COST, MAX_COST);
		requireInRange("maximum_operation_cost", maximumOperationCost, NO_MAXIMUM, MAX_COST);
	}

	public boolean hasMaximum() {
		return maximumOperationCost != NO_MAXIMUM;
	}

	static CostsSection read(ConfigReader r) {
		return new CostsSection(
				r.readInt("rename_cost", DEFAULTS.renameCost, MIN_COST, MAX_COST),
				r.readInt("repair_cost_per_material", DEFAULTS.repairCostPerMaterial, MIN_COST, MAX_COST),
				r.readInt("repair_combine_cost", DEFAULTS.repairCombineCost, MIN_COST, MAX_COST),
				r.readInt("maximum_operation_cost", DEFAULTS.maximumOperationCost, NO_MAXIMUM, MAX_COST),
				r.readBool("creative_free", DEFAULTS.creativeFree));
	}

	JsonObject write() {
		JsonObject o = new JsonObject();
		o.put("rename_cost", JsonPrimitive.of((long) renameCost), "XP levels to rename at the anvil.");
		o.put("repair_cost_per_material", JsonPrimitive.of((long) repairCostPerMaterial),
				"XP levels per material item used to repair (e.g. one per iron ingot).");
		o.put("repair_combine_cost", JsonPrimitive.of((long) repairCombineCost),
				"XP levels to repair by combining two of the same item. Enchantments moved across are priced on top.");
		o.put("maximum_operation_cost", JsonPrimitive.of((long) maximumOperationCost),
				"Totals above this are refused (\"Too Expensive!\"). -1 = no limit. Creative players are never refused.");
		o.put("creative_free", JsonPrimitive.of(creativeFree), "true = creative players pay nothing.");
		return o;
	}

	private static void requireInRange(String key, int value, int min, int max) {
		if (value < min || value > max) {
			throw new IllegalArgumentException(key + " = " + value + " is outside " + min + ".." + max);
		}
	}
}
