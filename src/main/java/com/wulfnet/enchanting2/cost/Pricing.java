package com.wulfnet.enchanting2.cost;

import java.util.Objects;

/**
 * How one enchantment is priced (DESIGN §5). All amounts are XP levels.
 *
 * @param baseCost             charged once, when the enchantment is newly added
 * @param costPerLevel         per level charged
 * @param upgradeMode          how levels added to one already on the item are charged
 * @param otherLevelsSurcharge per level of every other non-curse enchantment on the item (Mending's surcharge)
 * @param discountPercent      the enchant-cost discount while this is on the result at level I; only read for curses
 * @param discountPerLevel     added to the discount for each level above I (DESIGN §5a, Phase 10e)
 */
public record Pricing(int baseCost, int costPerLevel, UpgradeMode upgradeMode, int otherLevelsSurcharge,
		int discountPercent, int discountPerLevel) {
	public Pricing {
		Objects.requireNonNull(upgradeMode, "upgradeMode");
		if (baseCost < 0 || costPerLevel < 0 || otherLevelsSurcharge < 0) {
			throw new IllegalArgumentException("negative cost in " + baseCost + "/" + costPerLevel + "/"
					+ otherLevelsSurcharge);
		}
		if (discountPercent < 0 || discountPercent > 100) {
			throw new IllegalArgumentException("discount_percent out of range: " + discountPercent);
		}
		if (discountPerLevel < 0 || discountPerLevel > 100) {
			throw new IllegalArgumentException("discount_per_level out of range: " + discountPerLevel);
		}
	}

	/** A price whose curse discount is the same at every level. */
	public Pricing(int baseCost, int costPerLevel, UpgradeMode upgradeMode, int otherLevelsSurcharge,
			int discountPercent) {
		this(baseCost, costPerLevel, upgradeMode, otherLevelsSurcharge, discountPercent, 0);
	}

	/** The discount percent at {@code level}: level I's plus the per-level step for each level above, at most 100. */
	public int discountAt(int level) {
		if (level < 1) {
			throw new IllegalArgumentException("level " + level);
		}
		return (int) Math.min(100L, discountPercent + (long) discountPerLevel * (level - 1));
	}
}
