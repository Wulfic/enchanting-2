package com.wulfnet.enchanting2.tooltip;

/**
 * Scrolling a tooltip taller than the screen (owner 2026-10-13). Vanilla moves such a tooltip up until its bottom fits,
 * so its top (the item's name and first enchantments) is off screen and can't be reached. Here it starts at its top,
 * and the mouse wheel moves it while it is being shown. One per client; MC-free, so every case is unit-tested.
 *
 * <p>A tooltip is told apart from the last one by its size; a different size, or one not drawn for
 * {@link #STALE_NANOS}, starts at the top again. The wheel is taken only while a tall tooltip is being drawn.
 */
public final class TooltipScroll {
	/** Three lines a notch. */
	public static final int STEP = 30;
	/** Longer than a frame at any playable rate, shorter than moving to another item and back. */
	public static final long STALE_NANOS = 200_000_000L;

	private int offset;
	private int maxOffset;
	private long size = -1;
	private long lastDrawn;
	private boolean drawn;

	/**
	 * Where to draw a tooltip, called once a frame for the tooltip being drawn.
	 *
	 * @param vanillaY where vanilla's positioner put it
	 * @param now      {@code System.nanoTime()}
	 * @return {@code vanillaY} when it fits; otherwise its top, scrolled
	 */
	public int y(int vanillaY, int width, int height, int screenHeight, long now) {
		int overflow = TooltipFit.overflow(height, screenHeight);
		if (overflow == 0) {
			return vanillaY;
		}
		long key = (long) width << 32 | (height & 0xFFFFFFFFL);
		if (key != size || stale(now)) {
			offset = 0;
		}
		size = key;
		lastDrawn = now;
		drawn = true;
		maxOffset = overflow;
		offset = Math.clamp(offset, 0, maxOffset);
		return TooltipFit.MARGIN - offset;
	}

	/**
	 * The wheel, while a tall tooltip is shown.
	 *
	 * @param notches vanilla's vertical scroll amount: positive is up
	 * @return whether the tooltip took it (otherwise it is the screen's)
	 */
	public boolean scroll(double notches, long now) {
		if (stale(now)) {
			return false;
		}
		offset = Math.clamp(offset - Math.round(notches * STEP), 0, maxOffset);
		return true;
	}

	/** The current offset, for the client GameTest. */
	public int offset() {
		return offset;
	}

	private boolean stale(long now) {
		return !drawn || now - lastDrawn > STALE_NANOS;
	}
}
