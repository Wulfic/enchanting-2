package com.wulfnet.enchanting2.table;

/**
 * DESIGN §6 bookshelf power, pure. Vanilla's placement rules and 15-shelf maximum are kept; the shelf count comes from
 * vanilla's own {@code EnchantingTableBlock.isValidBookShelf}. 15 shelves unlocks every enchantment's max level.
 */
public final class TablePower {
	/** Vanilla's maximum; more shelves around the table count as this many. */
	public static final int MAX_SHELVES = 15;
	public static final int MAX_TIER = 5;

	private TablePower() {
	}

	/** {@code 1 + floor(shelves × 4 / 15)}: 0–3 → 1, 4–7 → 2, 8–11 → 3, 12–14 → 4, 15 → 5. */
	public static int tier(int shelves) {
		int counted = Math.clamp(shelves, 0, MAX_SHELVES);
		return 1 + counted * (MAX_TIER - 1) / MAX_SHELVES;
	}

	/** {@code ceil(maxLevel × tier / 5)}: the highest level the table sells at this tier. */
	public static int maxSelectable(int maxLevel, int tier) {
		if (maxLevel < 1) {
			throw new IllegalArgumentException("maxLevel = " + maxLevel);
		}
		if (tier < 1 || tier > MAX_TIER) {
			throw new IllegalArgumentException("tier = " + tier);
		}
		return (maxLevel * tier + MAX_TIER - 1) / MAX_TIER;
	}

	public static boolean fullPower(int tier) {
		return tier == MAX_TIER;
	}
}
