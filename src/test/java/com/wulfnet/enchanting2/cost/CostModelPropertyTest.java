package com.wulfnet.enchanting2.cost;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.wulfnet.enchanting2.config.CostsSection;
import com.wulfnet.enchanting2.config.CursesSection;
import com.wulfnet.enchanting2.config.EnchantmentOverrides;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * DESIGN §5: "the finished item costs the same however it was built". Seeded, so a failure reproduces; the message
 * names the case.
 */
class CostModelPropertyTest {
	private static final long SEED = 0x5EED_E2_05L;
	private static final int CASES = 1_000;
	/** §5a's discount is applied per operation and rounds up, so it is deliberately not order-independent. */
	private static final CursesSection NO_DISCOUNT = new CursesSection(0, 30);
	private static final List<String> POOL = List.of(CostModelTest.MENDING, CostModelTest.EFFICIENCY,
			CostModelTest.UNBREAKING, CostModelTest.FORTUNE, CostModelTest.BINDING, "minecraft:silk_touch",
			"minecraft:sharpness", "othermod:zap", "othermod:zip");

	@Test
	void anyBuildOrderCostsTheSameAsBuildingAtOnce() {
		Random random = new Random(SEED);
		for (int c = 0; c < CASES; c++) {
			PricingTable pricing = c % 2 == 0 ? PricingTable.builtIn() : randomPricing(random);
			Map<String, Integer> target = randomItem(random);
			List<Map<String, Integer>> steps = randomBuild(random, target);

			long stepwise = 0;
			Map<String, Integer> current = Map.of();
			for (Map<String, Integer> next : steps) {
				stepwise += CostModel.price(CostRequest.anvil(current, next), pricing, CostModelTest.IS_CURSE,
						CostsSection.DEFAULTS, NO_DISCOUNT).total();
				current = next;
			}
			int atOnce = CostModel.price(CostRequest.anvil(Map.of(), target), pricing, CostModelTest.IS_CURSE,
					CostsSection.DEFAULTS, NO_DISCOUNT).total();
			assertEquals(atOnce, stepwise, "case " + c + ": " + target + " built as " + steps);
		}
	}

	/** Every field random except the mode: only LEVEL_DIFFERENCE upgrades are order-independent (RESULT_LEVEL re-pays). */
	private static PricingTable randomPricing(Random random) {
		Map<String, EnchantmentOverrides.Entry> entries = new HashMap<>();
		for (String id : POOL) {
			entries.put(id, new EnchantmentOverrides.Entry(OptionalInt.empty(), OptionalInt.of(random.nextInt(6)),
					OptionalInt.of(random.nextInt(6)), Optional.of(UpgradeMode.LEVEL_DIFFERENCE),
					OptionalInt.of(random.nextInt(3) == 0 ? 1 + random.nextInt(3) : 0), OptionalInt.empty()));
		}
		return new PricingTable(new EnchantmentOverrides(entries));
	}

	private static Map<String, Integer> randomItem(Random random) {
		Map<String, Integer> item = new HashMap<>();
		for (String id : POOL) {
			if (random.nextInt(3) != 0) {
				item.put(id, 1 + random.nextInt(5));
			}
		}
		if (item.isEmpty()) {
			item.put(CostModelTest.MENDING, 1);
		}
		return item;
	}

	/** Random operations, each raising a random subset to random intermediate levels, ending at {@code target}. */
	private static List<Map<String, Integer>> randomBuild(Random random, Map<String, Integer> target) {
		List<Map<String, Integer>> steps = new ArrayList<>();
		Map<String, Integer> current = new HashMap<>();
		while (!current.equals(target)) {
			Map<String, Integer> next = new HashMap<>(current);
			for (Map.Entry<String, Integer> e : target.entrySet()) {
				int have = current.getOrDefault(e.getKey(), 0);
				if (have < e.getValue() && random.nextBoolean()) {
					next.put(e.getKey(), have + 1 + random.nextInt(e.getValue() - have));
				}
			}
			if (!next.equals(current)) {
				steps.add(Map.copyOf(next));
				current = next;
			}
		}
		return steps;
	}
}
