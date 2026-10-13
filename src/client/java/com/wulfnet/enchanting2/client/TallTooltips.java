package com.wulfnet.enchanting2.client;

import com.wulfnet.enchanting2.tooltip.TooltipScroll;
import net.minecraft.util.Util;

/**
 * The client's one {@link TooltipScroll} (owner 2026-10-13): {@code GuiGraphicsMixin} places a tall tooltip through it
 * and {@code MouseHandlerMixin} gives it the wheel first. Render and input both run on the client thread.
 */
public final class TallTooltips {
	private static final TooltipScroll SCROLL = new TooltipScroll();

	private TallTooltips() {
	}

	public static int y(int vanillaY, int width, int height, int screenHeight) {
		return SCROLL.y(vanillaY, width, height, screenHeight, Util.getNanos());
	}

	/** @return whether a tall tooltip took the wheel */
	public static boolean scroll(double notches) {
		return SCROLL.scroll(notches, Util.getNanos());
	}

	/** For the client GameTest. */
	public static int offset() {
		return SCROLL.offset();
	}
}
