package com.wulfnet.enchanting2.cost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wulfnet.enchanting2.config.CostsSection;
import com.wulfnet.enchanting2.config.CursesSection;
import com.wulfnet.enchanting2.config.EnchantmentOverrides;
import com.wulfnet.enchanting2.cost.CostBreakdown.Kind;
import com.wulfnet.enchanting2.cost.CostBreakdown.Line;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/** DESIGN §5a: curses discount the enchant cost; Cleanse repays it; Extract prices one enchantment. */
class CurseCostTest {
	private static final String MENDING = CostModelTest.MENDING;
	private static final String EFFICIENCY = CostModelTest.EFFICIENCY;
	private static final String BINDING = CostModelTest.BINDING;
	/** Stand-ins for the Phase 10 curses at 15%. */
	private static final List<String> HEAVY = List.of("e2test:weight", "e2test:greed", "e2test:fumbling",
			"e2test:hunger");
	private static final String UNKNOWN_CURSE = "othermod:doom";
	private static final Predicate<String> IS_CURSE = id -> CostModelTest.IS_CURSE.test(id) || HEAVY.contains(id)
			|| id.equals(UNKNOWN_CURSE);

	private static EnchantmentOverrides.Entry entry(int base, int perLevel, UpgradeMode mode, int surcharge,
			int discount) {
		return new EnchantmentOverrides.Entry(OptionalInt.empty(), OptionalInt.of(base), OptionalInt.of(perLevel),
				Optional.of(mode), OptionalInt.of(surcharge), OptionalInt.of(discount));
	}

	private static EnchantmentOverrides.Entry discount(int percent) {
		return new EnchantmentOverrides.Entry(OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty(),
				Optional.empty(), OptionalInt.empty(), OptionalInt.of(percent));
	}

	/** Built-in prices, the four 15% curses, and optionally Efficiency at {@code efficiencyPerLevel}. */
	private static PricingTable pricing(int efficiencyPerLevel) {
		Map<String, EnchantmentOverrides.Entry> entries = new HashMap<>();
		HEAVY.forEach(id -> entries.put(id, discount(15)));
		if (efficiencyPerLevel != 1) {
			entries.put(EFFICIENCY, entry(0, efficiencyPerLevel, UpgradeMode.LEVEL_DIFFERENCE, 0, 10));
		}
		return new PricingTable(new EnchantmentOverrides(entries));
	}

	private static final PricingTable PRICING = pricing(1);

	private static CostBreakdown price(CostRequest request, PricingTable pricing, CursesSection curses) {
		return CostModel.price(request, pricing, IS_CURSE, CostsSection.DEFAULTS, curses);
	}

	private static CostBreakdown anvil(Map<String, Integer> before, Map<String, Integer> after) {
		return price(CostRequest.anvil(before, after), PRICING, CursesSection.DEFAULTS);
	}

	// --- Discount ---

	/** §5a's worked example: Mending + Efficiency V (17) + Curse of Weight → ceil(17 × 0.85) = 15; cleanse → 30. */
	@Test
	void designExample() {
		Map<String, Integer> item = Map.of(MENDING, 1, EFFICIENCY, 5, "e2test:weight", 1);
		CostBreakdown b = anvil(Map.of(), item);
		assertEquals(15, b.total());
		assertEquals(15, b.enchantCost());
		assertEquals(new Line(Kind.CURSE_DISCOUNT, "", 15, 15, -2), b.lines().getLast());
		assertEquals(30, CostModel.cleanseCost(item, "e2test:weight", PRICING, IS_CURSE, CursesSection.DEFAULTS));
	}

	@Test
	void vanillaCursesAndUnknownCursesDiscountTenPercentRoundedUp() {
		assertEquals(16, anvil(Map.of(), Map.of(MENDING, 1, EFFICIENCY, 5, BINDING, 1)).total(), "ceil(15.3)");
		assertEquals(16, anvil(Map.of(), Map.of(MENDING, 1, EFFICIENCY, 5, UNKNOWN_CURSE, 1)).total());
		assertEquals(9, price(CostRequest.anvil(Map.of(), Map.of(EFFICIENCY, 5, BINDING, 1)), pricing(2),
				CursesSection.DEFAULTS).total(), "10 × 0.9 is exact");
	}

	@Test
	void discountsAddUpToTheCap() {
		Map<String, Integer> item = new HashMap<>(Map.of(EFFICIENCY, 5));
		HEAVY.forEach(id -> item.put(id, 1));
		CostBreakdown b = price(CostRequest.anvil(Map.of(), item), pricing(20), CursesSection.DEFAULTS);
		assertEquals(55, b.total(), "100 at 45%, not 60%");
		assertEquals(new Line(Kind.CURSE_DISCOUNT, "", 60, 45, -45), b.lines().getLast());
		assertEquals(40, price(CostRequest.anvil(Map.of(), item), pricing(20), new CursesSection(95, 30)).total(),
				"under a higher cap all 60% applies");
		assertEquals(100, price(CostRequest.anvil(Map.of(), item), pricing(20), new CursesSection(0, 30)).total(),
				"cap 0 turns the discount off");
	}

