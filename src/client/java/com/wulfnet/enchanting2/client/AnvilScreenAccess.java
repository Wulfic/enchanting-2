package com.wulfnet.enchanting2.client;

import com.wulfnet.enchanting2.anvil.AnvilPanelLayout;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Implemented on {@code AnvilScreen} by {@code AnvilScreenMixin}. What the screen last laid out and drew, so the client
 * GameTest can check the real screen rather than a copy of its logic.
 */
public interface AnvilScreenAccess {
	/** @return null on a vanilla server (no panel, no tab) */
	@Nullable
	AnvilPanelLayout enchanting2$layout();

	/** @return null on a vanilla server */
	@Nullable
	AnvilPanelTab enchanting2$tab();

	int enchanting2$leftPos();

	int enchanting2$nameBoxX();

	/** @return the cost label drawn in the last frame, or null if none was drawn */
	@Nullable
	String enchanting2$labelText();

	/** @return where the last label was drawn, relative to the window's left edge */
	int enchanting2$labelX();

	/** @return the panel's rows as drawn in the last frame (before wrapping); empty when the panel is hidden */
	List<String> enchanting2$panelText();
}
