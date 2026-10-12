package com.wulfnet.enchanting2.config;

import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;

/**
 * DESIGN §5a: curses discount the enchant cost, up to a cap, and removing one repays its discount. Each curse's own
 * percent is per enchantment ({@code enchantments.json5} {@code discount_percent}). Phase 10e: the cap on Curse of
 * Weight's total slowdown over every piece worn.
 */
public record CursesSection(int maxDiscountPercent, int removalMinCost, int weightMaxSlowdownPercent) {
	public static final int MIN_DISCOUNT_PERCENT = 0;
	// 100 would make any enchantment free with enough curses.
	public static final int MAX_DISCOUNT_PERCENT = 95;
	public static final int MIN_REMOVAL_COST = 0;
	public static final int MAX_REMOVAL_COST = 1_000_000;
	public static final int MIN_WEIGHT_SLOWDOWN_PERCENT = 0;
	// 100 stops a player wearing enough Weight from moving at all; the admin's call.
	public static final int MAX_WEIGHT_SLOWDOWN_PERCENT = 100;

	/** Weight's cap is the owner's 50% (2026-10-12). */
	public static final CursesSection DEFAULTS = new CursesSection(45, 30, 50);

	public CursesSection {
		if (maxDiscountPercent < MIN_DISCOUNT_PERCENT || maxDiscountPercent > MAX_DISCOUNT_PERCENT) {
			throw new IllegalArgumentException("max_discount_percent = " + maxDiscountPercent + " is outside "
					+ MIN_DISCOUNT_PERCENT + ".." + MAX_DISCOUNT_PERCENT);
		}
		if (removalMinCost < MIN_REMOVAL_COST || removalMinCost > MAX_REMOVAL_COST) {
			throw new IllegalArgumentException("removal_min_cost = " + removalMinCost + " is outside "
					+ MIN_REMOVAL_COST + ".." + MAX_REMOVAL_COST);
		}
		if (weightMaxSlowdownPercent < MIN_WEIGHT_SLOWDOWN_PERCENT
				|| weightMaxSlowdownPercent > MAX_WEIGHT_SLOWDOWN_PERCENT) {
			throw new IllegalArgumentException("weight_max_slowdown_percent = " + weightMaxSlowdownPercent
					+ " is outside " + MIN_WEIGHT_SLOWDOWN_PERCENT + ".." + MAX_WEIGHT_SLOWDOWN_PERCENT);
		}
	}

	/** The two discount settings, with Weight's default cap. */
	public CursesSection(int maxDiscountPercent, int removalMinCost) {
		this(maxDiscountPercent, removalMinCost, DEFAULTS.weightMaxSlowdownPercent);
	}

	static CursesSection read(ConfigReader r) {
		return new CursesSection(
				r.readInt("max_discount_percent", DEFAULTS.maxDiscountPercent, MIN_DISCOUNT_PERCENT,
						MAX_DISCOUNT_PERCENT),
				r.readInt("removal_min_cost", DEFAULTS.removalMinCost, MIN_REMOVAL_COST, MAX_REMOVAL_COST),
				r.readInt("weight_max_slowdown_percent", DEFAULTS.weightMaxSlowdownPercent,
						MIN_WEIGHT_SLOWDOWN_PERCENT, MAX_WEIGHT_SLOWDOWN_PERCENT));
	}

	JsonObject write() {
		JsonObject o = new JsonObject();
		o.put("max_discount_percent", JsonPrimitive.of((long) maxDiscountPercent),
				"Cap on the total discount from every curse on the item. Range " + MIN_DISCOUNT_PERCENT + ".."
						+ MAX_DISCOUNT_PERCENT + ".");
		o.put("removal_min_cost", JsonPrimitive.of((long) removalMinCost),
				"Least XP levels to cleanse a curse at the grindstone. Otherwise it costs the curse's percent of the "
						+ "item's enchantments, i.e. the discount it earned.");
		o.put("weight_max_slowdown_percent", JsonPrimitive.of((long) weightMaxSlowdownPercent),
				"Curse of Weight slows by 10% per level on each armor piece; the pieces add up to at most this much. "
						+ "Range " + MIN_WEIGHT_SLOWDOWN_PERCENT + ".." + MAX_WEIGHT_SLOWDOWN_PERCENT
						+ " (100 can stop the wearer completely). Applies the next time the wearer's gear changes.");
		return o;
	}
}
