package com.wulfnet.enchanting2.client;

import com.wulfnet.enchanting2.screen.PanelText.Zone;
import com.wulfnet.enchanting2.screen.SidePanelLayout;
import java.util.List;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;

/** Implemented on {@code EnchantmentScreen} by {@code EnchantmentScreenMixin}, for the client GameTest. */
public interface TableScreenAccess {
	/** @return null on a vanilla server, where the screen is vanilla's */
	@Nullable
	SidePanelLayout enchanting2$layout();

	int enchanting2$leftPos();

	/** Each panel row's text as last drawn, scrolled-away rows included. */
	List<String> enchanting2$panelText();

	/** The lines drawn in the window where vanilla's three offers were, the Enchant button's label last. */
	List<String> enchanting2$windowText();

	/** Screen Y of the row for {@code id}, as last drawn; empty when it's scrolled away or not listed. */
	OptionalInt enchanting2$rowY(String id);

	/** Screen X of a picked row's stepper arrow, as last drawn. */
	OptionalInt enchanting2$arrowX(String id, Zone zone);

	int enchanting2$scrollOffset();

	int enchanting2$maxScroll();
}
