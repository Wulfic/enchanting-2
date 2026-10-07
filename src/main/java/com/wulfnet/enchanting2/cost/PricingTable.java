package com.wulfnet.enchanting2.cost;

import com.wulfnet.enchanting2.config.EnchantmentOverrides;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves an enchantment ID to its {@link Pricing}: the built-in value (DESIGN §5), then each field the admin set in
 * {@code enchantments.json5} replaces that one field. Special cases are rows here, never branches in {@link CostModel}.
 */
public final class PricingTable {
	/**
	 * 1 XP level per enchantment level, upgrades pay only the added levels. The 10% discount is Binding's and
	 * Vanishing's (§5a), and also what a curse with no entry gets, e.g. a third-party one (decisions.md 2026-10-07).
	 */
	public static final Pricing DEFAULT = new Pricing(0, 1, UpgradeMode.LEVEL_DIFFERENCE, 0, 10);

	/**
	 * Mending is per-level 0: the §5 table has "Mending alone = 2" and "Mending + Eff I = 5", so its whole own price is
	 * the base 2, and the rest is the surcharge of 2 per other level.
	 */
	static final Map<String, Pricing> BUILT_IN = Map.of(
			"minecraft:mending", new Pricing(2, 0, UpgradeMode.LEVEL_DIFFERENCE, 2, 10));

	private final EnchantmentOverrides overrides;

	public PricingTable(EnchantmentOverrides overrides) {
		this.overrides = Objects.requireNonNull(overrides, "overrides");
	}

	/** The built-in prices with no file. */
	public static PricingTable builtIn() {
		return new PricingTable(EnchantmentOverrides.defaults());
	}

	public Pricing get(String id) {
		Pricing base = BUILT_IN.getOrDefault(id, DEFAULT);
		EnchantmentOverrides.Entry entry = overrides.entries().get(id);
		if (entry == null) {
			return base;
		}
		return new Pricing(
				entry.baseCost().orElse(base.baseCost()),
				entry.costPerLevel().orElse(base.costPerLevel()),
				entry.upgradeMode().orElse(base.upgradeMode()),
				entry.otherLevelsSurcharge().orElse(base.otherLevelsSurcharge()),
				entry.discountPercent().orElse(base.discountPercent()));
	}
}
