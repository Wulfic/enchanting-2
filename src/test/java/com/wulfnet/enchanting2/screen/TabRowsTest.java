package com.wulfnet.enchanting2.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The config screen's tab rows (Phase 11d, owner 2026-10-13: two rows instead of a scrolling one). */
class TabRowsTest {
	/** The eleven categories' widths at 1080p, GUI scale 3 (title + 20): wider than the 584 between the margins. */
	private static final List<Integer> CATEGORIES = List.of(78, 92, 45, 50, 45, 45, 59, 128, 44, 82, 57);

	@Test
	void tabsThatFitStayInOneRowFillingIt() {
		TabRows rows = TabRows.of(List.of(60, 80, 40), 640);
		assertEquals(1, rows.rows());
		assertEquals(TabRows.ROW_HEIGHT, rows.height());
		assertRowsFill(rows, 640);
	}

	@Test
	void theConfigScreensCategoriesTakeTwoEvenRowsAt1080p() {
		TabRows rows = TabRows.of(CATEGORIES, 640);
		assertEquals(2, rows.rows());
		assertEquals(2 * TabRows.ROW_HEIGHT, rows.height());
		assertRowsFill(rows, 640);
		long top = rows.cells().stream().filter(cell -> cell.y() == 0).count();
		long natural = 0;
		for (int i = 0; i < top; i++) {
			natural += CATEGORIES.get(i);
		}
		long rest = CATEGORIES.stream().mapToInt(Integer::intValue).sum() - natural;
		// Evened out, not a full first row and a short second one.
		assertTrue(Math.abs(natural - rest) <= CATEGORIES.stream().mapToInt(Integer::intValue).max().orElseThrow(),
				"rows of " + natural + " and " + rest);
	}

	@Test
	void aNarrowScreenGetsAsManyRowsAsItNeedsAndNoTabLeavesIt() {
		TabRows rows = TabRows.of(CATEGORIES, 320);
		assertTrue(rows.rows() > 2, "rows: " + rows.rows());
		assertRowsFill(rows, 320);
	}

	@Test
	void aTitleWiderThanTheScreenGetsAWholeRow() {
		TabRows rows = TabRows.of(List.of(50, 900, 50), 300);
		assertEquals(3, rows.rows());
		assertEquals(new TabRows.Cell(TabRows.MARGIN, TabRows.ROW_HEIGHT, 300 - 2 * TabRows.MARGIN),
				rows.cells().get(1));
	}

	@Test
	void badInputIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> TabRows.of(List.of(), 640));
		assertThrows(IllegalArgumentException.class, () -> TabRows.of(List.of(40, 0), 640));
	}

	/** Tab order kept; each row starts at the margin, its tabs touch, and it ends at the other margin. */
	private static void assertRowsFill(TabRows rows, int screenWidth) {
		List<TabRows.Cell> cells = rows.cells();
		for (int i = 0; i < cells.size(); i++) {
			TabRows.Cell cell = cells.get(i);
			boolean firstInRow = i == 0 || cells.get(i - 1).y() != cell.y();
			boolean lastInRow = i == cells.size() - 1 || cells.get(i + 1).y() != cell.y();
			if (firstInRow) {
				assertEquals(TabRows.MARGIN, cell.x(), "row start: " + cells);
			} else {
				assertEquals(cells.get(i - 1).right(), cell.x(), "tabs touch: " + cells);
				assertEquals(cells.get(i - 1).y(), cell.y());
			}
			if (lastInRow) {
				assertEquals(screenWidth - TabRows.MARGIN, cell.right(), "row end: " + cells);
			}
			assertTrue(cell.y() % TabRows.ROW_HEIGHT == 0 && cell.y() < rows.height(), "row top: " + cell);
			assertTrue(i == 0 || cell.y() >= cells.get(i - 1).y(), "order kept: " + cells);
		}
	}
}
