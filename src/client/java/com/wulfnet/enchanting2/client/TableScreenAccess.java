package com.wulfnet.enchanting2.client;

import com.wulfnet.enchanting2.screen.PanelText.Zone;
import java.util.List;
import java.util.OptionalInt;

/** Implemented on {@code EnchantmentScreen} by {@code EnchantmentScreenMixin}, for the client GameTest. */
public interface TableScreenAccess {
	/** @return false on a vanilla server, where the screen is vanilla's */
	boolean enchanting2$ours();

	int enchanting2$leftPos();

	int enchanting2$topPos();

	int enchanting2$imageHeight();

	/** Each entry's name as last drawn, in list order, scrolled-away entries included. */
	List<String> enchanting2$listText();

	/** The column under the slots as last drawn (price lines or the status), then the Enchant button's label. */
	List<String> enchanting2$windowText();

	/** The Enchant button's tooltip: the breakdown's lines, as last set. */
	List<String> enchanting2$buttonTooltip();

	boolean enchanting2$buttonActive();

	/** The tooltip of the entry under the mouse, as last drawn; empty when the mouse isn't on one. */
	List<String> enchanting2$rowTooltip();

	/** The enchantment IDs of the entries in the order last drawn, scrolled-away ones included. */
	List<String> enchanting2$rowIds();

	/** Screen X of the rows' left edge. */
	int enchanting2$rowsX();

	/** Screen Y of the middle of {@code id}'s first line, as last drawn; empty when it's not fully in view. */
	OptionalInt enchanting2$rowY(String id);

	/** Screen X of a picked entry's stepper arrow; the arrows are one line below {@link #enchanting2$rowY}. */
	OptionalInt enchanting2$arrowX(String id, Zone zone);

	int enchanting2$scrollOffset();

	int enchanting2$maxScroll();

	/** Screen X of the scroll bar's left edge. */
	int enchanting2$scrollBarX();

	/** Screen Y of the middle of the scroll bar's thumb. */
	int enchanting2$thumbCenterY();
}
