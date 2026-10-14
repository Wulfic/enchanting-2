package com.wulfnet.enchanting2.screen;

import java.util.ArrayList;
import java.util.List;

/**
 * The config screen's tabs in as few rows as fit the screen, instead of one row that scrolls (owner 2026-10-13, Phase
 * 11d). Tabs keep their order and are split into rows of about the same width, and each row is stretched to fill the
 * space between the margins. MC-free.
 *
 * @param cells each tab's place, in tab order
 * @param rows  how many rows
 */
public record TabRows(List<Cell> cells, int rows) {
	/** Vanilla's tab button height. */
	public static final int ROW_HEIGHT = 24;
	/** YACL's margin either side of its tab row ({@code ScrollableNavigationBar.NAVBAR_MARGIN}). */
	public static final int MARGIN = 28;

	/** One tab button: left edge, top edge, width. */
	public record Cell(int x, int y, int width) {
		public int right() {
			return x + width;
		}
	}

	public TabRows {
		cells = List.copyOf(cells);
	}

	/** The rows' total height: the content starts under it. */
	public int height() {
		return rows * ROW_HEIGHT;
	}

	/**
	 * @param widths      each tab's natural width (its title plus padding), in tab order
	 * @param screenWidth the screen's GUI width
	 */
	public static TabRows of(List<Integer> widths, int screenWidth) {
		if (widths.isEmpty()) {
			throw new IllegalArgumentException("no tabs");
		}
		int room = Math.max(1, screenWidth - 2 * MARGIN);
		// A title wider than the whole row gets the whole row; its button clips the text, as vanilla's does.
		int[] fitted = new int[widths.size()];
		int widest = 0;
		for (int i = 0; i < fitted.length; i++) {
			int width = widths.get(i);
			if (width <= 0) {
				throw new IllegalArgumentException("tab " + i + " width = " + width);
			}
			fitted[i] = Math.min(width, room);
			widest = Math.max(widest, fitted[i]);
		}
		// Filling each row before starting the next gives the fewest rows; the smallest row width that still needs
		// no more rows than that evens them out.
		int rowCount = split(fitted, room).size();
		int low = widest;
		int high = room;
		while (low < high) {
			int mid = (low + high) >>> 1;
			if (split(fitted, mid).size() <= rowCount) {
				high = mid;
			} else {
				low = mid + 1;
			}
		}
		List<int[]> rows = split(fitted, low);
		List<Cell> cells = new ArrayList<>(fitted.length);
		for (int row = 0; row < rows.size(); row++) {
			int first = rows.get(row)[0];
			int end = rows.get(row)[1];
			int count = end - first;
			int used = 0;
			for (int i = first; i < end; i++) {
				used += fitted[i];
			}
			int spare = room - used;
			int x = MARGIN;
			for (int i = first; i < end; i++) {
				int width = fitted[i] + spare / count + (i - first < spare % count ? 1 : 0);
				cells.add(new Cell(x, row * ROW_HEIGHT, width));
				x += width;
			}
		}
		return new TabRows(cells, rows.size());
	}

	/** Each row's [first, end) tab indices, filling a row up to {@code capacity} before the next. */
	private static List<int[]> split(int[] widths, int capacity) {
		List<int[]> rows = new ArrayList<>();
		int first = 0;
		int used = 0;
		for (int i = 0; i < widths.length; i++) {
			if (i > first && used + widths[i] > capacity) {
				rows.add(new int[] {first, i});
				first = i;
				used = 0;
			}
			used += widths[i];
		}
		rows.add(new int[] {first, widths.length});
		return rows;
	}
}
