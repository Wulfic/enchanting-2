package com.wulfnet.enchanting2.anvil;

import static com.wulfnet.enchanting2.anvil.AnvilPanelLayout.GAP;
import static com.wulfnet.enchanting2.anvil.AnvilPanelLayout.MARGIN;
import static com.wulfnet.enchanting2.anvil.AnvilPanelLayout.PANEL_WIDTH;
import static com.wulfnet.enchanting2.anvil.AnvilPanelLayout.TAB_WIDTH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wulfnet.enchanting2.anvil.AnvilPanelLayout.Placement;
import org.junit.jupiter.api.Test;

/** Stage 6D: panel left, else right, else the window shifts (owner); hidden → vanilla's centred window. */
class AnvilPanelLayoutTest {
	/** Vanilla's anvil window. */
	private static final int IMAGE = 176;

	private static int centered(int screen) {
		return (screen - IMAGE) / 2;
	}

	@Test
	void commonScreensPlaceThePanelLeft() {
		// 1920x1080 at GUI scale 4, and 2560x1440 at 4.
		for (int screen : new int[] {480, 640}) {
			AnvilPanelLayout layout = AnvilPanelLayout.compute(screen, IMAGE, true);
			assertEquals(Placement.LEFT, layout.placement(), "width " + screen);
			assertEquals(centered(screen), layout.leftPos());
			assertEquals(PANEL_WIDTH, layout.panelWidth());
		}
	}

	@Test
	void narrowScreenShiftsTheWindowOnlyAsFarAsNeeded() {
		// 1280x720 at GUI scale 3: neither side has room for the full panel.
		AnvilPanelLayout layout = AnvilPanelLayout.compute(427, IMAGE, true);
		assertEquals(Placement.SHIFTED, layout.placement());
		assertEquals(MARGIN, layout.panelX(), "panel against the left margin");
		assertEquals(PANEL_WIDTH, layout.panelWidth());
		assertEquals(MARGIN + PANEL_WIDTH + GAP + TAB_WIDTH, layout.leftPos());
	}

	@Test
	void minimumScreenNarrowsTheShiftedPanel() {
		AnvilPanelLayout layout = AnvilPanelLayout.compute(320, IMAGE, true);
		assertEquals(Placement.SHIFTED, layout.placement());
		assertEquals(320 - MARGIN - IMAGE, layout.leftPos(), "window against the right margin");
		assertEquals(124, layout.panelWidth());
	}

	@Test
	void hiddenPanelLeavesTheWindowCentred() {
		for (int screen = 200; screen <= 2000; screen++) {
			AnvilPanelLayout layout = AnvilPanelLayout.compute(screen, IMAGE, false);
			assertEquals(Placement.HIDDEN, layout.placement());
			assertEquals(centered(screen), layout.leftPos());
			assertEquals(layout.leftPos() - TAB_WIDTH, layout.tabX());
		}
	}

	/** Every width: on screen, no overlaps, and the window moves only when neither side fits. */
	@Test
	void everyWidthKeepsPanelTabAndWindowApartAndOnScreen() {
		for (int screen = 320; screen <= 2000; screen++) {
			AnvilPanelLayout layout = AnvilPanelLayout.compute(screen, IMAGE, true);
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
		AnvilPanelLayout layout = AnvilPanelLayout.compute(250, IMAGE, true);
		assertEquals(Placement.HIDDEN, layout.placement());
		assertEquals(centered(250), layout.leftPos());
	}
}
