package com.wulfnet.enchanting2.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.wulfnet.enchanting2.acquisition.EnchantmentFlagsDraft;
import com.wulfnet.enchanting2.config.EnchantmentNumbersDraft.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** Phase 10jB: the screen's max level and cost fields, written back without disturbing anything else. */
class EnchantmentNumbersDraftTest {
	private static final String SHARPNESS = "minecraft:sharpness";
	private static final String PIERCING = "minecraft:piercing";

	/** Max level 5, base cost 0, cost per level 1 for everything. */
	private static int defaults(String id, Field field) {
		return switch (field) {
			case MAX_LEVEL -> 5;
			case BASE_COST -> 0;
			case COST_PER_LEVEL -> 1;
		};
	}

	private static EnchantmentNumbersDraft draft(EnchantmentOverrides file) {
		return new EnchantmentNumbersDraft(file, List.of(SHARPNESS, PIERCING), EnchantmentNumbersDraftTest::defaults);
	}

	private static EnchantmentOverrides.Entry max(int level) {
		return new EnchantmentOverrides.Entry(OptionalInt.of(level));
	}

	@Test
	void showsTheFileValueOrTheDefault() {
		EnchantmentNumbersDraft draft = draft(new EnchantmentOverrides(Map.of(SHARPNESS, max(10))));
		assertEquals(10, draft.get(SHARPNESS, Field.MAX_LEVEL));
		assertEquals(5, draft.get(PIERCING, Field.MAX_LEVEL));
		assertEquals(1, draft.get(PIERCING, Field.COST_PER_LEVEL));
	}

	@Test
	void untouchedLeavesTheFileExactlyAsItWas() {
		// An override equal to the default stays written: the screen didn't change it.
		EnchantmentOverrides file = new EnchantmentOverrides(Map.of(SHARPNESS, max(5)));
		assertEquals(file, draft(file).apply(file));
		assertEquals(EnchantmentOverrides.defaults(), draft(EnchantmentOverrides.defaults()).apply(EnchantmentOverrides.defaults()));
	}

	@Test
	void aChangedFieldIsWrittenAndResettingClearsIt() {
		EnchantmentNumbersDraft draft = draft(EnchantmentOverrides.defaults());
		draft.set(PIERCING, Field.MAX_LEVEL, 100);
		draft.set(PIERCING, Field.BASE_COST, 3);
		EnchantmentOverrides.Entry written = draft.apply(EnchantmentOverrides.defaults()).entries().get(PIERCING);
		assertEquals(OptionalInt.of(100), written.maxLevelOverride());
		assertEquals(OptionalInt.of(3), written.baseCost());
		assertEquals(OptionalInt.empty(), written.costPerLevel());

		EnchantmentOverrides file = new EnchantmentOverrides(Map.of(SHARPNESS, max(10)));
		EnchantmentNumbersDraft reset = draft(file);
		reset.set(SHARPNESS, Field.MAX_LEVEL, 5);
		assertFalse(reset.apply(file).entries().containsKey(SHARPNESS), "an entry left empty is dropped");
	}

	@Test
	void otherKeysAndTheFlagsDraftSurvive() {
		EnchantmentOverrides.Entry full = new EnchantmentOverrides.Entry(OptionalInt.of(10), OptionalInt.empty(),
				OptionalInt.empty(), Optional.empty(), OptionalInt.of(4), OptionalInt.empty());
		EnchantmentOverrides file = new EnchantmentOverrides(Map.of(SHARPNESS, full));
		EnchantmentFlagsDraft flags = new EnchantmentFlagsDraft(file, List.of(SHARPNESS, PIERCING));
		flags.set(SHARPNESS, EnchantmentFlagsDraft.Flag.TRADE, false);
		EnchantmentNumbersDraft numbers = draft(file);
		numbers.set(SHARPNESS, Field.MAX_LEVEL, 5);
		EnchantmentOverrides.Entry after = numbers.apply(flags.apply()).entries().get(SHARPNESS);
		assertEquals(OptionalInt.empty(), after.maxLevelOverride(), "reset to the default");
		assertEquals(OptionalInt.of(4), after.otherLevelsSurcharge(), "a key the screen doesn't edit");
		assertEquals(Optional.of(false), after.acquisition().trade(), "the flags draft's change");
	}

	@Test
	void outOfRangeIsRefused() {
		EnchantmentNumbersDraft draft = draft(EnchantmentOverrides.defaults());
		assertThrows(IllegalArgumentException.class, () -> draft.set(PIERCING, Field.MAX_LEVEL, 0));
		assertThrows(IllegalArgumentException.class, () -> draft.set(PIERCING, Field.MAX_LEVEL, 256));
		assertThrows(IllegalArgumentException.class, () -> draft.set("minecraft:unlisted", Field.MAX_LEVEL, 3));
	}
}
