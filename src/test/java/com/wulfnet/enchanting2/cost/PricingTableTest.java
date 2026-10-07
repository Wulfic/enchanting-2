package com.wulfnet.enchanting2.cost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.wulfnet.enchanting2.config.EnchantmentOverrides;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class PricingTableTest {
	private static final OptionalInt NONE = OptionalInt.empty();

	private static PricingTable with(String id, EnchantmentOverrides.Entry entry) {
		return new PricingTable(new EnchantmentOverrides(Map.of(id, entry)));
	}

	@Test
	void defaultEnchantmentIsOneLevelPerLevelAndUpgradesPayTheDifference() {
		Pricing p = PricingTable.builtIn().get("minecraft:efficiency");
		assertEquals(new Pricing(0, 1, UpgradeMode.LEVEL_DIFFERENCE, 0, 10), p);
		assertEquals(p, PricingTable.builtIn().get("othermod:zap"), "third-party IDs get the default");
	}

	/** §5 "Mending alone = 2" and "Mending + Eff I = 5" force per-level 0 and surcharge 2. */
	@Test
	void mendingIsBaseTwoPerLevelZeroSurchargeTwo() {
		Pricing p = PricingTable.builtIn().get("minecraft:mending");
		assertEquals(2, p.baseCost());
		assertEquals(0, p.costPerLevel());
		assertEquals(2, p.otherLevelsSurcharge());
	}

	@Test
	void vanillaCursesDiscountTenPercent() {
		assertEquals(10, PricingTable.builtIn().get("minecraft:binding_curse").discountPercent());
		assertEquals(10, PricingTable.builtIn().get("minecraft:vanishing_curse").discountPercent());
	}

	@Test
	void eachFieldOverridesAlone() {
		String id = "minecraft:efficiency";
		Pricing d = PricingTable.DEFAULT;
		assertEquals(new Pricing(7, 1, d.upgradeMode(), 0, 10),
				with(id, new EnchantmentOverrides.Entry(NONE, OptionalInt.of(7), NONE, Optional.empty(), NONE, NONE))
						.get(id));
		assertEquals(new Pricing(0, 3, d.upgradeMode(), 0, 10),
				with(id, new EnchantmentOverrides.Entry(NONE, NONE, OptionalInt.of(3), Optional.empty(), NONE, NONE))
						.get(id));
		assertEquals(new Pricing(0, 1, UpgradeMode.FREE, 0, 10),
				with(id, new EnchantmentOverrides.Entry(NONE, NONE, NONE, Optional.of(UpgradeMode.FREE), NONE, NONE))
						.get(id));
		assertEquals(new Pricing(0, 1, d.upgradeMode(), 4, 10),
				with(id, new EnchantmentOverrides.Entry(NONE, NONE, NONE, Optional.empty(), OptionalInt.of(4), NONE))
						.get(id));
		assertEquals(new Pricing(0, 1, d.upgradeMode(), 0, 15),
				with(id, new EnchantmentOverrides.Entry(NONE, NONE, NONE, Optional.empty(), NONE, OptionalInt.of(15)))
						.get(id));
	}

	@Test
	void partlyOverriddenMendingKeepsItsOtherBuiltInFields() {
		String id = "minecraft:mending";
		Pricing p = with(id, new EnchantmentOverrides.Entry(NONE, NONE, NONE, Optional.empty(), OptionalInt.of(3),
				NONE)).get(id);
		assertEquals(new Pricing(2, 0, UpgradeMode.LEVEL_DIFFERENCE, 3, 10), p);
	}

	@Test
	void maxLevelOnlyEntryChangesNoPrice() {
		String id = "minecraft:mending";
		assertEquals(PricingTable.builtIn().get(id), with(id, new EnchantmentOverrides.Entry(OptionalInt.of(1))).get(id));
	}

	@Test
	void pricingRejectsNegativeAndOutOfRangeValues() {
		assertThrows(IllegalArgumentException.class, () -> new Pricing(-1, 1, UpgradeMode.FREE, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> new Pricing(0, -1, UpgradeMode.FREE, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> new Pricing(0, 1, UpgradeMode.FREE, -1, 0));
		assertThrows(IllegalArgumentException.class, () -> new Pricing(0, 1, UpgradeMode.FREE, 0, 101));
		assertThrows(NullPointerException.class, () -> new Pricing(0, 1, null, 0, 0));
	}
}
