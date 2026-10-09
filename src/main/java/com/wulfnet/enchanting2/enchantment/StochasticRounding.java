package com.wulfnet.enchanting2.enchantment;

import java.util.function.DoubleSupplier;

/**
 * Rounds a value to an int whose average is the value: {@code 5.75} becomes 6 three times in four and 5 otherwise.
 * Vanilla truncates the experience multiplied by {@code mob_experience} / {@code block_experience} effects, which would
 * make XP Boost I–III a no-op on a 5 XP mob (decisions.md "Phase 10a").
 */
public final class StochasticRounding {
	private StochasticRounding() {
	}

	/**
	 * {@code floor(value)}, plus 1 when {@code roll} (uniform in [0, 1)) is below the fraction. A whole value never draws
	 * a roll, so code that only ever sees whole values (vanilla, with no such enchantment) keeps its random sequence.
	 * The caller handles non-finite values: this one requires a finite value.
	 */
	public static int round(float value, DoubleSupplier roll) {
		if (!Float.isFinite(value)) {
			throw new IllegalArgumentException("not a finite value: " + value);
		}
		double floor = Math.floor(value);
		double fraction = value - floor;
		// (int) saturates past the int range, like the MutableFloat#intValue it replaces.
		int whole = (int) floor;
		if (fraction == 0.0 || whole == Integer.MAX_VALUE) {
			return whole;
		}
		return roll.getAsDouble() < fraction ? whole + 1 : whole;
	}
}
