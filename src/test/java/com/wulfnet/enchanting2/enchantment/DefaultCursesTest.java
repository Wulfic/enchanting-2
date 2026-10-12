package com.wulfnet.enchanting2.enchantment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Phase 10e: which entries get the config screen's curse discount fields with no world open. */
class DefaultCursesTest {
	@Test
	void vanillaCursesComeFromTheGameJarsTag() {
		assertTrue(DefaultCurses.contains("minecraft:binding_curse"));
		assertTrue(DefaultCurses.contains("minecraft:vanishing_curse"));
	}

	@Test
	void nonCursesAreNot() {
		assertFalse(DefaultCurses.contains("minecraft:sharpness"));
		assertFalse(DefaultCurses.contains("enchanting2:vision"));
		assertFalse(DefaultCurses.contains("#minecraft:curse"));
	}
}
