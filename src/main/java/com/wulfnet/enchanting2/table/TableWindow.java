package com.wulfnet.enchanting2.table;

import com.wulfnet.enchanting2.screen.ScrollModel;
import com.wulfnet.enchanting2.screen.WindowPiece;
import java.util.List;

/**
 * The enchanting table window's layout on a server running our table (owner 2026-10-13, layout B, then 72 wider; TODO
 * 10l): vanilla's window made {@link #EXTRA_WIDTH} wider and {@link #EXTRA} taller, the book kept, the list in vanilla's
 * offer box grown to fill the new room, and the price and Enchant button under the item and lapis slots. Every
 * coordinate is GUI pixels from the window's top-left. MC-free, so the arithmetic that has to line up is unit-tested.
 */
public final class TableWindow {
	public static final int VANILLA_WIDTH = 176;
	/** Vanilla's {@code imageHeight}. */
	public static final int VANILLA_HEIGHT = 166;
	/** How much wider: one-line rows with the name, the level stepper and the cost side by side. */
	public static final int EXTRA_WIDTH = 72;
	/** How much taller: nine list lines instead of vanilla's three 19-pixel offers. */
	public static final int EXTRA = 51;
	public static final int WIDTH = VANILLA_WIDTH + EXTRA_WIDTH;
	public static final int HEIGHT = VANILLA_HEIGHT + EXTRA;

	/** The background is drawn in three bands: texture rows 0 to here, the stretched grey row, then the rest. */
	public static final int SPLIT_ROW = 72;
	/** A texture row that is plain window grey across (borders included), stretched to fill {@link #EXTRA}. */
	public static final int BAND_ROW = 76;

	/** The top band: the left part as is (title, book, slots), a plain grey column stretched over the old box, the right border. */
	public static final List<WindowPiece> TOP = List.of(new WindowPiece(0, 58, 0, 58),
			new WindowPiece(58, 1, 58, WIDTH - 58 - 6), new WindowPiece(170, 6, WIDTH - 6, 6));
	/** The grey band and the bottom (inventory) band: borders kept, the inventory grid centred, grey on both sides. */
	public static final List<WindowPiece> BAND = List.of(new WindowPiece(0, 4, 0, 4),
			new WindowPiece(4, 1, 4, WIDTH - 8), new WindowPiece(172, 4, WIDTH - 4, 4));
	public static final List<WindowPiece> BOTTOM = List.of(new WindowPiece(0, 4, 0, 4),
			new WindowPiece(4, 1, 4, EXTRA_WIDTH / 2), new WindowPiece(4, 168, 4 + EXTRA_WIDTH / 2, 168),
			new WindowPiece(170, 1, 172 + EXTRA_WIDTH / 2, EXTRA_WIDTH / 2), new WindowPiece(172, 4, WIDTH - 4, 4));

	/** One list line per entry. */
	public static final int LINE = 12;
	/** The list's interior, inside vanilla's offer-box frame (which is one pixel outside it). */
	public static final int LIST_X = 60;
	public static final int LIST_Y = 14;
	public static final int LIST_WIDTH = 108 + EXTRA_WIDTH;
	public static final int LIST_HEIGHT = 9 * LINE;
	/** Entry rows, then a one-pixel gap, then the scroll bar at the interior's right edge. */
	public static final int SCROLLBAR_WIDTH = 6;
	public static final int ROW_WIDTH = LIST_WIDTH - SCROLLBAR_WIDTH - 1;
	public static final int SCROLLBAR_X = LIST_X + ROW_WIDTH + 1;
	/** Vanilla's villager-trade scroller sprite. */
	public static final int THUMB_HEIGHT = ScrollModel.THUMB_HEIGHT;

	/**
	 * A row, as x ranges from its left edge: the name from {@link #NAME_LEFT} up to {@link #NAME_RIGHT}, then (picked)
	 * {@code <}, the level and {@code >}, then the cost, right-aligned to {@link #COST_RIGHT}.
	 */
	public static final int NAME_LEFT = 4;
	public static final int COST_RIGHT = ROW_WIDTH - 4;
	public static final int COST_WIDTH = 28;
	public static final int INCREASE_RIGHT = COST_RIGHT - COST_WIDTH - 2;
	public static final int INCREASE_LEFT = INCREASE_RIGHT - 8;
	public static final int LEVEL_LEFT = INCREASE_LEFT - 18;
	public static final int DECREASE_LEFT = LEVEL_LEFT - 8;
	public static final int NAME_RIGHT = DECREASE_LEFT - 2;

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

	/** Vanilla's {@code imageHeight - 94} and x 8, moved with the inventory. */
	public static final int INVENTORY_LABEL_X = 8 + EXTRA_WIDTH / 2;
	public static final int INVENTORY_LABEL_Y = VANILLA_HEIGHT - 94 + EXTRA;

	private TableWindow() {
	}

	/** Where a player inventory slot vanilla puts at {@code vanillaX} goes: centred in the wider window. */
	public static int inventorySlotX(int vanillaX) {
		return vanillaX + EXTRA_WIDTH / 2;
	}

	/** Where a player inventory slot vanilla puts at {@code vanillaY} goes. */
	public static int inventorySlotY(int vanillaY) {
		return vanillaY + EXTRA;
	}
}
