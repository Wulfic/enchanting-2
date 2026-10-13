package com.wulfnet.enchanting2.elytra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Phase 10cGA (owner, 2026-10-10): Speed's +10% per level through the glide's air drag. */
class GlideMathTest {
	private static final double EPSILON = 1.0E-9;

	/** A glide's top speed with drag d is proportional to d / (1 − d). */
	private static double topSpeed(double drag) {
		return drag / (1.0 - drag);
	}

	@Test
	void theFactorIsOnePlusTheBonusUpToDoubleSpeed() {
		assertEquals(1.0, GlideMath.factor(0.0), EPSILON);
		assertEquals(1.5, GlideMath.factor(0.5), EPSILON, "Speed V");
		assertEquals(2.0, GlideMath.factor(1.0), EPSILON, "Speed X");
		assertEquals(2.0, GlideMath.factor(2.0), EPSILON, "Speed XX stops at X");
		assertEquals(1.0, GlideMath.factor(-0.3), EPSILON, "a datapack's negative bonus slows nothing");
		assertThrows(IllegalArgumentException.class, () -> GlideMath.factor(Double.NaN));
	}

	@Test
	void theEasedDragRaisesTheTopSpeedByAboutTheFactor() {
		double vanilla = 0.99;
		assertEquals(vanilla, GlideMath.drag(vanilla, 1.0), EPSILON, "no Speed, vanilla drag");
		for (double factor : new double[] { 1.1, 1.5, 2.0 }) {
			double ratio = topSpeed(GlideMath.drag(vanilla, factor)) / topSpeed(vanilla);
			assertEquals(factor, ratio, 0.01 * factor, "top speed ratio at factor " + factor);
		}
	}
}
