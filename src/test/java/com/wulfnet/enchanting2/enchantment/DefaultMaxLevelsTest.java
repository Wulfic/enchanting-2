package com.wulfnet.enchanting2.enchantment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** Phase 10jB: the "default (N)" a max-level field resets to, read from the shipped JSON. */
class DefaultMaxLevelsTest {
	@Test
	void vanillaAndOwnDefaultsComeFromTheJsonThroughTheTable() {
		assertEquals(OptionalInt.of(5), DefaultMaxLevels.of("minecraft:sharpness"), "vanilla 5, no table row");
		assertEquals(OptionalInt.of(5), DefaultMaxLevels.of("minecraft:piercing"), "vanilla 4, DESIGN §8 row 5");
		assertEquals(OptionalInt.of(4), DefaultMaxLevels.of("minecraft:quick_charge"), "vanilla 3, row 4");
		assertEquals(OptionalInt.of(1), DefaultMaxLevels.of("enchanting2:vision"));
		assertEquals(OptionalInt.of(3), DefaultMaxLevels.of("enchanting2:sure_footing"));
	}

	@Test
	void unknownOrMalformedIdsHaveNone() {
		assertEquals(OptionalInt.empty(), DefaultMaxLevels.of("othermod:zap"));
		assertEquals(OptionalInt.empty(), DefaultMaxLevels.of("no_namespace"));
	}
}
