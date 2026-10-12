package com.wulfnet.enchanting2.config;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.function.ToIntBiFunction;

/**
 * The config screen's working copy of the numbers per enchantment (DESIGN §8, Phase 10jB): max level, base cost,
 * cost per level, and for curses the discount at level I and per level above (DESIGN §5a, Phase 10e). Each field shows
 * the file's value, or the built-in default when the file has none. {@link #apply} leaves a field the screen didn't
 * change exactly as the file had it, clears the override when the field is put back to its default (the "default (N)"
 * reset), and writes the value otherwise. Every other key is kept, and an entry left with nothing in it is dropped.
 */
public final class EnchantmentNumbersDraft {
	public enum Field {
		MAX_LEVEL("max_level_override", EnchantmentOverrides.MIN_MAX_LEVEL, EnchantmentOverrides.MAX_MAX_LEVEL),
		BASE_COST("base_cost", EnchantmentOverrides.MIN_COST, EnchantmentOverrides.MAX_COST),
		COST_PER_LEVEL("cost_per_level", EnchantmentOverrides.MIN_COST, EnchantmentOverrides.MAX_COST),
		DISCOUNT_PERCENT("discount_percent", EnchantmentOverrides.MIN_DISCOUNT_PERCENT,
				EnchantmentOverrides.MAX_DISCOUNT_PERCENT),
		DISCOUNT_PER_LEVEL("discount_per_level", EnchantmentOverrides.MIN_DISCOUNT_PERCENT,
				EnchantmentOverrides.MAX_DISCOUNT_PERCENT);

		private final String key;
		private final int min;
		private final int max;

		Field(String key, int min, int max) {
			this.key = key;
			this.min = min;
			this.max = max;
		}

		/** The {@code enchantments.json5} key. */
		public String key() {
			return key;
		}

		public int min() {
			return min;
		}

		public int max() {
			return max;
		}

		/** Only read for curses, so the screen shows it only on a curse's entry. */
		public boolean cursesOnly() {
			return this == DISCOUNT_PERCENT || this == DISCOUNT_PER_LEVEL;
		}

		OptionalInt read(EnchantmentOverrides.Entry entry) {
			return switch (this) {
				case MAX_LEVEL -> entry.maxLevelOverride();
				case BASE_COST -> entry.baseCost();
				case COST_PER_LEVEL -> entry.costPerLevel();
				case DISCOUNT_PERCENT -> entry.discountPercent();
				case DISCOUNT_PER_LEVEL -> entry.discountPerLevel();
			};
		}
	}

	private final EnchantmentOverrides original;
	private final ToIntBiFunction<String, Field> defaults;
	private final Map<String, EnumMap<Field, Integer>> rows = new TreeMap<>();

	/**
	 * @param ids      the enchantments listed on the screen
	 * @param defaults the built-in value of a field for an ID, used when the file has none
	 */
	public EnchantmentNumbersDraft(EnchantmentOverrides original, Iterable<String> ids,
			ToIntBiFunction<String, Field> defaults) {
		this.original = Objects.requireNonNull(original, "original");
		this.defaults = Objects.requireNonNull(defaults, "defaults");
		for (String id : ids) {
			EnumMap<Field, Integer> row = new EnumMap<>(Field.class);
			for (Field field : Field.values()) {
				row.put(field, fileValue(id, field).orElse(defaultValue(id, field)));
			}
			rows.put(id, row);
		}
	}

	private OptionalInt fileValue(String id, Field field) {
		EnchantmentOverrides.Entry entry = original.entries().get(id);
		return entry == null ? OptionalInt.empty() : field.read(entry);
	}

	public int defaultValue(String id, Field field) {
		return Math.clamp(defaults.applyAsInt(id, field), field.min(), field.max());
	}

	public int get(String id, Field field) {
		return row(id).get(field);
	}

	public void set(String id, Field field, int value) {
		if (value < field.min() || value > field.max()) {
			throw new IllegalArgumentException(field.key() + " out of range for " + id + ": " + value);
		}
		row(id).put(field, value);
	}

	private EnumMap<Field, Integer> row(String id) {
		EnumMap<Field, Integer> row = rows.get(id);
		if (row == null) {
			throw new IllegalArgumentException("Not listed: " + id);
		}
		return row;
	}

	/** {@code base} (the original, or the flags draft's result) with this draft's numbers applied. */
	public EnchantmentOverrides apply(EnchantmentOverrides base) {
		Map<String, EnchantmentOverrides.Entry> entries = new LinkedHashMap<>(base.entries());
		rows.forEach((id, row) -> {
			EnchantmentOverrides.Entry before = entries.getOrDefault(id, new EnchantmentOverrides.Entry());
			OptionalInt[] next = new OptionalInt[Field.values().length];
			for (Field field : Field.values()) {
				OptionalInt inFile = fileValue(id, field);
				int value = row.get(field);
				if (value == inFile.orElse(defaultValue(id, field))) {
					next[field.ordinal()] = field.read(before);
				} else if (value == defaultValue(id, field)) {
					next[field.ordinal()] = OptionalInt.empty();
				} else {
					next[field.ordinal()] = OptionalInt.of(value);
				}
			}
			EnchantmentOverrides.Entry after = before.withNumbers(next[Field.MAX_LEVEL.ordinal()],
					next[Field.BASE_COST.ordinal()], next[Field.COST_PER_LEVEL.ordinal()],
					next[Field.DISCOUNT_PERCENT.ordinal()], next[Field.DISCOUNT_PER_LEVEL.ordinal()]);
			if (after.equals(before)) {
				return;
			}
			if (after.equals(new EnchantmentOverrides.Entry())) {
				entries.remove(id);
			} else {
				entries.put(id, after);
			}
		});
		return new EnchantmentOverrides(entries);
	}
}
