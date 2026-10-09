package com.wulfnet.enchanting2.acquisition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wulfnet.enchanting2.config.AcquisitionSettings;
import com.wulfnet.enchanting2.config.EnchantmentOverrides;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class AcquisitionRulesTest {
	private static final String ID = "minecraft:mending";

	private static AcquisitionRules rules(AcquisitionSettings settings) {
		return new AcquisitionRules(new EnchantmentOverrides(Map.of(ID, new EnchantmentOverrides.Entry(settings))));
	}

	private static AcquisitionSettings flags(Optional<Boolean> enabled, Optional<Boolean> table, Optional<Boolean> loot,
			Optional<Boolean> trade) {
		return new AcquisitionSettings(enabled, table, loot, trade, OptionalInt.empty(), OptionalInt.empty(),
				Optional.empty());
	}

	@Test
	void noEntryAllowsEverySourceWithTheRegistryWeight() {
		AcquisitionRules rules = new AcquisitionRules(EnchantmentOverrides.defaults());
		for (Source source : Source.values()) {
			assertTrue(rules.allows(ID, source), source.name());
		}
		assertEquals(2, rules.weight(ID, Source.LOOT, 2));
		assertEquals(7, rules.weight(ID, Source.TRADE, 7));
		assertTrue(rules.treasure(ID, true));
		assertFalse(rules.treasure(ID, false));
	}

	@Test
	void anEntryWithoutAcquisitionKeysChangesNothing() {
		AcquisitionRules rules = new AcquisitionRules(new EnchantmentOverrides(Map.of(ID,
				new EnchantmentOverrides.Entry(OptionalInt.of(3)))));
		for (Source source : Source.values()) {
			assertTrue(rules.allows(ID, source), source.name());
		}
	}

	@Test
	void disabledClosesEverySourceEvenWhenItsFlagSaysTrue() {
		Optional<Boolean> yes = Optional.of(true);
		AcquisitionRules rules = rules(flags(Optional.of(false), yes, yes, yes));
		for (Source source : Source.values()) {
			assertFalse(rules.allows(ID, source), source.name());
		}
	}

	@Test
	void eachFlagClosesOnlyItsOwnSource() {
		Optional<Boolean> no = Optional.of(false);
		Optional<Boolean> unset = Optional.empty();
		AcquisitionRules table = rules(flags(unset, no, unset, unset));
		AcquisitionRules loot = rules(flags(unset, unset, no, unset));
		AcquisitionRules trade = rules(flags(unset, unset, unset, no));
		assertEquals(Map.of(Source.TABLE, false, Source.LOOT, true, Source.TRADE, true), allowed(table));
		assertEquals(Map.of(Source.TABLE, true, Source.LOOT, false, Source.TRADE, true), allowed(loot));
		assertEquals(Map.of(Source.TABLE, true, Source.LOOT, true, Source.TRADE, false), allowed(trade));
	}

	@Test
	void flagsApplyOnlyToTheirOwnId() {
		AcquisitionRules rules = rules(flags(Optional.of(false), Optional.empty(), Optional.empty(), Optional.empty()));
		assertTrue(rules.allows("minecraft:sharpness", Source.LOOT));
	}

	@Test
	void weightOverridesAreSeparatePerSource() {
		AcquisitionRules rules = rules(new AcquisitionSettings(Optional.empty(), Optional.empty(), Optional.empty(),
				Optional.empty(), OptionalInt.of(40), OptionalInt.empty(), Optional.empty()));
		assertEquals(40, rules.weight(ID, Source.LOOT, 2));
		assertEquals(2, rules.weight(ID, Source.TRADE, 2));
	}

	@Test
	void theTableHasNoWeight() {
		AcquisitionRules rules = new AcquisitionRules(EnchantmentOverrides.defaults());
		assertThrows(IllegalArgumentException.class, () -> rules.weight(ID, Source.TABLE, 2));
	}

	@Test
	void treasureOverrideBeatsTheTagBothWays() {
		assertFalse(rules(treasure(false)).treasure(ID, true));
		assertTrue(rules(treasure(true)).treasure(ID, false));
	}

	private static AcquisitionSettings treasure(boolean value) {
		return new AcquisitionSettings(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
				OptionalInt.empty(), OptionalInt.empty(), Optional.of(value));
	}

	private static Map<Source, Boolean> allowed(AcquisitionRules rules) {
		return Map.of(Source.TABLE, rules.allows(ID, Source.TABLE), Source.LOOT, rules.allows(ID, Source.LOOT),
				Source.TRADE, rules.allows(ID, Source.TRADE));
	}
}
