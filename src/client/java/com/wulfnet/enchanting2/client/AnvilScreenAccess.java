package com.wulfnet.enchanting2.client;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Implemented on {@code AnvilScreen} by {@code AnvilScreenMixin}. What the screen last laid out and drew, so the client
 * GameTest can check the real screen rather than a copy of its logic.
 */
public interface AnvilScreenAccess {
	/** @return the window is ours: wide, with the breakdown box; false on a vanilla server */
	boolean enchanting2$wide();

	int enchanting2$leftPos();

	int enchanting2$imageWidth();

	int enchanting2$nameBoxX();

	/** @return the cost label drawn in the last frame, or null if none was drawn */
	@Nullable
	String enchanting2$labelText();

	/** @return where the last label was drawn, relative to the window's left edge */
	int enchanting2$labelX();

	/** @return the box's rows as drawn in the last frame (before wrapping); empty on a vanilla server */
	List<String> enchanting2$boxText();

	/** @return the box's amounts with their units, as drawn in the last frame */
	List<String> enchanting2$boxAmounts();

	/** @return the box's interior left edge on screen */
	int enchanting2$boxLeft();

	/** @return the box's interior top edge on screen */
	int enchanting2$boxTop();

	int enchanting2$scrollOffset();

	int enchanting2$maxScroll();

	int enchanting2$scrollBarX();

	int enchanting2$thumbCenterY();
}
