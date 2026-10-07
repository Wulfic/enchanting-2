package com.wulfnet.enchanting2.cost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wulfnet.enchanting2.config.CostsSection;
import com.wulfnet.enchanting2.config.CursesSection;
import com.wulfnet.enchanting2.config.EnchantmentOverrides;
import com.wulfnet.enchanting2.cost.CostBreakdown.Kind;
import com.wulfnet.enchanting2.cost.CostBreakdown.Line;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class CostModelTest {
	static final String MENDING = "minecraft:mending";
	static final String EFFICIENCY = "minecraft:efficiency";
	static final String UNBREAKING = "minecraft:unbreaking";
	static final String FORTUNE = "minecraft:fortune";
	static final String BINDING = "minecraft:binding_curse";
	static final Predicate<String> IS_CURSE = Set.of(BINDING, "minecraft:vanishing_curse")::contains;

	private static CostBreakdown anvil(Map<String, Integer> before, Map<String, Integer> after) {
		return CostModel.price(CostRequest.anvil(before, after), PricingTable.builtIn(), IS_CURSE, CostsSection.DEFAULTS, CursesSection.DEFAULTS);
	}

	private static int cost(Map<String, Integer> before, Map<String, Integer> after) {
		return anvil(before, after).levelsCharged();
	}

	private static PricingTable pricing(String id, int base, int perLevel, UpgradeMode mode) {
		return new PricingTable(new EnchantmentOverrides(Map.of(id, new EnchantmentOverrides.Entry(OptionalInt.empty(),
				OptionalInt.of(base), OptionalInt.of(perLevel), Optional.of(mode), OptionalInt.empty(),
				OptionalInt.empty()))));
	}

	private static CostsSection costs(int maximum, boolean creativeFree) {
		return new CostsSection(1, 1, 2, maximum, creativeFree);
	}

	// --- DESIGN §5 examples table, one row each ---

	@Test
	void mendingAlone() {
		assertEquals(2, cost(Map.of(), Map.of(MENDING, 1)));
	}

	@Test
	void mendingWithEfficiencyOneToFive() {
		int[] expected = {5, 8, 11, 14, 17};
		for (int level = 1; level <= 5; level++) {
			assertEquals(expected[level - 1], cost(Map.of(), Map.of(MENDING, 1, EFFICIENCY, level)), "Eff " + level);
		}
	}

	@Test
	void efficiencyFiveThenMending() {
		int first = cost(Map.of(), Map.of(EFFICIENCY, 5));
		int second = cost(Map.of(EFFICIENCY, 5), Map.of(EFFICIENCY, 5, MENDING, 1));
		assertEquals(5, first);
		assertEquals(12, second);
		assertEquals(17, first + second);
	}

	@Test
	void mendingThenEfficiencyFive() {
		int first = cost(Map.of(), Map.of(MENDING, 1));
		int second = cost(Map.of(MENDING, 1), Map.of(MENDING, 1, EFFICIENCY, 5));
		assertEquals(2, first);
		assertEquals(15, second);
		assertEquals(17, first + second);
	}

	@Test
	void mendingEfficiencyUnbreakingFortune() {
		CostBreakdown b = anvil(Map.of(), Map.of(MENDING, 1, EFFICIENCY, 5, UNBREAKING, 3, FORTUNE, 3));
		assertEquals(35, b.total());
		assertEquals(List.of(
				new Line(Kind.ENCHANT, EFFICIENCY, 0, 5, 5),
				new Line(Kind.ENCHANT, FORTUNE, 0, 3, 3),
				new Line(Kind.ENCHANT, MENDING, 0, 1, 2),
				new Line(Kind.ENCHANT, UNBREAKING, 0, 3, 3),
				new Line(Kind.SURCHARGE, MENDING, 0, 11, 22)), b.lines());
	}

	@Test
	void efficiencyThreeToFiveWithMending() {
		CostBreakdown b = anvil(Map.of(MENDING, 1, EFFICIENCY, 3), Map.of(MENDING, 1, EFFICIENCY, 5));
		assertEquals(6, b.total());
		assertEquals(List.of(new Line(Kind.ENCHANT, EFFICIENCY, 3, 5, 2), new Line(Kind.SURCHARGE, MENDING, 3, 5, 4)),
				b.lines());
	}

	// --- Unchanged, curses, third-party ---

	@Test
	void unchangedItemIsFreeWithNoLines() {
		CostBreakdown b = anvil(Map.of(EFFICIENCY, 5, MENDING, 1), Map.of(EFFICIENCY, 5, MENDING, 1));
		assertEquals(0, b.total());
		assertEquals(List.of(), b.lines());
	}

	@Test
	void curseLineCostsNothingAndCursesDontRaiseTheSurcharge() {
		CostBreakdown b = anvil(Map.of(MENDING, 1), Map.of(MENDING, 1, BINDING, 1));
		assertEquals(0, b.total());
		assertEquals(List.of(new Line(Kind.ENCHANT, BINDING, 0, 1, 0)), b.lines());
		assertEquals(2, cost(Map.of(), Map.of(MENDING, 1, BINDING, 1)));
	}

	@Test
	void thirdPartyEnchantmentsArePricedByDefault() {
		assertEquals(3, cost(Map.of(EFFICIENCY, 2), Map.of(EFFICIENCY, 2, "othermod:zap", 3)));
	}

	// --- Upgrade modes and base cost ---

	@Test
	void upgradeModesChargeEfficiencyThreeToFive() {
		Map<String, Integer> before = Map.of(EFFICIENCY, 3);
		Map<String, Integer> after = Map.of(EFFICIENCY, 5);
		Map<UpgradeMode, Integer> expected = Map.of(UpgradeMode.LEVEL_DIFFERENCE, 6, UpgradeMode.RESULT_LEVEL, 15,
				UpgradeMode.FIXED, 3, UpgradeMode.FREE, 0);
		expected.forEach((mode, levels) -> assertEquals(levels, CostModel.price(CostRequest.anvil(before, after),
				pricing(EFFICIENCY, 0, 3, mode), IS_CURSE, CostsSection.DEFAULTS, CursesSection.DEFAULTS).total(), mode.name()));
	}

	@Test
	void newEnchantmentPaysBaseAndResultLevelWhateverTheUpgradeMode() {
		for (UpgradeMode mode : UpgradeMode.values()) {
			assertEquals(4 + 15, CostModel.price(CostRequest.anvil(Map.of(), Map.of(EFFICIENCY, 5)),
					pricing(EFFICIENCY, 4, 3, mode), IS_CURSE, CostsSection.DEFAULTS, CursesSection.DEFAULTS).total(), mode.name());
		}
	}

	@Test
	void baseCostIsChargedOnlyWhenNewlyAdded() {
		PricingTable p = pricing(EFFICIENCY, 4, 1, UpgradeMode.LEVEL_DIFFERENCE);
		assertEquals(7, CostModel.price(CostRequest.anvil(Map.of(), Map.of(EFFICIENCY, 3)), p, IS_CURSE,
				CostsSection.DEFAULTS, CursesSection.DEFAULTS).total());
		assertEquals(2, CostModel.price(CostRequest.anvil(Map.of(EFFICIENCY, 3), Map.of(EFFICIENCY, 5)), p, IS_CURSE,
				CostsSection.DEFAULTS, CursesSection.DEFAULTS).total());
	}

	// --- Rename, repair, creative, maximum ---

	@Test
	void renameAloneCostsOne() {
		var request = new CostRequest(CostRequest.Station.ANVIL, Map.of(EFFICIENCY, 5), Map.of(EFFICIENCY, 5), true, 0,
				false, false);
		CostBreakdown b = CostModel.price(request, PricingTable.builtIn(), IS_CURSE, CostsSection.DEFAULTS, CursesSection.DEFAULTS);
		assertEquals(1, b.total());
		assertEquals(0, b.enchantCost());
		assertEquals(List.of(new Line(Kind.RENAME, "", 0, 0, 1)), b.lines());
	}

	@Test
	void repairByMaterialsAndByCombining() {
		var materials = new CostRequest(CostRequest.Station.ANVIL, Map.of(), Map.of(), false, 3, false, false);
		assertEquals(3, CostModel.price(materials, PricingTable.builtIn(), IS_CURSE, CostsSection.DEFAULTS, CursesSection.DEFAULTS).total());
		var combine = new CostRequest(CostRequest.Station.ANVIL, Map.of(EFFICIENCY, 3), Map.of(EFFICIENCY, 4), false,
				0, true, false);
		CostBreakdown b = CostModel.price(combine, PricingTable.builtIn(), IS_CURSE, CostsSection.DEFAULTS, CursesSection.DEFAULTS);
		assertEquals(1 + 2, b.total());
		assertEquals(1, b.enchantCost(), "repair is not part of the enchant cost");
		var custom = new CostsSection(5, 4, 9, CostsSection.NO_MAXIMUM, true);
		assertEquals(12, CostModel.price(materials, PricingTable.builtIn(), IS_CURSE, custom, CursesSection.DEFAULTS).total());
		assertEquals(1 + 9, CostModel.price(combine, PricingTable.builtIn(), IS_CURSE, custom, CursesSection.DEFAULTS).total());
	}

	@Test
	void creativeIsFreeButKeepsItsLines() {
		var request = CostRequest.table(Map.of(), Map.of(MENDING, 1, EFFICIENCY, 5), true);
		CostBreakdown b = CostModel.price(request, PricingTable.builtIn(), IS_CURSE, CostsSection.DEFAULTS, CursesSection.DEFAULTS);
		assertTrue(b.free());
		assertEquals(17, b.total());
		assertEquals(0, b.levelsCharged());
		assertEquals(0, b.lapisCharged());
		assertEquals(3, b.lines().size());

		CostBreakdown paid = CostModel.price(request, PricingTable.builtIn(), IS_CURSE, costs(-1, false), CursesSection.DEFAULTS);
		assertFalse(paid.free());
		assertEquals(17, paid.levelsCharged());
		assertEquals(17, paid.lapisCharged());
	}

	@Test
	void maximumOperationCostRefusesOnlyTotalsAboveIt() {
		var request = CostRequest.anvil(Map.of(), Map.of(MENDING, 1, EFFICIENCY, 5));
		assertTrue(CostModel.price(request, PricingTable.builtIn(), IS_CURSE, costs(16, true), CursesSection.DEFAULTS).tooExpensive());
		assertFalse(CostModel.price(request, PricingTable.builtIn(), IS_CURSE, costs(17, true), CursesSection.DEFAULTS).tooExpensive());
		assertFalse(CostModel.price(request, PricingTable.builtIn(), IS_CURSE, costs(-1, true), CursesSection.DEFAULTS).tooExpensive());
		var rename = new CostRequest(CostRequest.Station.ANVIL, Map.of(), Map.of(), true, 0, false, false);
		assertTrue(CostModel.price(rename, PricingTable.builtIn(), IS_CURSE, costs(0, true), CursesSection.DEFAULTS).tooExpensive(),
				"0 is a real maximum: renaming for 1 is refused");
	}

	@Test
	void creativeIsNeverTooExpensiveEvenWhenPaying() {
		var request = new CostRequest(CostRequest.Station.ANVIL, Map.of(), Map.of(MENDING, 1, EFFICIENCY, 5), false, 0,
				false, true);
		assertFalse(CostModel.price(request, PricingTable.builtIn(), IS_CURSE, costs(0, false), CursesSection.DEFAULTS).tooExpensive());
		assertFalse(CostModel.price(request, PricingTable.builtIn(), IS_CURSE, costs(0, true), CursesSection.DEFAULTS).tooExpensive());
	}

	// --- Lapis ---

	@Test
	void lapisIsTheEnchantCostAtTheTableAndNeverAtTheAnvil() {
		var after = Map.of(MENDING, 1, EFFICIENCY, 5);
		assertEquals(17, CostModel.price(CostRequest.table(Map.of(), after, false), PricingTable.builtIn(), IS_CURSE,
				CostsSection.DEFAULTS, CursesSection.DEFAULTS).lapisCharged());
		var anvil = new CostRequest(CostRequest.Station.ANVIL, Map.of(), after, true, 0, false, false);
		CostBreakdown b = CostModel.price(anvil, PricingTable.builtIn(), IS_CURSE, CostsSection.DEFAULTS, CursesSection.DEFAULTS);
		assertEquals(18, b.levelsCharged());
		assertEquals(0, b.lapisCharged());
	}

	@Test
	void lapisIsCappedAtSixtyFour() {
		CostBreakdown b = CostModel.price(CostRequest.table(Map.of(), Map.of(EFFICIENCY, 5), false),
				pricing(EFFICIENCY, 0, 100, UpgradeMode.LEVEL_DIFFERENCE), IS_CURSE, CostsSection.DEFAULTS, CursesSection.DEFAULTS);
		assertEquals(500, b.levelsCharged());
		assertEquals(64, b.lapisCharged());
	}

	@Test
	void totalsSaturateInsteadOfWrapping() {
		Map<String, EnchantmentOverrides.Entry> entries = new HashMap<>();
		Map<String, Integer> after = new HashMap<>();
		for (int i = 0; i < 1000; i++) {
			String id = "othermod:e" + i;
			entries.put(id, new EnchantmentOverrides.Entry(OptionalInt.empty(), OptionalInt.of(10_000),
					OptionalInt.of(10_000), Optional.empty(), OptionalInt.of(100), OptionalInt.empty()));
			after.put(id, 255);
		}
		CostBreakdown b = CostModel.price(CostRequest.table(Map.of(), after, false),
				new PricingTable(new EnchantmentOverrides(entries)), IS_CURSE, CostsSection.DEFAULTS, CursesSection.DEFAULTS);
		assertEquals(Integer.MAX_VALUE, b.total());
		assertEquals(Integer.MAX_VALUE, b.enchantCost());
		assertEquals(64, b.lapis());
		assertTrue(b.lines().stream().allMatch(l -> l.amount() >= 0));
	}

	// --- Request validation ---

	@Test
	void requestRejectsRemovalLoweringAndBadLevels() {
		assertThrows(IllegalArgumentException.class, () -> CostRequest.anvil(Map.of(EFFICIENCY, 5), Map.of()));
		assertThrows(IllegalArgumentException.class,
				() -> CostRequest.anvil(Map.of(EFFICIENCY, 5), Map.of(EFFICIENCY, 4)));
		assertThrows(IllegalArgumentException.class, () -> CostRequest.anvil(Map.of(), Map.of(EFFICIENCY, 0)));
		assertThrows(IllegalArgumentException.class, () -> CostRequest.anvil(Map.of(EFFICIENCY, 0), Map.of()));
	}

	@Test
	void requestRejectsImpossibleExtras() {
		assertThrows(IllegalArgumentException.class,
				() -> new CostRequest(CostRequest.Station.TABLE, Map.of(), Map.of(), true, 0, false, false));
		assertThrows(IllegalArgumentException.class,
				() -> new CostRequest(CostRequest.Station.TABLE, Map.of(), Map.of(), false, 1, false, false));
		assertThrows(IllegalArgumentException.class,
				() -> new CostRequest(CostRequest.Station.ANVIL, Map.of(), Map.of(), false, 2, true, false));
		assertThrows(IllegalArgumentException.class,
				() -> new CostRequest(CostRequest.Station.ANVIL, Map.of(), Map.of(), false, -1, false, false));
	}

	@Test
	void breakdownRejectsTotalsThatDontMatchItsLines() {
		var lines = List.of(new Line(Kind.ENCHANT, EFFICIENCY, 0, 5, 5));
		assertThrows(IllegalArgumentException.class, () -> new CostBreakdown(lines, 5, 6, 0, false, false));
		assertThrows(IllegalArgumentException.class, () -> new CostBreakdown(lines, 4, 5, 0, false, false));
		assertThrows(IllegalArgumentException.class, () -> new CostBreakdown(lines, 5, 5, 65, false, false));
	}
}
