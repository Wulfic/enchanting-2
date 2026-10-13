package com.wulfnet.enchanting2.tooltip;

/**
 * Whether a text tooltip fits the screen, and how wide Ctrl descriptions wrap (owner 2026-10-13: a tool with many
 * enchantments got a Ctrl tooltip taller than the screen). The numbers are vanilla's
 * ({@code GuiGraphics.renderTooltip} and {@code DefaultTooltipPositioner}, javap 1.21.11), so this stays MC-free.
 */
public final class TooltipFit {
	/** Pixels kept above a tooltip: vanilla keeps 4 left of one, and 3 below. */
	public static final int MARGIN = 4;
	private static final int BELOW = 3;
	private static final int LINE = 10;
	/** A lone line is 8 tall: vanilla starts the sum at -2 for one line. */
	private static final int SINGLE_LINE_TRIM = 2;
	/** The table and grindstone lists' wrap width ({@code EnchantmentDescriptions.WRAP_WIDTH}); never narrower. */
	public static final int MIN_COMPACT_WIDTH = 200;
	public static final int MAX_COMPACT_WIDTH = 320;
	/** Vanilla puts a tooltip 12 right of the cursor and flips it 24 left of that; 24 covers either side. */
	private static final int CURSOR_ROOM = 24;

	private TooltipFit() {
	}

	/**
	 * The height vanilla positions a text tooltip by, and draws its background to: 10 a line, 8 for a lone one.
	 */
	public static int height(int lines) {
		if (lines < 1) {
			throw new IllegalArgumentException("lines must be positive: " + lines);
		}
		return lines * LINE - (lines == 1 ? SINGLE_LINE_TRIM : 0);
	}

	/** Pixels of a {@code height}-tall tooltip that can't be on screen at once; 0 when it fits. */
	public static int overflow(int height, int screenHeight) {
		return Math.max(0, height + MARGIN + BELOW - screenHeight);
	}

	public static boolean fits(int lines, int screenHeight) {
		return overflow(height(lines), screenHeight) == 0;
	}

	/** The "Name: description" lines' wrap width: what fits beside the cursor on either side, within bounds. */
	public static int compactWidth(int screenWidth) {
		return Math.clamp(screenWidth / 2 - CURSOR_ROOM, MIN_COMPACT_WIDTH, MAX_COMPACT_WIDTH);
	}
}
