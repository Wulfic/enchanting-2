package com.wulfnet.enchanting2.cost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.wulfnet.enchanting2.config.AcquisitionSettings;
import com.wulfnet.enchanting2.config.CostsSection;
import com.wulfnet.enchanting2.config.CursesSection;
import com.wulfnet.enchanting2.config.EnchantmentOverrides;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/**
 * DESIGN §5a, Phase 10e (owner, 2026-10-12): the five new curses discount 15% at level I and 5% more per level, still
 * capped by {@code max_discount_percent}; Cleanse repays the curse's discount at its level.
 */
class CurseLevelDiscountTest {
	private static final String FRAGILITY = "enchanting2:fragility";
	private static final String GREED = "enchanting2:greed";
	private static final String EFFICIENCY = CostModelTest.EFFICIENCY;
	private static final Set<String> NEW_CURSES = Set.of(FRAGILITY, "enchanting2:weight", GREED,
			"enchanting2:fumbling", "enchanting2:hunger");
	private static final Predicate<String> IS_CURSE = id -> NEW_CURSES.contains(id) || CostModelTest.IS_CURSE.test(id);

	private static CostBreakdown price(Map<String, Integer> after, PricingTable pricing) {
		return CostModel.price(CostRequest.anvil(Map.of(), after), pricing, IS_CURSE, CostsSection.DEFAULTS,
				CursesSection.DEFAULTS);
	}

	@Test
	void discountAtAddsTheStepPerLevelAndStopsAt100() {
		Pricing curse = new Pricing(0, 1, UpgradeMode.LEVEL_DIFFERENCE, 0, 15, 5);
		assertEquals(15, curse.discountAt(1));
		assertEquals(25, curse.discountAt(3));
		assertEquals(35, curse.discountAt(5));
		assertEquals(100, curse.discountAt(255));
		assertEquals(10, PricingTable.DEFAULT.discountAt(255), "no step: the same at every level");
		assertThrows(IllegalArgumentException.class, () -> curse.discountAt(0));
		assertThrows(IllegalArgumentException.class,
				() -> new Pricing(0, 1, UpgradeMode.LEVEL_DIFFERENCE, 0, 15, 101));
	}

	@Test
	void everyNewCurseIs15PlusFivePerLevelBuiltIn() {
		for (String id : NEW_CURSES) {
			Pricing pricing = PricingTable.builtIn().get(id);
			assertEquals(15, pricing.discountAt(1), id);
			assertEquals(35, pricing.discountAt(5), id);
		}
		assertEquals(10, PricingTable.builtIn().get("minecraft:binding_curse").discountAt(1), "vanilla unchanged");
	}

	@Test
	void theResultsCurseLevelSetsTheDiscount() {
		// Efficiency V = 5. Fragility I: ceil(5 × 0.85) = 5; V: ceil(5 × 0.65) = 4.
		assertEquals(5, price(Map.of(EFFICIENCY, 5, FRAGILITY, 1), PricingTable.builtIn()).enchantCost());
		CostBreakdown five = price(Map.of(EFFICIENCY, 5, FRAGILITY, 5), PricingTable.builtIn());
		assertEquals(4, five.enchantCost());
		assertEquals(35, five.lines().stream().filter(l -> l.kind() == CostBreakdown.Kind.CURSE_DISCOUNT)
				.findFirst().orElseThrow().from());
	}

	@Test
	void twoCursesAtVHitTheCap() {
		// 35 + 35 = 70, capped at 45: Efficiency V → ceil(5 × 0.55) = 3.
		CostBreakdown capped = price(Map.of(EFFICIENCY, 5, FRAGILITY, 5, GREED, 5), PricingTable.builtIn());
		assertEquals(3, capped.enchantCost());
		CostBreakdown.Line line = capped.lines().stream().filter(l -> l.kind() == CostBreakdown.Kind.CURSE_DISCOUNT)
				.findFirst().orElseThrow();
		assertEquals(70, line.from());
		assertEquals(45, line.to());
	}

	@Test
	void cleanseRepaysTheDiscountAtTheCursesLevel() {
		// Efficiency V at 4 per level = 20. Greed III = 25% → 5; Greed V = 35% → 7. No minimum, to see the percent.
		PricingTable pricing = new PricingTable(new EnchantmentOverrides(Map.of(EFFICIENCY,
				new EnchantmentOverrides.Entry(OptionalInt.empty(), OptionalInt.empty(), OptionalInt.of(4),
						Optional.empty(), OptionalInt.empty(), OptionalInt.empty()))));
		CursesSection noMinimum = new CursesSection(45, 0);
		assertEquals(5, CostModel.cleanseCost(Map.of(EFFICIENCY, 5, GREED, 3), GREED, pricing, IS_CURSE, noMinimum));
		assertEquals(7, CostModel.cleanseCost(Map.of(EFFICIENCY, 5, GREED, 5), GREED, pricing, IS_CURSE, noMinimum));
	}

	@Test
	void aFileOverrideOfTheStepWins() {
		PricingTable pricing = new PricingTable(new EnchantmentOverrides(Map.of(GREED,
				new EnchantmentOverrides.Entry(OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty(),
						Optional.empty(), OptionalInt.empty(), OptionalInt.empty(), OptionalInt.of(10),
						AcquisitionSettings.NONE))));
		assertEquals(55, pricing.get(GREED).discountAt(5));
		assertEquals(15, pricing.get(GREED).discountAt(1), "level I's percent is still the built-in");
	}
}
