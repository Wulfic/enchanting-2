package com.wulfnet.enchanting2.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The layout's numbers have to line up with vanilla's texture and with each other (TODO 10l). */
class TableWindowTest {
	/** Vanilla's offer box: three 19-pixel rows from y 14, its frame's bottom highlight at y 71. */
	private static final int VANILLA_BOX_BOTTOM = 14 + 3 * 19;

	@Test
	void theListIsVanillasOfferBoxStretchedByTheExtraHeight() {
		assertEquals(108, TableWindow.LIST_WIDTH, "vanilla's offer width");
		assertEquals(VANILLA_BOX_BOTTOM + TableWindow.EXTRA, TableWindow.LIST_Y + TableWindow.LIST_HEIGHT,
				"the box grows by exactly the window's extra height");
		assertEquals(0, TableWindow.LIST_HEIGHT % TableWindow.LINE, "whole lines");
	}

	@Test
	void rowsGapAndScrollBarFillTheInterior() {
		assertEquals(TableWindow.LIST_X + TableWindow.LIST_WIDTH, TableWindow.SCROLLBAR_X + TableWindow.SCROLLBAR_WIDTH,
				"the scroll bar ends at the interior's right edge");
		assertTrue(TableWindow.INCREASE_RIGHT < TableWindow.ROW_WIDTH, "the stepper fits in a row");
		assertTrue(TableWindow.DECREASE_LEFT < TableWindow.LEVEL_LEFT && TableWindow.LEVEL_LEFT < TableWindow.INCREASE_LEFT,
				"arrows and label in order");
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

	/** 1080p at GUI scale 4 is 270 pixels tall: the window still fits. */
	@Test
	void theWindowFitsAt1080pGuiScaleFour() {
		assertTrue(TableWindow.HEIGHT <= 1080 / 4);
	}

	@Test
	void theBandRowIsInTheGreyStripUnderTheBox() {
		assertTrue(TableWindow.BAND_ROW > VANILLA_BOX_BOTTOM && TableWindow.BAND_ROW < 83,
				"between the box's frame and the inventory's first slot");
		assertTrue(TableWindow.SPLIT_ROW > VANILLA_BOX_BOTTOM, "the box's frame stays in the top piece");
	}
}
