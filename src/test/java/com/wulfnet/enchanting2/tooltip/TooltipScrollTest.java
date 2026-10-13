package com.wulfnet.enchanting2.tooltip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Owner, 2026-10-13: a tooltip taller than the screen starts at its top and the wheel scrolls it. */
class TooltipScrollTest {
	private static final int SCREEN = 240;
	/** 40 lines: 400 tall, so 400 + 7 - 240 = 167 pixels don't fit. */
	private static final int TALL = TooltipFit.height(40);
	private static final int OVERFLOW = TALL + TooltipFit.MARGIN + 3 - SCREEN;
	private static final long MS = 1_000_000L;

	@Test
	void aTooltipThatFitsKeepsVanillasPlaceAndTheWheelIsLeftAlone() {
		TooltipScroll scroll = new TooltipScroll();
		assertEquals(57, scroll.y(57, 100, TooltipFit.height(10), SCREEN, 0));
		assertFalse(scroll.scroll(-1, 0), "nothing to scroll");
	}

	@Test
	void aTallOneStartsAtItsTopAndScrollsWithinItsLength() {
		TooltipScroll scroll = new TooltipScroll();
		int vanilla = SCREEN - TALL - 3;
		assertEquals(TooltipFit.MARGIN, scroll.y(vanilla, 200, TALL, SCREEN, 0), "the item's name is on screen");
		assertTrue(scroll.scroll(-1, 10 * MS), "wheel down is taken");
		assertEquals(TooltipFit.MARGIN - TooltipScroll.STEP, scroll.y(vanilla, 200, TALL, SCREEN, 20 * MS));
		for (int i = 0; i < 50; i++) {
			scroll.scroll(-1, 20 * MS);
		}
		assertEquals(TooltipFit.MARGIN - OVERFLOW, scroll.y(vanilla, 200, TALL, SCREEN, 30 * MS),
				"stops with the last line on screen (vanilla's own place)");
		assertEquals(vanilla, TooltipFit.MARGIN - OVERFLOW);
		for (int i = 0; i < 50; i++) {
			scroll.scroll(1, 30 * MS);
		}
		assertEquals(TooltipFit.MARGIN, scroll.y(vanilla, 200, TALL, SCREEN, 40 * MS), "and back to the top");
	}

	@Test
	void anotherTooltipOrAPauseStartsAtTheTopAgain() {
		TooltipScroll scroll = new TooltipScroll();
		scroll.y(0, 200, TALL, SCREEN, 0);
		scroll.scroll(-2, 0);
		assertEquals(TooltipFit.MARGIN, scroll.y(0, 210, TALL, SCREEN, MS), "a different size is another tooltip");
		scroll.scroll(-2, MS);
		long later = MS + TooltipScroll.STALE_NANOS + 1;
		assertFalse(scroll.scroll(-1, later), "not shown lately: the wheel is the screen's again");
		assertEquals(TooltipFit.MARGIN, scroll.y(0, 210, TALL, SCREEN, later), "hovered again: from the top");
	}
}
