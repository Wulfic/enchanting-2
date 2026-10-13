package com.wulfnet.enchanting2.anvil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wulfnet.enchanting2.config.AnvilSection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleSupplier;
import org.junit.jupiter.api.Test;

/** Owner, 2026-10-13: anvils unbreakable by default; vanilla durability optional, made rarer by a multiplier. */
class AnvilWearTest {
	private static AnvilSection anvil(boolean vanilla, int multiplier) {
		return new AnvilSection(InvalidBookMode.APPLY_VALID, vanilla, multiplier);
	}

	@Test
	void theDefaultAnvilNeverTakesAChip() {
		assertFalse(AnvilSection.DEFAULTS.vanillaDurability(), "unbreakable is the default");
		assertFalse(AnvilWear.takesChip(AnvilSection.DEFAULTS, () -> 0), "not even on the luckiest roll");
		assertFalse(AnvilWear.takesChip(anvil(false, 100), () -> 0), "the multiplier only matters when it's on");
	}

	@Test
	void vanillaDurabilityTakesEveryChipVanillaRolledWithoutAnotherRoll() {
		AtomicInteger rolls = new AtomicInteger();
		DoubleSupplier counted = () -> {
			rolls.incrementAndGet();
			return 0.999;
		};
		assertTrue(AnvilWear.takesChip(anvil(true, 1), counted));
		assertEquals(0, rolls.get(), "vanilla's random sequence is untouched at 1");
	}

	@Test
	void theMultiplierKeepsOneChipInThatMany() {
		AnvilSection twice = anvil(true, 2);
		assertTrue(AnvilWear.takesChip(twice, () -> 0.49));
		assertFalse(AnvilWear.takesChip(twice, () -> 0.5), "half the chips are skipped at 2");
		AnvilSection hundred = anvil(true, 100);
		assertTrue(AnvilWear.takesChip(hundred, () -> 0.0099));
		assertFalse(AnvilWear.takesChip(hundred, () -> 0.01));
	}
}
