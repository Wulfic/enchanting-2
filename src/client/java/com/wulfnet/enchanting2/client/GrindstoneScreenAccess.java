package com.wulfnet.enchanting2.client;

import com.wulfnet.enchanting2.grindstone.GrindstoneChoice;
import com.wulfnet.enchanting2.screen.SidePanelLayout;
import java.util.List;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;

/** Implemented on {@code GrindstoneScreen} by {@code GrindstoneScreenMixin}, for the client GameTest. */
public interface GrindstoneScreenAccess {
	/** @return null on a vanilla server, where the screen is vanilla's */
	@Nullable
	SidePanelLayout enchanting2$layout();

	int enchanting2$leftPos();

	/** Each row's text as last drawn. */
	List<String> enchanting2$panelText();

	/** Screen Y of the row that picks {@code choice}, as last drawn; empty when it isn't shown. */
	OptionalInt enchanting2$rowY(GrindstoneChoice choice);
}
