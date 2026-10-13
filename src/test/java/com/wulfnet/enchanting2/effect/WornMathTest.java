package com.wulfnet.enchanting2.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.wulfnet.enchanting2.curse.CurseMath;
import org.junit.jupiter.api.Test;

/** Phase 10cA (owner, 2026-10-10 and 2026-10-12): Clarity, Sustenance, Endurance and Sure Footing on powder snow. */
class WornMathTest {
	private static final double EPSILON = 1.0E-9;

	@Test
	void clarityLeavesTwoThirdsThenAThirdThenNothing() {
		assertEquals(2.0 / 3.0, WornMath.remaining(1.0 / 3.0), EPSILON, "Clarity I");
		assertEquals(1.0 / 3.0, WornMath.remaining(2.0 / 3.0), EPSILON, "Clarity II");
		assertEquals(0.0, WornMath.remaining(1.0), EPSILON, "Clarity III: immune");
		assertEquals(0.0, WornMath.remaining(5.0 / 3.0), EPSILON, "Clarity V: still immune, never negative");
	}

	@Test
	void clarityRoundsTheShortenedDuration() {
		float twoThirds = 2.0F / 3.0F; // what LevelBasedValue's fraction gives for Clarity II
		assertEquals(100, WornMath.scaledTicks(300, 1.0 - twoThirds), "Clarity II on 300 ticks, not a floored 99");
		assertEquals(200, WornMath.scaledTicks(300, 1.0 - 1.0F / 3.0F), "Clarity I on 300 ticks");
		assertEquals(173, WornMath.scaledTicks(260, 1.0 - 1.0F / 3.0F), "Clarity I on a 13 s Darkness");
		assertEquals(1, WornMath.scaledTicks(1, 1.0 - twoThirds), "never below 1 tick");
		assertThrows(IllegalArgumentException.class, () -> WornMath.scaledTicks(-1, 0.5), "infinite is the caller's");
	}

	@Test
	void sustenanceAndEnduranceMultiplyWithHunger() {
		assertEquals(0.8, WornMath.remaining(0.2), EPSILON, "Sustenance V");
		assertEquals(0.75, WornMath.remaining(0.25), EPSILON, "Endurance V");
		assertEquals(2.4, CurseMath.exhaustionFactor(2.0) * WornMath.remaining(0.2), 1.0E-6,
				"Hunger V on four pieces with Sustenance V: 3.0 × 0.8");
		assertEquals(0.0, WornMath.remaining(1.2), EPSILON, "Sustenance XXX: no drain, never negative");
	}

	@Test
	void aNegativeReductionCountsAsNone() {
		assertEquals(1.0, WornMath.remaining(0.0), EPSILON);
		assertEquals(1.0, WornMath.remaining(-0.5), EPSILON, "a datapack's negative value");
	}

	@Test
	void sureFootingLerpsPowderSnowTowardsNormal() {
		assertEquals(0.9, WornMath.stuckFactor(0.9, 0.0), EPSILON, "none: vanilla");
		assertEquals(0.9 + 0.1 / 3.0, WornMath.stuckFactor(0.9, 1.0 / 3.0), EPSILON, "Sure Footing I");
		assertEquals(1.0, WornMath.stuckFactor(0.9, 1.0), EPSILON, "Sure Footing III: no slowdown");
		assertEquals(1.0, WornMath.stuckFactor(0.9, 2.0), EPSILON, "past 1: still 1");
	}

	@Test
	void refusesNonFiniteInput() {
		assertThrows(IllegalArgumentException.class, () -> WornMath.remaining(Double.NaN));
		assertThrows(IllegalArgumentException.class, () -> WornMath.stuckFactor(0.9, Double.POSITIVE_INFINITY));
	}
}
