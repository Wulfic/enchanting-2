package com.wulfnet.enchanting2.enchantment;

import java.util.Map;
import java.util.OptionalInt;

/**
 * DESIGN §8's max-level table and the rule for applying it (pure, no Minecraft classes). Only rows whose v1 max differs
 * from vanilla are listed; every other enchantment keeps whatever max it was loaded with.
 */
public final class MaxLevels {
	public record Row(int vanillaMax, int v1Max) {
	}

	public enum Why {
		/** {@code max_level_override} in {@code enchantments.json5}. */
		OVERRIDE,
		/** The §8 v1 column. */
		TABLE,
		/** A §8 row, but something already changed the loaded max (a datapack or another mod), so it is kept. */
		DATAPACK_CHANGED,
		/** Not in §8: vanilla's unchanged rows and every other mod's enchantments. */
		UNLISTED
	}

	public record Resolution(int max, Why why) {
	}

	// Lure and Riptide stay at vanilla 3 until Stage 4D's GameTests prove 5 (owner, 2026-10-07).
	static final Map<String, Row> TABLE = Map.ofEntries(
			Map.entry("minecraft:protection", new Row(4, 5)),
			Map.entry("minecraft:fire_protection", new Row(4, 5)),
			Map.entry("minecraft:blast_protection", new Row(4, 5)),
			Map.entry("minecraft:projectile_protection", new Row(4, 5)),
			Map.entry("minecraft:feather_falling", new Row(4, 5)),
			Map.entry("minecraft:respiration", new Row(3, 5)),
			Map.entry("minecraft:thorns", new Row(3, 5)),
			Map.entry("minecraft:soul_speed", new Row(3, 5)),
			Map.entry("minecraft:swift_sneak", new Row(3, 5)),
			Map.entry("minecraft:frost_walker", new Row(2, 5)),
			Map.entry("minecraft:knockback", new Row(2, 5)),
			Map.entry("minecraft:fire_aspect", new Row(2, 5)),
			Map.entry("minecraft:punch", new Row(2, 5)),
			Map.entry("minecraft:looting", new Row(3, 5)),
			Map.entry("minecraft:fortune", new Row(3, 5)),
			Map.entry("minecraft:luck_of_the_sea", new Row(3, 5)),
			Map.entry("minecraft:unbreaking", new Row(3, 5)),
			Map.entry("minecraft:sweeping_edge", new Row(3, 5)),
			Map.entry("minecraft:loyalty", new Row(3, 5)),
			Map.entry("minecraft:wind_burst", new Row(3, 5)),
			Map.entry("minecraft:piercing", new Row(4, 5)),
			Map.entry("minecraft:breach", new Row(4, 5)),
			Map.entry("minecraft:lunge", new Row(3, 5)),
			Map.entry("minecraft:quick_charge", new Row(3, 4)));

	private MaxLevels() {
	}

	/**
	 * The max level an enchantment should have. An override always wins. A table row applies only when the loaded max
	 * is still vanilla's, so a datapack that already changed it isn't silently undone.
	 */
	public static Resolution resolve(String id, int loadedMax, OptionalInt override) {
		if (override.isPresent()) {
			return new Resolution(override.getAsInt(), Why.OVERRIDE);
		}
		Row row = TABLE.get(id);
		if (row == null) {
			return new Resolution(loadedMax, Why.UNLISTED);
		}
		if (loadedMax != row.vanillaMax()) {
			return new Resolution(loadedMax, Why.DATAPACK_CHANGED);
		}
		return new Resolution(row.v1Max(), Why.TABLE);
	}
}
