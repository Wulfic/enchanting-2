package com.wulfnet.enchanting2.table;

/**
 * The enchanting table window's layout on a server running our table (owner 2026-10-13, layout B; TODO 10l): vanilla's
 * window made {@link #EXTRA} pixels taller, the book kept, the list in vanilla's offer box, and the price and Enchant
 * button under the item and lapis slots. Every coordinate is GUI pixels from the window's top-left. MC-free, so the
 * arithmetic that has to line up is unit-tested.
 */
public final class TableWindow {
	/** Vanilla's {@code imageHeight}. */
	public static final int VANILLA_HEIGHT = 166;
	/** How much taller the window is: nine list lines instead of vanilla's three 19-pixel offers. */
	public static final int EXTRA = 51;
	public static final int HEIGHT = VANILLA_HEIGHT + EXTRA;

	/** The background is drawn in three pieces: texture rows 0 to here, then the band, then the rest moved down. */
	public static final int SPLIT_ROW = 72;
	/** A texture row that is plain window grey across (borders included), stretched to fill {@link #EXTRA}. */
	public static final int BAND_ROW = 76;

	/** One list line; a picked entry takes two. */
	public static final int LINE = 12;
	/** The list's interior, inside vanilla's offer-box frame (which is one pixel outside it). */
	public static final int LIST_X = 60;
	public static final int LIST_Y = 14;
	public static final int LIST_WIDTH = 108;
	public static final int LIST_HEIGHT = 9 * LINE;
	/** Entry rows, then a one-pixel gap, then the scroll bar at the interior's right edge. */
	public static final int ROW_WIDTH = 101;
	public static final int SCROLLBAR_X = LIST_X + ROW_WIDTH + 1;
	public static final int SCROLLBAR_WIDTH = 6;
	/** Vanilla's villager-trade scroller sprite. */
	public static final int THUMB_HEIGHT = 27;

	/** A picked entry's second line: {@code <}, the level, {@code >}, as x ranges from the row's left edge. */
	public static final int DECREASE_LEFT = 2;
	public static final int LEVEL_LEFT = 12;
	public static final int INCREASE_LEFT = 44;
	public static final int INCREASE_RIGHT = 54;

	/** The column under the item and lapis slots: the price (or status) lines, then the Enchant button. */
	public static final int COLUMN_X = 7;
	public static final int COLUMN_WIDTH = 50;
	public static final int PRICE_Y = 66;
	public static final int TEXT_LINE = 10;
	/** Status lines that fit above the button. */
	public static final int STATUS_LINES = 3;
	public static final int BUTTON_X = 8;
	public static final int BUTTON_Y = 100;
	public static final int BUTTON_WIDTH = 48;
	public static final int BUTTON_HEIGHT = 20;

	/** Vanilla's {@code imageHeight - 94}, moved with the inventory. */
	public static final int INVENTORY_LABEL_Y = VANILLA_HEIGHT - 94 + EXTRA;

	private TableWindow() {
	}

	/** Where a player inventory slot vanilla puts at {@code vanillaY} goes. */
	public static int inventorySlotY(int vanillaY) {
		return vanillaY + EXTRA;
	}
}
