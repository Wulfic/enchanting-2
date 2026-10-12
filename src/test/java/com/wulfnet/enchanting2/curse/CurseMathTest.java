package com.wulfnet.enchanting2.curse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Phase 10e (owner, 2026-10-12): Weight's pieces add up to a cap; Hunger's add up with none. */
class CurseMathTest {
	private static final double EPSILON = 1.0E-9;

	@Test
	void weightAddsPiecesUpToTheCap() {
		assertEquals(-0.1, CurseMath.weightModifier(0.1, 50), EPSILON, "one piece at I");
		assertEquals(-0.4, CurseMath.weightModifier(0.4, 50), EPSILON, "four pieces at I");
		assertEquals(-0.5, CurseMath.weightModifier(0.6, 50), EPSILON, "III + III capped at 50%");
		assertEquals(-0.5, CurseMath.weightModifier(2.0, 50), EPSILON, "four pieces at V capped at 50%");
		assertEquals(-1.0, CurseMath.weightModifier(2.0, 100), EPSILON, "an admin's 100% cap");
		assertEquals(0.0, CurseMath.weightModifier(0.5, 0), EPSILON, "a 0% cap turns Weight off");
	}

	@Test
	void weightNeverSpeedsAnyoneUp() {
		assertEquals(0.0, CurseMath.weightModifier(0.0, 50), EPSILON);
		assertEquals(0.0, CurseMath.weightModifier(-0.3, 50), EPSILON, "a datapack's negative penalty");
	}

	@Test
	void weightRefusesBadInput() {
		assertThrows(IllegalArgumentException.class, () -> CurseMath.weightModifier(Double.NaN, 50));
		assertThrows(IllegalArgumentException.class, () -> CurseMath.weightModifier(0.1, 101));
		assertThrows(IllegalArgumentException.class, () -> CurseMath.weightModifier(0.1, -1));
	}

	@Test
	void hungerAddsEveryPieceUncapped() {
		assertEquals(1.0F, CurseMath.exhaustionFactor(0.0), 1.0E-6F);
		assertEquals(1.1F, CurseMath.exhaustionFactor(0.1), 1.0E-6F, "one piece at I");
		assertEquals(1.5F, CurseMath.exhaustionFactor(0.5), 1.0E-6F, "one piece at V");
		assertEquals(3.0F, CurseMath.exhaustionFactor(2.0), 1.0E-6F, "four pieces at V: +200%");
		assertEquals(1.0F, CurseMath.exhaustionFactor(-1.0), 1.0E-6F, "never below vanilla");
		assertThrows(IllegalArgumentException.class, () -> CurseMath.exhaustionFactor(Double.POSITIVE_INFINITY));
	}
}
