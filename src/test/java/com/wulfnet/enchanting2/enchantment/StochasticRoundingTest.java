package com.wulfnet.enchanting2.enchantment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.function.DoubleSupplier;
import org.junit.jupiter.api.Test;

class StochasticRoundingTest {
	private static final DoubleSupplier NO_ROLL = () -> {
		throw new AssertionError("a whole value drew a roll");
	};

	@Test
	void wholeValuesAreUnchangedAndDrawNoRoll() {
		for (float value : new float[] {0.0F, 1.0F, 5.0F, -3.0F, 1_000_000.0F}) {
			assertEquals((int) value, StochasticRounding.round(value, NO_ROLL), "value " + value);
		}
	}

	@Test
	void theFractionIsTheChanceOfRoundingUp() {
		assertEquals(6, StochasticRounding.round(5.75F, () -> 0.0));
		assertEquals(6, StochasticRounding.round(5.75F, () -> 0.7499));
		assertEquals(5, StochasticRounding.round(5.75F, () -> 0.75));
		assertEquals(5, StochasticRounding.round(5.75F, () -> 0.9999));
		assertEquals(0, StochasticRounding.round(0.25F, () -> 0.5));
		assertEquals(1, StochasticRounding.round(0.25F, () -> 0.1));
	}

	/** Negative values floor: −0.5 is −1 or 0, never −2 or 1, so the average stays −0.5. */
	@Test
	void negativeValuesFloorThenRoundUp() {
		assertEquals(0, StochasticRounding.round(-0.5F, () -> 0.1));
		assertEquals(-1, StochasticRounding.round(-0.5F, () -> 0.9));
	}

	@Test
	void valuesPastTheIntRangeSaturateLikeIntValue() {
		assertEquals(Integer.MAX_VALUE, StochasticRounding.round(1.0E12F, NO_ROLL));
		assertEquals(Integer.MIN_VALUE, StochasticRounding.round(-1.0E12F, NO_ROLL));
	}

	@Test
	void nonFiniteValuesAreRefused() {
		assertThrows(IllegalArgumentException.class, () -> StochasticRounding.round(Float.NaN, NO_ROLL));
		assertThrows(IllegalArgumentException.class, () -> StochasticRounding.round(Float.POSITIVE_INFINITY, NO_ROLL));
	}

	/** XP Boost V on a 5 XP mob: 6.25 on average. 100 000 rolls put the mean within ±0.01 (~7 standard deviations). */
	@Test
	void theMeanIsTheValue() {
		Random random = new Random(42);
		int rolls = 100_000;
		long total = 0;
		for (int i = 0; i < rolls; i++) {
			int rounded = StochasticRounding.round(6.25F, random::nextDouble);
			assertTrue(rounded == 6 || rounded == 7, "rounded to " + rounded);
			total += rounded;
		}
		assertEquals(6.25, total / (double) rolls, 0.01);
	}
}
