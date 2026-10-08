package com.wulfnet.enchanting2.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TablePowerTest {
	@Test
	void everyShelfCountMatchesTheDesignTable() {
		int[] expected = {1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 5};
		for (int shelves = 0; shelves <= 15; shelves++) {
			assertEquals(expected[shelves], TablePower.tier(shelves), shelves + " shelves");
		}
	}

	@Test
	void shelvesOutsideTheVanillaRangeAreClamped() {
		assertEquals(1, TablePower.tier(-1));
		assertEquals(5, TablePower.tier(16));
		assertEquals(5, TablePower.tier(Integer.MAX_VALUE));
	}

	@Test
	void maxSelectableRoundsUp() {
		// {max level, tier 1..5}
		int[][] expected = {{1, 1, 1, 1, 1, 1}, {3, 1, 2, 2, 3, 3}, {4, 1, 2, 3, 4, 4}, {5, 1, 2, 3, 4, 5}};
		for (int[] row : expected) {
			for (int tier = 1; tier <= 5; tier++) {
				assertEquals(row[tier], TablePower.maxSelectable(row[0], tier), "max " + row[0] + " tier " + tier);
			}
		}
	}

	@Test
	void fullPowerUnlocksEveryMaxIncludingTheLargestOverride() {
		for (int max = 1; max <= 255; max++) {
			assertEquals(max, TablePower.maxSelectable(max, TablePower.tier(15)), "max " + max);
			assertTrue(TablePower.maxSelectable(max, 1) >= 1, "max " + max);
		}
		assertTrue(TablePower.fullPower(TablePower.tier(15)));
		assertFalse(TablePower.fullPower(TablePower.tier(14)));
	}

	@Test
	void shelvesForATierAreTheLeastThatReachIt() {
		int[] expected = {0, 4, 8, 12, 15};
		for (int tier = 1; tier <= 5; tier++) {
			assertEquals(expected[tier - 1], TablePower.shelvesFor(tier), "tier " + tier);
			assertEquals(tier, TablePower.tier(TablePower.shelvesFor(tier)), "tier " + tier + " round trip");
			if (TablePower.shelvesFor(tier) > 0) {
				assertEquals(tier - 1, TablePower.tier(TablePower.shelvesFor(tier) - 1), "one fewer, tier " + tier);
			}
		}
	}

	/** Checked against the definition: the least shelf count whose tier sells the level. */
	@Test
	void shelvesForALevelMatchTheDefinition() {
		for (int max = 1; max <= 255; max++) {
			for (int level = 1; level <= max; level++) {
				int least = -1;
				for (int shelves = 0; shelves <= 15 && least < 0; shelves++) {
					if (TablePower.maxSelectable(max, TablePower.tier(shelves)) >= level) {
						least = shelves;
					}
				}
				assertEquals(least, TablePower.shelvesFor(max, level), "max " + max + " level " + level);
			}
		}
		assertEquals(8, TablePower.shelvesFor(5, 3), "Sharpness III");
		assertThrows(IllegalArgumentException.class, () -> TablePower.shelvesFor(5, 6));
		assertThrows(IllegalArgumentException.class, () -> TablePower.shelvesFor(0));
	}

	@Test
	void invalidArgumentsAreRefused() {
		assertThrows(IllegalArgumentException.class, () -> TablePower.maxSelectable(0, 3));
		assertThrows(IllegalArgumentException.class, () -> TablePower.maxSelectable(5, 0));
		assertThrows(IllegalArgumentException.class, () -> TablePower.maxSelectable(5, 6));
	}
}
