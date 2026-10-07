package com.wulfnet.enchanting2.enchantment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConflictFamilyTest {
	private static final Set<ConflictFamily> ALL = EnumSet.allOf(ConflictFamily.class);

	@Test
	void familiesAreDisjoint() {
		Set<String> seen = new HashSet<>();
		for (ConflictFamily family : ConflictFamily.values()) {
			for (String id : family.members()) {
				assertTrue(seen.add(id), id + " is in two families");
			}
		}
	}

	@Test
	void damageFamilyHasAllSixVanillaMembers() {
		assertEquals(Set.of("minecraft:sharpness", "minecraft:smite", "minecraft:bane_of_arthropods",
				"minecraft:impaling", "minecraft:density", "minecraft:breach"), ConflictFamily.DAMAGE.members());
	}

	@Test
	void relaxesEveryPairInsideAnAllowedFamilyBothWays() {
		for (ConflictFamily family : ConflictFamily.values()) {
			for (String a : family.members()) {
				for (String b : family.members()) {
					assertTrue(ConflictFamily.relaxes(a, b, Set.of(family)), a + " + " + b);
				}
			}
		}
	}

	@Test
	void neverRelaxesAcrossFamilies() {
		assertFalse(ConflictFamily.relaxes("minecraft:silk_touch", "minecraft:mending", ALL));
		assertFalse(ConflictFamily.relaxes("minecraft:sharpness", "minecraft:protection", ALL));
		assertFalse(ConflictFamily.relaxes("minecraft:riptide", "minecraft:multishot", ALL));
	}

	@Test
	void familyTurnedOffIsNotRelaxedButOthersStillAre() {
		Set<ConflictFamily> allowed = EnumSet.complementOf(EnumSet.of(ConflictFamily.SILK_TOUCH_FORTUNE));
		assertFalse(ConflictFamily.relaxes("minecraft:silk_touch", "minecraft:fortune", allowed));
		assertFalse(ConflictFamily.relaxes("minecraft:fortune", "minecraft:silk_touch", allowed));
		assertTrue(ConflictFamily.relaxes("minecraft:mending", "minecraft:infinity", allowed));
		assertFalse(ConflictFamily.relaxes("minecraft:mending", "minecraft:infinity", Set.of()));
	}

	@Test
	void thirdPartyIdsAreNeverRelaxed() {
		for (String other : List.of("othermod:sharpness_plus", "sharpness", "minecraft:unbreaking", "")) {
			assertFalse(ConflictFamily.relaxes("minecraft:sharpness", other, ALL), other);
			assertFalse(ConflictFamily.relaxes(other, "minecraft:sharpness", ALL), other);
		}
	}

	@Test
	void keysAreSnakeCase() {
		assertEquals("silk_touch_fortune", ConflictFamily.SILK_TOUCH_FORTUNE.key());
		assertEquals("depth_strider_frost_walker", ConflictFamily.DEPTH_STRIDER_FROST_WALKER.key());
	}
}
