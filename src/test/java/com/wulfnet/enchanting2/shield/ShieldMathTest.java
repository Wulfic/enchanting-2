package com.wulfnet.enchanting2.shield;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Phase 10cC (owner, 2026-10-10 and 2026-10-12): Bulwark's one heart per level and Riposte's +3% per level. */
class ShieldMathTest {
	@Test
	void bulwarkGivesOneHealthPerAmplifierStep() {
		assertEquals(1, ShieldMath.bulwarkAmplifier(2.0), "Bulwark I: one heart = 2 health = amplifier 1");
		assertEquals(3, ShieldMath.bulwarkAmplifier(4.0), "Bulwark II");
		assertEquals(5, ShieldMath.bulwarkAmplifier(6.0), "Bulwark III");
		assertEquals(0, ShieldMath.bulwarkAmplifier(1.0), "half a heart");
		assertEquals(2, ShieldMath.bulwarkAmplifier(2.6), "rounded to whole health");
	}

	@Test
	void bulwarkGivesNothingUnderHalfAHealthAndCapsAtTheSavedMaximum() {
		assertEquals(-1, ShieldMath.bulwarkAmplifier(0.4), "rounds to no health");
		assertEquals(-1, ShieldMath.bulwarkAmplifier(-6.0), "a datapack's negative amount");
		assertEquals(ShieldMath.MAX_AMPLIFIER, ShieldMath.bulwarkAmplifier(1.0E9), "a datapack's huge amount");
		assertThrows(IllegalArgumentException.class, () -> ShieldMath.bulwarkAmplifier(Double.NaN));
	}

	@Test
	void riposteAddsItsBonusAndNeverTakesAway() {
		assertEquals(11.5F, ShieldMath.withBonus(10.0F, 0.15F), 1.0E-5F, "Riposte V on a 10 hit");
		assertEquals(10.3F, ShieldMath.withBonus(10.0F, 0.03F), 1.0E-5F, "Riposte I");
		assertEquals(10.0F, ShieldMath.withBonus(10.0F, -0.5), 1.0E-6F, "a negative bonus adds nothing");
		assertThrows(IllegalArgumentException.class, () -> ShieldMath.withBonus(10.0F, Double.POSITIVE_INFINITY));
	}
}
