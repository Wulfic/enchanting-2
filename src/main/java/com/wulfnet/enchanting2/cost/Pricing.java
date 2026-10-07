package com.wulfnet.enchanting2.cost;

import java.util.Objects;

/**
 * How one enchantment is priced (DESIGN §5). All amounts are XP levels.
 *
 * @param baseCost             charged once, when the enchantment is newly added
 * @param costPerLevel         per level charged
 * @param upgradeMode          how levels added to one already on the item are charged
 * @param otherLevelsSurcharge per level of every other non-curse enchantment on the item (Mending's surcharge)
 * @param discountPercent      the enchant-cost discount while this is on the result; only read for curses
 */
public record Pricing(int baseCost, int costPerLevel, UpgradeMode upgradeMode, int otherLevelsSurcharge,
		int discountPercent) {
	public Pricing {
		Objects.requireNonNull(upgradeMode, "upgradeMode");
		if (baseCost < 0 || costPerLevel < 0 || otherLevelsSurcharge < 0) {
			throw new IllegalArgumentException("negative cost in " + baseCost + "/" + costPerLevel + "/"
					+ otherLevelsSurcharge);
		}
		if (discountPercent < 0 || discountPercent > 100) {
			throw new IllegalArgumentException("discount_percent out of range: " + discountPercent);
		}
	}
}
