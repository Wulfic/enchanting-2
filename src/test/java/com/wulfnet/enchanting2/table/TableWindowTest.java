package com.wulfnet.enchanting2.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The layout's numbers have to line up with vanilla's texture and with each other (TODO 10l). */
class TableWindowTest {
	/** Vanilla's offer box: three 19-pixel rows from y 14, its frame's bottom highlight at y 71. */
	private static final int VANILLA_BOX_BOTTOM = 14 + 3 * 19;

	@Test
	void theListIsVanillasOfferBoxGrownByTheExtraSize() {
		assertEquals(108 + TableWindow.EXTRA_WIDTH, TableWindow.LIST_WIDTH, "vanilla's offer width, widened with the window");
		assertEquals(VANILLA_BOX_BOTTOM + TableWindow.EXTRA, TableWindow.LIST_Y + TableWindow.LIST_HEIGHT,
				"the box grows by exactly the window's extra height");
		assertEquals(0, TableWindow.LIST_HEIGHT % TableWindow.LINE, "whole lines");
	}

	@Test
	void rowsGapAndScrollBarFillTheInterior() {
		assertEquals(TableWindow.LIST_X + TableWindow.LIST_WIDTH, TableWindow.SCROLLBAR_X + TableWindow.SCROLLBAR_WIDTH,
				"the scroll bar ends at the interior's right edge");
		assertTrue(TableWindow.LIST_X + TableWindow.LIST_WIDTH + 1 <= TableWindow.WIDTH - 6,
				"the box's frame clears the window's right border");
	}

	/** Name, then {@code < V >}, then the cost, left to right with no overlap (owner, 2026-10-13). */
	@Test
	void aRowHoldsNameStepperAndCostSideBySide() {
		assertTrue(TableWindow.NAME_LEFT < TableWindow.NAME_RIGHT);
		assertTrue(TableWindow.NAME_RIGHT < TableWindow.DECREASE_LEFT);
		assertTrue(TableWindow.DECREASE_LEFT < TableWindow.LEVEL_LEFT && TableWindow.LEVEL_LEFT < TableWindow.INCREASE_LEFT
				&& TableWindow.INCREASE_LEFT < TableWindow.INCREASE_RIGHT);
		assertTrue(TableWindow.INCREASE_RIGHT <= TableWindow.COST_RIGHT - TableWindow.COST_WIDTH);
		assertTrue(TableWindow.COST_RIGHT < TableWindow.ROW_WIDTH);
		assertTrue(TableWindow.NAME_RIGHT - TableWindow.NAME_LEFT >= 95, "room for most names beside the stepper");
	}

	@Test
	void eachBackgroundBandCoversTheWholeWidthOnce() {
		for (List<TableWindow.Piece> band : List.of(TableWindow.TOP, TableWindow.BAND, TableWindow.BOTTOM)) {
			int x = 0;
			for (TableWindow.Piece piece : band) {
				assertEquals(x, piece.x(), "pieces are contiguous: " + band);
				assertTrue(piece.u() + piece.uWidth() <= TableWindow.VANILLA_WIDTH, "inside the texture: " + piece);
				x += piece.width();
			}
			assertEquals(TableWindow.WIDTH, x, "the band ends at the window's edge: " + band);
		}
	}

	/** The inventory grid is drawn unstretched and centred; its slots move by the same amount. */
	@Test
	void theInventoryGridIsCentred() {
		TableWindow.Piece grid = TableWindow.BOTTOM.stream().filter(p -> p.uWidth() == p.width() && p.width() > 100)
				.findFirst().orElseThrow();
		assertEquals(TableWindow.inventorySlotX(8) - 8, grid.x() - grid.u(), "slots move with the grid");
		assertEquals(grid.x(), TableWindow.WIDTH - (grid.x() + grid.width()), "the same grey on both sides");
		assertEquals(TableWindow.inventorySlotX(8), TableWindow.INVENTORY_LABEL_X);
	}

	@Test
	void theColumnSitsUnderTheSlotsAndBesideTheList() {
		int slotsBottom = 47 + 16;
		assertTrue(TableWindow.PRICE_Y > slotsBottom, "price under the item and lapis slots");
		assertTrue(TableWindow.PRICE_Y + TableWindow.STATUS_LINES * TableWindow.TEXT_LINE <= TableWindow.BUTTON_Y,
				"status lines end above the button");
		assertTrue(TableWindow.BUTTON_Y + TableWindow.BUTTON_HEIGHT <= TableWindow.LIST_Y + TableWindow.LIST_HEIGHT,
				"the button ends level with the list");
		assertTrue(TableWindow.COLUMN_X + TableWindow.COLUMN_WIDTH < TableWindow.LIST_X - 1, "the column clears the box");
		assertTrue(TableWindow.BUTTON_X + TableWindow.BUTTON_WIDTH <= TableWindow.COLUMN_X + TableWindow.COLUMN_WIDTH);
	}

	@Test
	void theInventoryMovesDownByTheExtraHeight() {
		assertEquals(84 + TableWindow.EXTRA, TableWindow.inventorySlotY(84));
		assertEquals(TableWindow.HEIGHT - 94, TableWindow.INVENTORY_LABEL_Y, "vanilla's imageHeight - 94");
		assertTrue(TableWindow.INVENTORY_LABEL_Y >= TableWindow.LIST_Y + TableWindow.LIST_HEIGHT,
				"the label is under the list");
	}

	/** 1080p at GUI scale 4 is 480 x 270: the window still fits. */
	@Test
	void theWindowFitsAt1080pGuiScaleFour() {
		assertTrue(TableWindow.HEIGHT <= 1080 / 4);
		assertTrue(TableWindow.WIDTH <= 1920 / 4);
	}

	@Test
	void theBandRowIsInTheGreyStripUnderTheBox() {
		assertTrue(TableWindow.BAND_ROW > VANILLA_BOX_BOTTOM && TableWindow.BAND_ROW < 83,
				"between the box's frame and the inventory's first slot");
		assertTrue(TableWindow.SPLIT_ROW > VANILLA_BOX_BOTTOM, "the box's frame stays in the top piece");
	}
}
