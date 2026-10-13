package com.wulfnet.enchanting2.screen;

import static com.wulfnet.enchanting2.screen.SidePanelLayout.GAP;
import static com.wulfnet.enchanting2.screen.SidePanelLayout.MARGIN;
import static com.wulfnet.enchanting2.screen.SidePanelLayout.PANEL_WIDTH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wulfnet.enchanting2.screen.SidePanelLayout.Placement;
import org.junit.jupiter.api.Test;

/**
 * Stage 6D: panel left, else right, else the window shifts (owner); hidden → vanilla's centred window. Run with the
 * anvil's tab (14) and with none (the grindstone, Stage 6bC).
 */
class SidePanelLayoutTest {
	/** Vanilla's anvil and grindstone windows. */
	private static final int IMAGE = 176;
	/** The anvil's tab, {@code AnvilPanelTab.WIDTH} (client code, which JUnit can't load). */
	private static final int TAB_WIDTH = 14;

	private static int centered(int screen) {
		return (screen - IMAGE) / 2;
	}

	@Test
	void commonScreensPlaceThePanelLeft() {
		// 1920x1080 at GUI scale 4, and 2560x1440 at 4.
		for (int screen : new int[] {480, 640}) {
			SidePanelLayout layout = SidePanelLayout.compute(screen, IMAGE, TAB_WIDTH, true);
			assertEquals(Placement.LEFT, layout.placement(), "width " + screen);
			assertEquals(centered(screen), layout.leftPos());
			assertEquals(PANEL_WIDTH, layout.panelWidth());
		}
	}

	@Test
	void narrowScreenShiftsTheWindowOnlyAsFarAsNeeded() {
		// 1280x720 at GUI scale 3: neither side has room for the full panel.
		SidePanelLayout layout = SidePanelLayout.compute(427, IMAGE, TAB_WIDTH, true);
		assertEquals(Placement.SHIFTED, layout.placement());
		assertEquals(MARGIN, layout.panelX(), "panel against the left margin");
		assertEquals(PANEL_WIDTH, layout.panelWidth());
		assertEquals(MARGIN + PANEL_WIDTH + GAP + TAB_WIDTH, layout.leftPos());
	}

	@Test
	void minimumScreenNarrowsTheShiftedPanel() {
		SidePanelLayout layout = SidePanelLayout.compute(320, IMAGE, TAB_WIDTH, true);
		assertEquals(Placement.SHIFTED, layout.placement());
		assertEquals(320 - MARGIN - IMAGE, layout.leftPos(), "window against the right margin");
		assertEquals(124, layout.panelWidth());
	}

	@Test
	void hiddenPanelLeavesTheWindowCentred() {
		for (int screen = 200; screen <= 2000; screen++) {
			SidePanelLayout layout = SidePanelLayout.compute(screen, IMAGE, TAB_WIDTH, false);
			assertEquals(Placement.HIDDEN, layout.placement());
			assertEquals(centered(screen), layout.leftPos());
			assertEquals(layout.leftPos() - TAB_WIDTH, layout.tabX());
		}
	}

	/** Every width: on screen, no overlaps, and the window moves only when neither side fits. */
	@Test
	void everyWidthKeepsPanelTabAndWindowApartAndOnScreen() {
		for (int screen = 320; screen <= 2000; screen++) {
			SidePanelLayout layout = SidePanelLayout.compute(screen, IMAGE, TAB_WIDTH, true);
			String at = "width " + screen + ": " + layout;
			assertTrue(layout.panelShown(), at);
			assertEquals(layout.leftPos() - TAB_WIDTH, layout.tabX(), at);
			assertTrue(layout.panelX() >= MARGIN, at);
			assertTrue(layout.leftPos() + IMAGE <= screen, at);
			assertTrue(layout.panelX() + layout.panelWidth() <= screen - MARGIN, at);
			if (layout.placement() == Placement.RIGHT) {
				assertTrue(layout.panelX() >= layout.leftPos() + IMAGE + GAP, at);
			} else {
				assertTrue(layout.panelX() + layout.panelWidth() + GAP <= layout.tabX(), at);
			}
			boolean leftFits = centered(screen) >= MARGIN + PANEL_WIDTH + GAP + TAB_WIDTH;
			boolean rightFits = centered(screen) + IMAGE + GAP + PANEL_WIDTH <= screen - MARGIN;
			assertEquals(leftFits || rightFits, layout.leftPos() == centered(screen), at);
			assertEquals(leftFits, layout.placement() == Placement.LEFT, at);
		}
	}

	@Test
	void screenBelowMinecraftsMinimumHidesThePanelRatherThanCrowdIt() {
		SidePanelLayout layout = SidePanelLayout.compute(250, IMAGE, TAB_WIDTH, true);
		assertEquals(Placement.HIDDEN, layout.placement());
		assertEquals(centered(250), layout.leftPos());
	}

	/** The grindstone: no tab, so the panel sits a gap from the window and the shift is the tab's width smaller. */
	@Test
	void noTabPutsThePanelAGapFromTheWindow() {
		for (int screen = 320; screen <= 2000; screen++) {
			SidePanelLayout layout = SidePanelLayout.compute(screen, IMAGE, 0, true);
			String at = "width " + screen + ": " + layout;
			assertTrue(layout.panelShown(), at);
			assertEquals(layout.leftPos(), layout.tabX(), at);
			assertTrue(layout.panelX() >= MARGIN && layout.leftPos() + IMAGE <= screen, at);
			if (layout.placement() == Placement.RIGHT) {
				assertTrue(layout.panelX() >= layout.leftPos() + IMAGE + GAP, at);
			} else {
				assertTrue(layout.panelX() + layout.panelWidth() + GAP <= layout.leftPos(), at);
			}
		}
		SidePanelLayout shifted = SidePanelLayout.compute(427, IMAGE, 0, true);
		assertEquals(Placement.SHIFTED, shifted.placement());
		assertEquals(MARGIN + PANEL_WIDTH + GAP, shifted.leftPos());
	}

	/** A wider panel (the table's was 160 before it moved into its window) follows the same rules; only a shifted one can be narrower. */
	@Test
	void aWiderPanelFollowsTheSameRules() {
		int wide = 160;
		for (int screen = 320; screen <= 2000; screen++) {
			SidePanelLayout layout = SidePanelLayout.compute(screen, IMAGE, 0, true, wide);
			String at = "width " + screen + ": " + layout;
			assertTrue(layout.panelShown(), at);
			assertTrue(layout.panelX() >= MARGIN && layout.leftPos() + IMAGE <= screen, at);
			assertTrue(layout.panelX() + layout.panelWidth() <= screen - MARGIN, at);
			assertTrue(layout.placement() == Placement.SHIFTED ? layout.panelWidth() <= wide
					: layout.panelWidth() == wide, at);
			assertTrue(layout.panelWidth() >= SidePanelLayout.MIN_PANEL_WIDTH, at);
			boolean leftFits = centered(screen) >= MARGIN + wide + GAP;
			assertEquals(leftFits, layout.placement() == Placement.LEFT, at);
		}
		// 1920x1080 at GUI scale 4: shifted 12 pixels, full width.
		SidePanelLayout scale4 = SidePanelLayout.compute(480, IMAGE, 0, true, wide);
		assertEquals(Placement.SHIFTED, scale4.placement());
		assertEquals(centered(480) + 12, scale4.leftPos());
		assertEquals(wide, scale4.panelWidth());
		assertThrows(IllegalArgumentException.class, () -> SidePanelLayout.compute(480, IMAGE, 0, true, 95));
	}
}
