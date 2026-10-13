package com.wulfnet.enchanting2.screen;

/**
 * One horizontal piece of a widened window's background: texture columns {@code [u, u + uWidth)} drawn over window
 * columns {@code [x, x + width)}, stretched when the widths differ. The enchanting table's bands (TODO 10l) and the
 * anvil's and grindstone's column (10o) are lists of these.
 */
public record WindowPiece(int u, int uWidth, int x, int width) {
	public WindowPiece {
		if (u < 0 || uWidth <= 0 || x < 0 || width <= 0) {
			throw new IllegalArgumentException("bad piece: u " + u + " uWidth " + uWidth + " x " + x + " width " + width);
		}
	}
}
