package com.wulfnet.enchanting2.cost;

/**
 * DESIGN §5: how an upgrade (the enchantment is already on the item) is charged. A newly added enchantment always pays
 * for its result level. Spelled lower-case in {@code enchantments.json5}.
 */
public enum UpgradeMode {
	/** The whole result level again. */
	RESULT_LEVEL,
	/** Only the added levels: Efficiency III → V = 2. The default. */
	LEVEL_DIFFERENCE,
	/** {@code cost_per_level} once, however many levels are added (owner, 2026-10-07). */
	FIXED,
	/** Nothing. */
	FREE
}