	@Test
	void curseAlreadyOnTheItemDiscountsLaterEnchanting() {
		assertEquals(13, anvil(Map.of("e2test:weight", 1, MENDING, 1), Map.of("e2test:weight", 1, MENDING, 1,
				EFFICIENCY, 5)).total(), "Eff V 5 + surcharge 10 = 15, ceil(15 × 0.85) = ceil(12.75)");
	}

	@Test
	void renameAndRepairAreNeverDiscounted() {
		var request = new CostRequest(CostRequest.Station.ANVIL, Map.of(EFFICIENCY, 3, "e2test:weight", 1,
				"e2test:greed", 1, "e2test:hunger", 1), Map.of(EFFICIENCY, 5, "e2test:weight", 1, "e2test:greed", 1,
				"e2test:hunger", 1), true, 0, true, false);
		CostBreakdown b = price(request, PRICING, CursesSection.DEFAULTS);
		assertEquals(2, b.enchantCost(), "ceil(2 × 0.55)");
		assertEquals(2 + 1 + 2, b.total());

		var renameOnly = new CostRequest(CostRequest.Station.ANVIL, Map.of(BINDING, 1), Map.of(BINDING, 1), true, 4,
				false, false);
		CostBreakdown r = price(renameOnly, PRICING, CursesSection.DEFAULTS);
		assertEquals(1 + 4, r.total());
		assertTrue(r.lines().stream().noneMatch(l -> l.kind() == Kind.CURSE_DISCOUNT), "nothing to discount");
	}

	@Test
	void tableLapisFollowsTheDiscountedCost() {
		CostBreakdown b = price(CostRequest.table(Map.of(), Map.of(MENDING, 1, EFFICIENCY, 5, "e2test:weight", 1),
				false), PRICING, CursesSection.DEFAULTS);
		assertEquals(15, b.lapisCharged());
	}

	@Test
	void discountOnASaturatedCostStillAddsUp() {
		Map<String, EnchantmentOverrides.Entry> entries = new HashMap<>();
		Map<String, Integer> after = new HashMap<>();
		for (int i = 0; i < 1000; i++) {
			entries.put("othermod:e" + i, entry(10_000, 10_000, UpgradeMode.LEVEL_DIFFERENCE, 100, 0));
			after.put("othermod:e" + i, 255);
		}
		entries.put(BINDING, discount(100));
		after.put(BINDING, 1);
		CostBreakdown b = price(CostRequest.table(Map.of(), after, false), new PricingTable(new EnchantmentOverrides(
				entries)), new CursesSection(95, 30));
		long raw = 1000L * (10_000 + 10_000 * 255) + 1000L * 100 * (999L * 255);
		assertEquals(CostBreakdown.saturate(Math.ceilDiv(raw * 5, 100)), b.enchantCost());
		assertTrue(b.lines().getLast().amount() < Integer.MIN_VALUE, "the discount line itself is past int range");
	}

	// --- Cleanse ---

	@Test
	void cleanseRepaysTheRemovedCursesOwnPercentNotTheMarginalDrop() {
		Map<String, Integer> item = new HashMap<>(Map.of(EFFICIENCY, 5));
		HEAVY.forEach(id -> item.put(id, 1));
		// 60% raw is capped at 45%, so removing one 15% curse changes the discount by nothing (marginal = 0 → 30).
		assertEquals(75, CostModel.cleanseCost(item, "e2test:weight", pricing(100), IS_CURSE, CursesSection.DEFAULTS),
				"15% of 500");
	}

	@Test
	void cleanseCostsAtLeastTheMinimumAndCountsTheSurcharge() {
		Map<String, Integer> item = Map.of(MENDING, 1, EFFICIENCY, 5, BINDING, 1);
		assertEquals(30, CostModel.cleanseCost(item, BINDING, PRICING, IS_CURSE, CursesSection.DEFAULTS));
		assertEquals(2, CostModel.cleanseCost(item, BINDING, PRICING, IS_CURSE, new CursesSection(45, 0)),
				"ceil(10% of 17)");
		PricingTable full = new PricingTable(new EnchantmentOverrides(Map.of(BINDING, discount(100))));
		assertEquals(17, CostModel.cleanseCost(item, BINDING, full, IS_CURSE, new CursesSection(45, 0)),
				"the build cost includes Mending's surcharge");
	}

	@Test
	void cleanseRejectsNonCursesAndCursesNotOnTheItem() {
		Map<String, Integer> item = Map.of(EFFICIENCY, 5, BINDING, 1);
		assertThrows(IllegalArgumentException.class,
				() -> CostModel.cleanseCost(item, EFFICIENCY, PRICING, IS_CURSE, CursesSection.DEFAULTS));
		assertThrows(IllegalArgumentException.class,
				() -> CostModel.cleanseCost(item, "e2test:weight", PRICING, IS_CURSE, CursesSection.DEFAULTS));
	}

