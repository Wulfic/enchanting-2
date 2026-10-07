package com.wulfnet.enchanting2.grindstone;

/**
 * What the grindstone can do (DESIGN §6 "Grindstone modes"). The player always picks one; nothing happens before that
 * (owner, decisions.md 2026-10-07 "Grindstone"). The order is the panel's order and the wire ordinal.
 */
public enum GrindstoneAction {
	/** Move one non-curse enchantment onto a plain book. Takes an enchantment. */
	EXTRACT(true),
	/** Remove one curse at the §5a repay cost. Takes an enchantment. */
	CLEANSE(true),
	/** Vanilla: strip every non-curse enchantment from one item, XP orbs back. */
	DISENCHANT(false),
	/** Vanilla: combine two of the same item, non-curse enchantments stripped, XP orbs back. */
	REPAIR(false);

	private final boolean takesEnchantment;

	GrindstoneAction(boolean takesEnchantment) {
		this.takesEnchantment = takesEnchantment;
	}

	public boolean takesEnchantment() {
		return takesEnchantment;
	}

	/** Disenchant and Repair keep vanilla's take (XP orbs, both inputs emptied); Extract and Cleanse use ours. */
	public boolean vanillaTake() {
		return !takesEnchantment;
	}
}
