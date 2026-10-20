package com.wulfnet.enchanting2.client;

import com.wulfnet.enchanting2.tooltip.TooltipScroll;
import java.util.Objects;
import java.util.function.LongSupplier;
import net.minecraft.util.Util;

/**
 * The client's one {@link TooltipScroll} (owner 2026-10-13): {@code GuiGraphicsMixin} places a tall tooltip through it
 * and {@code MouseHandlerMixin} gives it the wheel first. Render and input both run on the client thread.
 */
public final class TallTooltips {
	/** What {@link TooltipScroll}'s stale check reads in play. */
	public static final LongSupplier REAL_CLOCK = Util::getNanos;

	private static final TooltipScroll SCROLL = new TooltipScroll();
	private static LongSupplier clock = REAL_CLOCK;

	private TallTooltips() {
	}

	public static int y(int vanillaY, int width, int height, int screenHeight) {
		return SCROLL.y(vanillaY, width, height, screenHeight, clock.getAsLong());
	}

	/** @return whether a tall tooltip took the wheel */
	public static boolean scroll(double notches) {
		return SCROLL.scroll(notches, clock.getAsLong());
	}

	/** For the client GameTest. */
	public static int offset() {
		return SCROLL.offset();
	}

	/**
	 * For the client GameTest, on the client thread: a GameTest client only draws on a tick, and a loaded machine or a
	 * screenshot can leave more than {@link TooltipScroll#STALE_NANOS} between a frame and the next wheel notch
	 * (gotchas.md 2026-10-13, 2026-10-19), so the test holds time still. {@link #REAL_CLOCK} puts play's back.
	 */
	public static void useClock(LongSupplier testClock) {
		clock = Objects.requireNonNull(testClock, "testClock");
	}
}