	// --- Extract ---

	@Test
	void extractIsThePriceOfAddingItToABlankItem() {
		assertEquals(5, CostModel.extractCost(EFFICIENCY, 5, PRICING, IS_CURSE));
		assertEquals(2, CostModel.extractCost(MENDING, 1, PRICING, IS_CURSE));
		PricingTable custom = new PricingTable(new EnchantmentOverrides(Map.of(EFFICIENCY,
				entry(3, 2, UpgradeMode.FREE, 0, 0))));
		assertEquals(3 + 2 * 4, CostModel.extractCost(EFFICIENCY, 4, custom, IS_CURSE));
	}

	@Test
	void extractRejectsCursesAndBadLevels() {
		assertThrows(IllegalArgumentException.class, () -> CostModel.extractCost(BINDING, 1, PRICING, IS_CURSE));
		assertThrows(IllegalArgumentException.class, () -> CostModel.extractCost(EFFICIENCY, 0, PRICING, IS_CURSE));
	}

	// --- Property: curse → enchant → cleanse is never cheaper than enchanting clean ---

	private static final long SEED = 0x5EED_E2_5AL;
	private static final int CASES = 1_000;
	private static final List<String> ENCHANTS = List.of(MENDING, EFFICIENCY, CostModelTest.UNBREAKING,
			CostModelTest.FORTUNE, "minecraft:silk_touch", "othermod:zap");
	private static final List<String> CURSES = List.of(BINDING, "minecraft:vanishing_curse", "e2test:weight",
			"e2test:greed", "e2test:fumbling", "e2test:hunger");

	/**
	 * The cursed route is compared with the cheaper of the same steps done clean and the item built clean at once.
	 * Under LEVEL_DIFFERENCE those are equal; RESULT_LEVEL steps cost more than at once and FIXED/FREE steps less, and
	 * the cursed route lies between its own two parts, so this bound holds for every mode.
	 */
	@Test
	void curseEnchantCleanseIsNeverCheaperThanEnchantingClean() {
		Random random = new Random(SEED);
		UpgradeMode[] modes = UpgradeMode.values();
		for (int c = 0; c < CASES; c++) {
			Map<String, EnchantmentOverrides.Entry> entries = new HashMap<>();
			for (String id : ENCHANTS) {
				entries.put(id, entry(random.nextInt(11), random.nextInt(61), modes[random.nextInt(modes.length)],
						random.nextInt(4) == 0 ? random.nextInt(4) : 0, 0));
			}
			for (String id : CURSES) {
				entries.put(id, discount(5 + random.nextInt(26)));
			}
			PricingTable pricing = new PricingTable(new EnchantmentOverrides(entries));
			CursesSection curses = new CursesSection(random.nextInt(96), random.nextInt(31));

			Map<String, Integer> curseSet = new HashMap<>();
			for (String id : CURSES) {
				if (random.nextInt(3) == 0) {
					curseSet.put(id, 1);
				}
			}
			if (curseSet.isEmpty()) {
				curseSet.put(CURSES.get(random.nextInt(CURSES.size())), 1);
			}
			Map<String, Integer> target = new HashMap<>();
			for (String id : ENCHANTS) {
				if (random.nextInt(3) != 0) {
					target.put(id, 1 + random.nextInt(5));
				}
			}
			if (target.isEmpty()) {
				target.put(EFFICIENCY, 1 + random.nextInt(5));
			}
			List<Map<String, Integer>> steps = randomBuild(random, target);

			long clean = 0;
			long cursed = cost(Map.of(), curseSet, pricing, curses);
			Map<String, Integer> current = Map.of();
			for (Map<String, Integer> next : steps) {
				clean += cost(current, next, pricing, curses);
				cursed += cost(plus(current, curseSet), plus(next, curseSet), pricing, curses);
				current = next;
			}
			Map<String, Integer> finished = plus(target, curseSet);
			List<String> order = new ArrayList<>(curseSet.keySet());
			Collections.shuffle(order, random);
			for (String curse : order) {
				cursed += CostModel.cleanseCost(finished, curse, pricing, IS_CURSE, curses);
				finished.remove(curse);
			}
			long atOnce = cost(Map.of(), target, pricing, curses);
			assertTrue(cursed >= Math.min(clean, atOnce), "case " + c + ": cursed " + cursed + " < clean "
					+ Math.min(clean, atOnce) + " for " + target + " + " + curseSet + " built as " + steps);
		}
	}

	private static long cost(Map<String, Integer> before, Map<String, Integer> after, PricingTable pricing,
			CursesSection curses) {
		return CostModel.price(CostRequest.anvil(before, after), pricing, IS_CURSE, CostsSection.DEFAULTS, curses)
				.total();
	}

	private static Map<String, Integer> plus(Map<String, Integer> a, Map<String, Integer> b) {
		Map<String, Integer> out = new HashMap<>(a);
		out.putAll(b);
		return out;
	}

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
