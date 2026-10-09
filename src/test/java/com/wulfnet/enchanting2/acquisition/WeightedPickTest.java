package com.wulfnet.enchanting2.acquisition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

class WeightedPickTest {
	private static final Map<String, Integer> WEIGHTS = Map.of("sharpness", 10, "mending", 2, "off", 0);

	private static int weight(String id) {
		return WEIGHTS.get(id);
	}

	@Test
	void emptyListPicksNothingAndNeverRolls() {
		Optional<String> picked = WeightedPick.pick(List.of(), WeightedPickTest::weight, bound -> {
			throw new AssertionError("rolled with nothing to pick");
		});
		assertEquals(Optional.empty(), picked);
	}

	@Test
	void allZeroWeightsPickNothingAndNeverRoll() {
		Optional<String> picked = WeightedPick.pick(List.of("off"), WeightedPickTest::weight, bound -> {
			throw new AssertionError("rolled with zero total weight");
		});
		assertEquals(Optional.empty(), picked);
	}

	@Test
	void eachRollMapsToTheItemWhoseWeightCoversIt() {
		List<String> items = List.of("sharpness", "off", "mending");
		// Total 12: rolls 0-9 land on sharpness, 10-11 on mending; "off" covers no roll.
		for (int roll = 0; roll < 12; roll++) {
			int fixed = roll;
			String expected = roll < 10 ? "sharpness" : "mending";
			assertEquals(Optional.of(expected), WeightedPick.pick(items, WeightedPickTest::weight, bound -> {
				assertEquals(12, bound);
				return fixed;
			}), "roll " + roll);
		}
	}

	@Test
	void frequenciesFollowTheWeights() {
		Random random = new Random(9001);
		Map<String, Integer> counts = new HashMap<>();
		int draws = 12_000;
		for (int i = 0; i < draws; i++) {
			String picked = WeightedPick.pick(List.of("sharpness", "mending", "off"), WeightedPickTest::weight,
					random::nextInt).orElseThrow();
			counts.merge(picked, 1, Integer::sum);
		}
		// Expected 10,000 / 2,000 / 0. The bounds are about five standard deviations wide.
		assertEquals(0, counts.getOrDefault("off", 0));
		int mending = counts.get("mending");
		assertTrue(mending > 1800 && mending < 2200, "mending " + mending);
	}
}
