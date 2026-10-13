package com.wulfnet.enchanting2.anvil;

import com.wulfnet.enchanting2.config.AnvilSection;
import java.util.Objects;
import java.util.function.DoubleSupplier;

/**
 * Whether an anvil takes the chip vanilla just rolled (owner 2026-10-13, Phase 11c). Vanilla rolls first (12% per use,
 * {@code 0.05 + 0.05 × distance} on landing) and only then calls {@code AnvilBlock.damage}; this decides at that call.
 * Off: never. On: one more roll keeps {@code 1 / durability_multiplier} of vanilla's chips, so every chance is
 * {@code durability_multiplier} times rarer. Pure, so the rule is unit-tested; the mixins only ask.
 */
public final class AnvilWear {
	private AnvilWear() {
	}

	/**
	 * @param roll a uniform roll in [0, 1); not drawn at all at multiplier 1, so vanilla durability is exactly vanilla's
	 */
	public static boolean takesChip(AnvilSection anvil, DoubleSupplier roll) {
		Objects.requireNonNull(anvil, "anvil");
		Objects.requireNonNull(roll, "roll");
		if (!anvil.vanillaDurability()) {
			return false;
		}
		return anvil.durabilityMultiplier() == 1 || roll.getAsDouble() * anvil.durabilityMultiplier() < 1;
	}
}
