package com.wulfnet.enchanting2.enchantment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wulfnet.enchanting2.enchantment.MaxLevels.Resolution;
import com.wulfnet.enchanting2.enchantment.MaxLevels.Why;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class MaxLevelsTest {
	/** DESIGN §8, typed out again by hand: {vanilla, v1}. */
	private static final Map<String, int[]> DESIGN_8 = Map.ofEntries(
			Map.entry("minecraft:protection", new int[] {4, 5}),
			Map.entry("minecraft:fire_protection", new int[] {4, 5}),
			Map.entry("minecraft:blast_protection", new int[] {4, 5}),
			Map.entry("minecraft:projectile_protection", new int[] {4, 5}),
			Map.entry("minecraft:feather_falling", new int[] {4, 5}),
			Map.entry("minecraft:respiration", new int[] {3, 5}),
			Map.entry("minecraft:thorns", new int[] {3, 5}),
			Map.entry("minecraft:soul_speed", new int[] {3, 5}),
			Map.entry("minecraft:swift_sneak", new int[] {3, 5}),
			Map.entry("minecraft:frost_walker", new int[] {2, 5}),
			Map.entry("minecraft:knockback", new int[] {2, 5}),
			Map.entry("minecraft:fire_aspect", new int[] {2, 5}),
			Map.entry("minecraft:punch", new int[] {2, 5}),
			Map.entry("minecraft:looting", new int[] {3, 5}),
			Map.entry("minecraft:fortune", new int[] {3, 5}),
			Map.entry("minecraft:luck_of_the_sea", new int[] {3, 5}),
			Map.entry("minecraft:unbreaking", new int[] {3, 5}),
			Map.entry("minecraft:sweeping_edge", new int[] {3, 5}),
			Map.entry("minecraft:loyalty", new int[] {3, 5}),
			Map.entry("minecraft:wind_burst", new int[] {3, 5}),
			Map.entry("minecraft:piercing", new int[] {4, 5}),
			Map.entry("minecraft:breach", new int[] {4, 5}),
			Map.entry("minecraft:lunge", new int[] {3, 5}),
			Map.entry("minecraft:quick_charge", new int[] {3, 4}),
			Map.entry("minecraft:lure", new int[] {3, 5}),
			Map.entry("minecraft:riptide", new int[] {3, 5}),
			// Unchanged rows: resolved through the "not in the table" path, never touched.
			Map.entry("minecraft:depth_strider", new int[] {3, 3}),
			Map.entry("minecraft:sharpness", new int[] {5, 5}),
			Map.entry("minecraft:efficiency", new int[] {5, 5}),
			Map.entry("minecraft:silk_touch", new int[] {1, 1}),
			Map.entry("minecraft:mending", new int[] {1, 1}),
			Map.entry("minecraft:multishot", new int[] {1, 1}));

	@Test
	void everyDesignRowResolvesToItsV1Max() {
		DESIGN_8.forEach((id, levels) -> {
			Resolution r = MaxLevels.resolve(id, levels[0], OptionalInt.empty());
			assertEquals(levels[1], r.max(), id);
			assertEquals(levels[0] == levels[1] ? Why.UNLISTED : Why.TABLE, r.why(), id);
		});
	}

	@Test
	void tableHasNoRowTheDesignDoesNotList() {
		MaxLevels.TABLE.forEach((id, row) -> {
			int[] design = DESIGN_8.get(id);
			assertTrue(design != null, "not in DESIGN §8: " + id);
			assertEquals(design[0], row.vanillaMax(), id);
			assertEquals(design[1], row.v1Max(), id);
		});
	}

	@Test
	void overrideWinsInBothDirections() {
		assertEquals(new Resolution(2, Why.OVERRIDE), MaxLevels.resolve("minecraft:fortune", 3, OptionalInt.of(2)));
		assertEquals(new Resolution(10, Why.OVERRIDE), MaxLevels.resolve("minecraft:fortune", 3, OptionalInt.of(10)));
		assertEquals(new Resolution(4, Why.OVERRIDE),
				MaxLevels.resolve("minecraft:depth_strider", 3, OptionalInt.of(4)));
	}

	@Test
	void overrideWinsOverADatapackChange() {
		assertEquals(new Resolution(2, Why.OVERRIDE), MaxLevels.resolve("minecraft:fortune", 7, OptionalInt.of(2)));
	}

	@Test
	void aDatapackThatChangedTheMaxIsRespected() {
		assertEquals(new Resolution(4, Why.DATAPACK_CHANGED), MaxLevels.resolve("minecraft:fortune", 4, OptionalInt.empty()));
		assertEquals(new Resolution(1, Why.DATAPACK_CHANGED),
				MaxLevels.resolve("minecraft:protection", 1, OptionalInt.empty()));
	}

	@Test
	void anOverridePastABreakLevelIsClampedToTheSafeMax() {
		// Break points (arithmetic in plan step 4D.1): Lure VI never bites, Riptide VIII can trip moved-too-quickly,
		// Quick Charge V has zero charge time.
		assertEquals(new Resolution(5, Why.OVERRIDE_CLAMPED), MaxLevels.resolve("minecraft:lure", 3, OptionalInt.of(6)));
		assertEquals(new Resolution(7, Why.OVERRIDE_CLAMPED),
				MaxLevels.resolve("minecraft:riptide", 3, OptionalInt.of(255)));
		assertEquals(new Resolution(4, Why.OVERRIDE_CLAMPED),
				MaxLevels.resolve("minecraft:quick_charge", 3, OptionalInt.of(5)));
	}

	@Test
	void anOverrideAtOrBelowTheSafeMaxIsKept() {
		assertEquals(new Resolution(5, Why.OVERRIDE), MaxLevels.resolve("minecraft:lure", 3, OptionalInt.of(5)));
		assertEquals(new Resolution(7, Why.OVERRIDE), MaxLevels.resolve("minecraft:riptide", 3, OptionalInt.of(7)));
		assertEquals(new Resolution(1, Why.OVERRIDE), MaxLevels.resolve("minecraft:quick_charge", 3, OptionalInt.of(1)));
	}

	@Test
	void aDatapackMaxPastTheSafeMaxIsNotClamped() {
		// The datapack may have changed the effect too, so its number is respected (decisions.md 2026-10-07).
		assertEquals(new Resolution(9, Why.DATAPACK_CHANGED), MaxLevels.resolve("minecraft:quick_charge", 9,
				OptionalInt.empty()));
	}

	@Test
	void everyV1MaxIsWithinItsSafeMax() {
		LevelCeilings.hardCeilings().forEach((id, cap) -> {
			Resolution r = MaxLevels.resolve(id, DESIGN_8.get(id)[0], OptionalInt.empty());
			assertTrue(r.max() <= cap, id + " v1 max " + r.max() + " is above its safe max " + cap);
		});
	}

	@Test
	void unknownIdsKeepTheirLoadedMaxUnlessOverridden() {
		assertEquals(new Resolution(7, Why.UNLISTED), MaxLevels.resolve("othermod:zap", 7, OptionalInt.empty()));
		assertEquals(new Resolution(3, Why.OVERRIDE), MaxLevels.resolve("othermod:zap", 7, OptionalInt.of(3)));
	}
}
