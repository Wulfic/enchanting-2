package com.wulfnet.enchanting2.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Phase 10cFB (owner, 2026-10-10): Last Stand's 10 / 8 / 6 minute cooldown and its HUD clock. */
class LastStandClockTest {
	@Test
	void theCooldownFollowsTheFormulaButNeverDropsBelowLevelFive() {
		assertEquals(12000L, LastStandClock.cooldown(12000.0F), "I: 10 minutes");
		assertEquals(7200L, LastStandClock.cooldown(7200.0F), "III: 6 minutes");
		assertEquals(2400L, LastStandClock.cooldown(2400.0F), "V: 2 minutes");
		assertEquals(2400L, LastStandClock.cooldown(0.0F), "VI's 0 would make the wearer unkillable");
		assertEquals(2400L, LastStandClock.cooldown(-9600.0F), "X");
		assertThrows(IllegalArgumentException.class, () -> LastStandClock.cooldown(Float.NaN));
	}

	@Test
	void theClockShowsMinutesAndSecondsRoundedUp() {
		assertEquals("10:00", LastStandClock.format(12000));
		assertEquals("4:32", LastStandClock.format(272 * 20));
		assertEquals("0:01", LastStandClock.format(1), "never 0:00 while still cooling down");
		assertEquals("0:00", LastStandClock.format(0));
		assertEquals("1:00", LastStandClock.format(1199), "59.95 s rounds up to the minute");
	}
}
