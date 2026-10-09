package com.wulfnet.enchanting2.acquisition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wulfnet.enchanting2.acquisition.EnchantmentFlagsDraft.Flag;
import com.wulfnet.enchanting2.config.AcquisitionSettings;
import com.wulfnet.enchanting2.config.EnchantmentOverrides;
import com.wulfnet.enchanting2.cost.UpgradeMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EnchantmentFlagsDraftTest {
	private static final String MENDING = "minecraft:mending";
	private static final String SHARPNESS = "minecraft:sharpness";
	private static final String UNLOADED = "othermod:zap";

	/** Mending has costs and a weight but no flags; the unloaded mod's entry has an explicit loot: false. */
	private static final EnchantmentOverrides FILE = new EnchantmentOverrides(Map.of(
			MENDING, new EnchantmentOverrides.Entry(OptionalInt.of(1), OptionalInt.of(2), OptionalInt.of(0),
					Optional.of(UpgradeMode.FIXED), OptionalInt.of(2), OptionalInt.empty(),
					new AcquisitionSettings(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
							OptionalInt.empty(), OptionalInt.of(9), Optional.of(true))),
			UNLOADED, new EnchantmentOverrides.Entry(new AcquisitionSettings(Optional.empty(), Optional.of(true),
					Optional.of(false), Optional.empty(), OptionalInt.empty(), OptionalInt.empty(),
					Optional.empty()))));

	private static EnchantmentFlagsDraft draft() {
		return new EnchantmentFlagsDraft(FILE, Set.of(MENDING, SHARPNESS));
	}

	@Test
	void listsKnownAndFileIdsInIdOrderWithTheFilesValues() {
		EnchantmentFlagsDraft draft = draft();
		assertEquals(List.of(MENDING, SHARPNESS, UNLOADED), draft.ids());
		assertFalse(draft.get(UNLOADED, Flag.LOOT));
		assertTrue(draft.get(UNLOADED, Flag.TABLE));
		for (Flag flag : Flag.values()) {
			assertTrue(draft.get(SHARPNESS, flag), flag.name());
		}
	}

	@Test
	void noChangeGivesBackAnEqualFile() {
		assertEquals(FILE, draft().apply());
	}

	@Test
	void oneToggleWritesOneKeyAndAddsAnEntryOnlyWhereNeeded() {
		EnchantmentFlagsDraft draft = draft();
		draft.set(SHARPNESS, Flag.TRADE, false);
		EnchantmentOverrides applied = draft.apply();
		assertEquals(Set.of(MENDING, SHARPNESS, UNLOADED), applied.entries().keySet());
		assertEquals(new EnchantmentOverrides.Entry(new AcquisitionSettings(Optional.empty(), Optional.empty(),
				Optional.empty(), Optional.of(false), OptionalInt.empty(), OptionalInt.empty(), Optional.empty())),
				applied.entries().get(SHARPNESS));
		assertSame(FILE.entries().get(MENDING), applied.entries().get(MENDING), "untouched entry kept as is");
	}

	@Test
	void aToggleKeepsEveryOtherFieldOfTheEntry() {
		EnchantmentFlagsDraft draft = draft();
		draft.set(MENDING, Flag.ENABLED, false);
		EnchantmentOverrides.Entry before = FILE.entries().get(MENDING);
		EnchantmentOverrides.Entry after = draft.apply().entries().get(MENDING);
		assertEquals(before.withAcquisition(new AcquisitionSettings(Optional.of(false), Optional.empty(),
				Optional.empty(), Optional.empty(), OptionalInt.empty(), OptionalInt.of(9), Optional.of(true))), after);
		assertEquals(before.baseCost(), after.baseCost());
		assertEquals(before.upgradeMode(), after.upgradeMode());
	}

	@Test
	void togglingBackToTheDefaultLeavesAnAbsentKeyAbsent() {
		EnchantmentFlagsDraft draft = draft();
		draft.set(SHARPNESS, Flag.TABLE, false);
		draft.set(SHARPNESS, Flag.TABLE, true);
		assertEquals(FILE, draft.apply());
		assertFalse(draft.apply().entries().containsKey(SHARPNESS));
	}

	@Test
	void anExplicitKeyStaysExplicit() {
		EnchantmentFlagsDraft draft = draft();
		draft.set(UNLOADED, Flag.LOOT, true);
		draft.set(UNLOADED, Flag.TABLE, true);
		AcquisitionSettings after = draft.apply().entries().get(UNLOADED).acquisition();
		assertEquals(Optional.of(true), after.loot(), "explicit false turned to an explicit true");
		assertEquals(Optional.of(true), after.table(), "an explicit true that didn't change stays");
	}

	@Test
	void anUnlistedIdIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> draft().set("minecraft:fortune", Flag.LOOT, false));
	}
}
