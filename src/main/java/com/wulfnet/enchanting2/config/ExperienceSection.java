package com.wulfnet.enchanting2.config;

import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;

/**
 * DESIGN §3. From {@code flatten_after_level} on, every level costs {@code fixed_points_per_level} points.
 * The ranges are public so the config screen uses the same bounds as the loader.
 */
public record ExperienceSection(boolean enabled, int flattenAfterLevel, int fixedPointsPerLevel, int maximumLevel) {
	public static final int MIN_FLATTEN_AFTER_LEVEL = 0;
	public static final int MAX_FLATTEN_AFTER_LEVEL = 1_000_000;
	// 0 would let a single XP point grant unlimited levels.
	public static final int MIN_FIXED_POINTS_PER_LEVEL = 1;
	public static final int MAX_FIXED_POINTS_PER_LEVEL = 1_000_000;
	public static final int UNLIMITED_LEVEL = -1;
	public static final int MAX_MAXIMUM_LEVEL = Integer.MAX_VALUE;

	/** Vanilla's 30→31 cost (112 points), flattened from level 30. */
	public static final ExperienceSection DEFAULTS = new ExperienceSection(true, 30, 112, UNLIMITED_LEVEL);

	public ExperienceSection {
		requireInRange("flatten_after_level", flattenAfterLevel, MIN_FLATTEN_AFTER_LEVEL, MAX_FLATTEN_AFTER_LEVEL);
		requireInRange("fixed_points_per_level", fixedPointsPerLevel, MIN_FIXED_POINTS_PER_LEVEL,
				MAX_FIXED_POINTS_PER_LEVEL);
		requireInRange("maximum_level", maximumLevel, UNLIMITED_LEVEL, MAX_MAXIMUM_LEVEL);
	}

	static ExperienceSection read(ConfigReader r) {
		return new ExperienceSection(
				r.readBool("enabled", DEFAULTS.enabled),
				r.readInt("flatten_after_level", DEFAULTS.flattenAfterLevel, MIN_FLATTEN_AFTER_LEVEL,
						MAX_FLATTEN_AFTER_LEVEL),
				r.readInt("fixed_points_per_level", DEFAULTS.fixedPointsPerLevel, MIN_FIXED_POINTS_PER_LEVEL,
						MAX_FIXED_POINTS_PER_LEVEL),
				r.readInt("maximum_level", DEFAULTS.maximumLevel, UNLIMITED_LEVEL, MAX_MAXIMUM_LEVEL));
	}

	JsonObject write() {
		JsonObject o = new JsonObject();
		o.put("enabled", JsonPrimitive.of(enabled), "false = the full vanilla curve at every level.");
		o.put("flatten_after_level", JsonPrimitive.of((long) flattenAfterLevel),
				"From this level on, each level costs fixed_points_per_level. 0 = fixed cost at every level. Range "
						+ MIN_FLATTEN_AFTER_LEVEL + ".." + MAX_FLATTEN_AFTER_LEVEL + ".");
		o.put("fixed_points_per_level", JsonPrimitive.of((long) fixedPointsPerLevel),
				"XP points per level past the threshold. 112 is vanilla's 30->31 cost. Range "
						+ MIN_FIXED_POINTS_PER_LEVEL + ".." + MAX_FIXED_POINTS_PER_LEVEL + ".");
		o.put("maximum_level", JsonPrimitive.of((long) maximumLevel),
				"Highest level a player can reach. -1 = unlimited.");
		return o;
	}

	private static void requireInRange(String key, int value, int min, int max) {
		if (value < min || value > max) {
			throw new IllegalArgumentException(key + " = " + value + " is outside " + min + ".." + max);
		}
	}
}
