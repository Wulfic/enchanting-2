package com.wulfnet.enchanting2.screen;

import java.util.List;

/**
 * The anvil's and grindstone's window on a server running ours (TODO 10o; owner 2026-10-13, decisions.md "full-height
 * right column"): vanilla's window untouched on the left, {@link #EXTRA_WIDTH} wider, and one box running the full
 * height of the new right column, as the villager trade list does on its left. Every coordinate is GUI pixels from the
 * window's top-left. MC-free, so the arithmetic that has to line up with vanilla's textures is unit-tested.
 */
public final class ColumnWindow {
	public static final int VANILLA_WIDTH = 176;
	/** Vanilla's {@code imageHeight}; the window keeps it. */
	public static final int HEIGHT = 166;
	public static final int EXTRA_WIDTH = 120;
	public static final int WIDTH = VANILLA_WIDTH + EXTRA_WIDTH;

	/**
	 * A texture column that is plain grey from row 3 to 162 with the straight top and bottom border above and below, in
	 * both {@code anvil.png} and {@code grindstone.png}; everything left of it is drawn as is.
	 */
	public static final int PLAIN_COLUMN = 170;
	/** Vanilla's right border: three grey columns, two shadow, one black. */
	public static final int BORDER = 6;

	/** The background, full height: vanilla's part, the plain column stretched over the extra width, the right border. */
	public static final List<WindowPiece> BACKGROUND = List.of(new WindowPiece(0, PLAIN_COLUMN, 0, PLAIN_COLUMN),
			new WindowPiece(PLAIN_COLUMN, 1, PLAIN_COLUMN, WIDTH - BORDER - PLAIN_COLUMN),
			new WindowPiece(VANILLA_WIDTH - BORDER, BORDER, WIDTH - BORDER, BORDER));

	/** The box's interior, below its one-pixel inset frame and above it; the same in both windows. */
	public static final int BOX_TOP = 11;
	public static final int BOX_HEIGHT = 144;
	/** The interior ends here (exclusive); the frame's right edge is this column. */
	public static final int BOX_RIGHT = WIDTH - BORDER - 1;

	/**
	 * Both windows' box: right of vanilla's part, which runs to column 168 at full height (the inventory grid, and the
	 * anvil's rename box). Both textures are plain grey from 169 over the box's whole height, so its frame sits at 172.
	 */
	public static final Box BOX = new Box(173);

	/** The scroll bar at the interior's right edge, a one-pixel gap left of it. */
	public static final int SCROLLBAR_WIDTH = 6;

	/** Where vanilla's anvil draws its cost label, and where the grindstone's price goes (y, right edge). */
	public static final int LABEL_Y = 69;
	public static final int LABEL_RIGHT = VANILLA_WIDTH - 8;

	private ColumnWindow() {
	}

	/** The box: its interior from {@code left} to {@link #BOX_RIGHT}, {@link #BOX_TOP} down {@link #BOX_HEIGHT}. */
	public record Box(int left) {
		public Box {
			if (left <= 0 || left >= BOX_RIGHT - SCROLLBAR_WIDTH - 1) {
				throw new IllegalArgumentException("left = " + left);
			}
		}

		public int width() {
			return BOX_RIGHT - left;
		}

		/** Rows end a pixel before the scroll bar. */
		public int rowWidth() {
			return width() - SCROLLBAR_WIDTH - 1;
		}

		public int scrollBarX() {
			return BOX_RIGHT - SCROLLBAR_WIDTH;
		}
	}
}
