package com.wulfnet.enchanting2.acquisition;

/** Where an enchantment can be obtained (DESIGN §6 "Loot and trading"). */
public enum Source {
	/** The enchanting table's pool. Not random, so it has a flag and no weight. */
	TABLE,
	/** Loot tables ({@code enchant_randomly}, {@code enchant_with_levels}) and mob spawn gear. */
	LOOT,
	/** Villager trades: librarian books and enchanted gear. */
	TRADE
}
