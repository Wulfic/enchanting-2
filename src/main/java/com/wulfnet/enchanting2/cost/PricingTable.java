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
	 * Every single-level non-curse enchantment except Mending: a flat 10 (owner, 2026-10-10). Per-level 0, so a max
	 * raised by {@code max_level_override} still costs 10 for any level until the admin sets {@code cost_per_level}.
	 */
	static final Pricing SINGLE_LEVEL = new Pricing(10, 0, UpgradeMode.LEVEL_DIFFERENCE, 0, 10);

	/**
	 * The five Phase 10e curses: 15% at level I, +5% per level (owner, 2026-10-12), so V = 35% and one curse alone never
	 * reaches the 45% cap. A curse's own line is 0 whatever its cost fields say ({@link CostModel}).
	 */
	static final Pricing CURSE_15 = new Pricing(0, 1, UpgradeMode.LEVEL_DIFFERENCE, 0, 15, 5);

	/**
	 * Mending is per-level 0: the §5 table has "Mending alone = 2" and "Mending + Eff I = 5", so its whole own price is
	 * the base 2, and the rest is the surcharge of 2 per other level. Each later single-level enchantment (10c, 10i)
	 * adds its {@link #SINGLE_LEVEL} row in its own stage.
	 */
	static final Map<String, Pricing> BUILT_IN = Map.ofEntries(
			Map.entry("minecraft:mending", new Pricing(2, 0, UpgradeMode.LEVEL_DIFFERENCE, 2, 10)),
			Map.entry("minecraft:frost_walker", new Pricing(0, 2, UpgradeMode.LEVEL_DIFFERENCE, 0, 10)),
			Map.entry("enchanting2:speed", new Pricing(0, 6, UpgradeMode.LEVEL_DIFFERENCE, 0, 10)),
			Map.entry("enchanting2:flight", new Pricing(0, 6, UpgradeMode.LEVEL_DIFFERENCE, 0, 10)),
			Map.entry("minecraft:aqua_affinity", SINGLE_LEVEL),
			Map.entry("minecraft:silk_touch", SINGLE_LEVEL),
			Map.entry("minecraft:flame", SINGLE_LEVEL),
			Map.entry("minecraft:infinity", SINGLE_LEVEL),
			Map.entry("minecraft:channeling", SINGLE_LEVEL),
			Map.entry("minecraft:multishot", SINGLE_LEVEL),
			Map.entry("enchanting2:smelting", SINGLE_LEVEL),
			Map.entry("enchanting2:true_shot_aura", SINGLE_LEVEL),
			Map.entry("enchanting2:vision", SINGLE_LEVEL),
			Map.entry("enchanting2:step_assist", SINGLE_LEVEL),
			Map.entry("enchanting2:calm_gaze", SINGLE_LEVEL),
			Map.entry("enchanting2:phantom_ward", SINGLE_LEVEL),
			Map.entry("enchanting2:lava_walking", SINGLE_LEVEL),
			Map.entry("enchanting2:snow_walker", SINGLE_LEVEL),
			Map.entry("enchanting2:steady_stance", SINGLE_LEVEL),
			Map.entry("enchanting2:magnetic", SINGLE_LEVEL),
			Map.entry("enchanting2:ender_link", SINGLE_LEVEL),
			Map.entry("enchanting2:keep_on_death", SINGLE_LEVEL),
			Map.entry("enchanting2:area_mining", SINGLE_LEVEL),
			Map.entry("enchanting2:replant", SINGLE_LEVEL),
			Map.entry("enchanting2:double_jump", SINGLE_LEVEL),
			Map.entry("enchanting2:fragility", CURSE_15),
			Map.entry("enchanting2:weight", CURSE_15),
			Map.entry("enchanting2:greed", CURSE_15),
			Map.entry("enchanting2:fumbling", CURSE_15),
			Map.entry("enchanting2:hunger", CURSE_15));

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
				entry.discountPercent().orElse(base.discountPercent()),
				entry.discountPerLevel().orElse(base.discountPerLevel()));
	}
}
