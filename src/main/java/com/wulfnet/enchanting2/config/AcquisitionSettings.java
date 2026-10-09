package com.wulfnet.enchanting2.config;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The Phase 9 keys of one {@code enchantments.json5} entry. Every field is optional; an empty one means the built-in
 * value. They are flat keys in the file and grouped here so {@link EnchantmentOverrides.Entry} stays readable. Flags only
 * take an enchantment out of a source; the tags ({@code #tradeable}, {@code #on_random_loot}, ...) decide what's in it.
 *
 * @param enabled     false = never from any source (table, loot, trades); items that already carry it keep it
 * @param table       false = not offered at the enchanting table
 * @param loot        false = never from loot tables or on mob spawn gear
 * @param trade       false = never from villager trades (librarian books and enchanted gear)
 * @param lootWeight  replaces the registry weight for loot picks
 * @param tradeWeight replaces the registry weight for trade picks
 * @param treasure    replaces {@code #treasure} for the table's full-power gate and {@code #double_trade_price} for the
 *                    librarian's price
 */
public record AcquisitionSettings(Optional<Boolean> enabled, Optional<Boolean> table, Optional<Boolean> loot,
		Optional<Boolean> trade, OptionalInt lootWeight, OptionalInt tradeWeight, Optional<Boolean> treasure) {
	/** Vanilla's weight codec range ({@code Enchantment.EnchantmentDefinition}). */
	public static final int MIN_WEIGHT = 1;
	public static final int MAX_WEIGHT = 1024;

	public static final AcquisitionSettings NONE = new AcquisitionSettings(Optional.empty(), Optional.empty(),
			Optional.empty(), Optional.empty(), OptionalInt.empty(), OptionalInt.empty(), Optional.empty());

	public AcquisitionSettings {
		Objects.requireNonNull(enabled, "enabled");
		Objects.requireNonNull(table, "table");
		Objects.requireNonNull(loot, "loot");
		Objects.requireNonNull(trade, "trade");
		Objects.requireNonNull(treasure, "treasure");
		requireWeight("loot_weight", lootWeight);
		requireWeight("trade_weight", tradeWeight);
	}

	private static void requireWeight(String key, OptionalInt weight) {
		Objects.requireNonNull(weight, key);
		weight.ifPresent(n -> {
			if (n < MIN_WEIGHT || n > MAX_WEIGHT) {
				throw new IllegalArgumentException(key + " out of range: " + n);
			}
		});
	}
}
