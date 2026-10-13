package com.wulfnet.enchanting2.config;

import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;

/**
 * Mob settings (owner, 2026-10-13; TODO 10m): {@code health_multiplier} multiplies every mob's max health, bosses
 * included, players never. Whole multiples 1 to 100; 1 is vanilla.
 */
public record MobsSection(int healthMultiplier) {
	public static final int MIN_HEALTH_MULTIPLIER = 1;
	public static final int MAX_HEALTH_MULTIPLIER = 100;
	public static final MobsSection DEFAULTS = new MobsSection(1);

	public MobsSection {
		if (healthMultiplier < MIN_HEALTH_MULTIPLIER || healthMultiplier > MAX_HEALTH_MULTIPLIER) {
			throw new IllegalArgumentException("health_multiplier " + healthMultiplier + " is outside "
					+ MIN_HEALTH_MULTIPLIER + ".." + MAX_HEALTH_MULTIPLIER);
		}
	}

	static MobsSection read(ConfigReader r) {
		return new MobsSection(r.readInt("health_multiplier", DEFAULTS.healthMultiplier, MIN_HEALTH_MULTIPLIER,
				MAX_HEALTH_MULTIPLIER));
	}

	JsonObject write() {
		JsonObject o = new JsonObject();
		o.put("health_multiplier", JsonPrimitive.of((long) healthMultiplier),
				"Every mob's max health times this (1 to 100; 1 = vanilla). Bosses too, never players. Mobs already in"
						+ " the world change on the next /reload and keep their share of health.");
		return o;
	}
}
