package com.wulfnet.enchanting2.cost;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * One table or anvil operation to price: the item's enchantments before and after, by ID, plus the anvil extras. The
 * caller has already run {@code EnchantRules} and merged, so {@code after} holds one level per ID. Levels never go
 * down here and no ID disappears: taking enchantments off is the grindstone's job, priced by its own methods.
 *
 * @param repairMaterials material items used to repair (an iron ingot each); 0 if none
 * @param combineRepair   repaired by combining two of the same item
 */
public record CostRequest(Station station, Map<String, Integer> before, Map<String, Integer> after, boolean renamed,
		int repairMaterials, boolean combineRepair, boolean creative) {
	public enum Station {
		/** Charges lapis. Can't rename or repair. */
		TABLE,
		/** Never charges lapis (decisions.md 2026-10-06). */
		ANVIL
	}

	public CostRequest {
		Objects.requireNonNull(station, "station");
		before = sortedCopy(before, "before");
		after = sortedCopy(after, "after");
		for (Map.Entry<String, Integer> e : before.entrySet()) {
			Integer to = after.get(e.getKey());
			if (to == null || to < e.getValue()) {
				throw new IllegalArgumentException(e.getKey() + " goes from " + e.getValue() + " to " + to
						+ "; removing or lowering an enchantment isn't a table or anvil operation");
			}
		}
		if (repairMaterials < 0) {
			throw new IllegalArgumentException("repairMaterials = " + repairMaterials);
		}
		if (repairMaterials > 0 && combineRepair) {
			throw new IllegalArgumentException("a repair uses materials or a second item, not both");
		}
		if (station == Station.TABLE && (renamed || repairMaterials > 0 || combineRepair)) {
			throw new IllegalArgumentException("the enchanting table can't rename or repair");
		}
	}

	/** Enchanting only, as the table does it. */
	public static CostRequest table(Map<String, Integer> before, Map<String, Integer> after, boolean creative) {
		return new CostRequest(Station.TABLE, before, after, false, 0, false, creative);
	}

	/** An anvil operation with no rename or repair. */
	public static CostRequest anvil(Map<String, Integer> before, Map<String, Integer> after) {
		return new CostRequest(Station.ANVIL, before, after, false, 0, false, false);
	}

	private static Map<String, Integer> sortedCopy(Map<String, Integer> levels, String name) {
		Objects.requireNonNull(levels, name);
		levels.forEach((id, level) -> {
			Objects.requireNonNull(id, name + " ID");
			if (level == null || level < 1) {
				throw new IllegalArgumentException(name + ": " + id + " at level " + level);
			}
		});
		// Sorted, so the breakdown lists lines in the same order on server and client.
		return Collections.unmodifiableMap(new TreeMap<>(levels));
	}
}
