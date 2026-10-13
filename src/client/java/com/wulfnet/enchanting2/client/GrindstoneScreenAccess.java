package com.wulfnet.enchanting2.client;

import com.wulfnet.enchanting2.grindstone.GrindstoneChoice;
import java.util.List;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;

/** Implemented on {@code GrindstoneScreen} by {@code GrindstoneScreenMixin}, for the client GameTest. */
public interface GrindstoneScreenAccess {
	/** @return the window is ours: wide, with the picker; false on a vanilla server */
	boolean enchanting2$wide();

	int enchanting2$leftPos();

	int enchanting2$imageWidth();

	/** Each item's text as last drawn: headings, picks and the instruction. */
	List<String> enchanting2$listText();

	/** The hover tooltip as last drawn; empty when no pick is under the mouse. */
	List<String> enchanting2$rowTooltip();

	/** Screen Y of the row that picks {@code choice}, as last drawn; empty when it isn't in view. */
	OptionalInt enchanting2$rowY(GrindstoneChoice choice);

	/** @return the list's interior left edge on screen */
	int enchanting2$listLeft();

	/** @return the list's interior top edge on screen */
	int enchanting2$listTop();

	/** @return the price label drawn in the last frame, or null if none was drawn */
	@Nullable
	String enchanting2$labelText();
}
