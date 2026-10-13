package com.wulfnet.enchanting2.tooltip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Owner, 2026-10-13: a tool with many enchantments must not get a tooltip taller than the screen. */
class TooltipFitTest {
	@Test
	void heightIsVanillasTenPerLine() {
		assertEquals(8, TooltipFit.height(1), "one line: GuiGraphics starts its sum at -2");
		assertEquals(20, TooltipFit.height(2));
		assertEquals(100, TooltipFit.height(10));
	}

	/** Vanilla's positioner keeps 3 below; we keep {@link TooltipFit#MARGIN} above, as it does left of the tooltip. */
	@Test
	void fitsWhenTheMarginsAboveAndBelowStillShow() {
		int screen = 240;
		int most = (screen - TooltipFit.MARGIN - 3) / 10;
		assertTrue(TooltipFit.fits(most, screen), most + " lines fit in " + screen);
		assertFalse(TooltipFit.fits(most + 1, screen));
		assertEquals(0, TooltipFit.overflow(TooltipFit.height(most), screen));
		// 24 lines: 240 tall + 4 above + 3 below = 247, 7 more than the screen.
		assertEquals(7, TooltipFit.overflow(TooltipFit.height(most + 1), screen));
	}

	@Test
	void compactWrapIsWiderOnWideScreensWithinBounds() {
		assertEquals(TooltipFit.MIN_COMPACT_WIDTH, TooltipFit.compactWidth(320), "never narrower than the lists'");
		assertEquals(296, TooltipFit.compactWidth(640), "half the screen beside the cursor, less the offset and frame");
		assertEquals(TooltipFit.MAX_COMPACT_WIDTH, TooltipFit.compactWidth(1920), "long lines are hard to read");
	}
}
