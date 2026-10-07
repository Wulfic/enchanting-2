package com.wulfnet.enchanting2.config;

import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;

/**
 * DESIGN §5a: curses discount the enchant cost, up to a cap, and removing one repays its discount. Each curse's own
 * percent is per enchantment ({@code enchantments.json5} {@code discount_percent}).
 */
public record CursesSection(int maxDiscountPercent, int removalMinCost) {
	public static final int MIN_DISCOUNT_PERCENT = 0;
	// 100 would make any enchantment free with enough curses.
	public static final int MAX_DISCOUNT_PERCENT = 95;
	public static final int MIN_REMOVAL_COST = 0;
	public static final int MAX_REMOVAL_COST = 1_000_000;

	public static final CursesSection DEFAULTS = new CursesSection(45, 30);

	public CursesSection {
		if (maxDiscountPercent < MIN_DISCOUNT_PERCENT || maxDiscountPercent > MAX_DISCOUNT_PERCENT) {
			throw new IllegalArgumentException("max_discount_percent = " + maxDiscountPercent + " is outside "
					+ MIN_DISCOUNT_PERCENT + ".." + MAX_DISCOUNT_PERCENT);
		}
		if (removalMinCost < MIN_REMOVAL_COST || removalMinCost > MAX_REMOVAL_COST) {
			throw new IllegalArgumentException("removal_min_cost = " + removalMinCost + " is outside "
					+ MIN_REMOVAL_COST + ".." + MAX_REMOVAL_COST);
		}
	}

	static CursesSection read(ConfigReader r) {
		return new CursesSection(
				r.readInt("max_discount_percent", DEFAULTS.maxDiscountPercent, MIN_DISCOUNT_PERCENT,
						MAX_DISCOUNT_PERCENT),
				r.readInt("removal_min_cost", DEFAULTS.removalMinCost, MIN_REMOVAL_COST, MAX_REMOVAL_COST));
	}

	JsonObject write() {
		JsonObject o = new JsonObject();
		o.put("max_discount_percent", JsonPrimitive.of((long) maxDiscountPercent),
				"Cap on the total discount from every curse on the item. Range " + MIN_DISCOUNT_PERCENT + ".."
						+ MAX_DISCOUNT_PERCENT + ".");
		o.put("removal_min_cost", JsonPrimitive.of((long) removalMinCost),
				"Least XP levels to cleanse a curse at the grindstone. Otherwise it costs the curse's percent of the "
						+ "item's enchantments, i.e. the discount it earned.");
		return o;
	}
}
