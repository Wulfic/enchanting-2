package com.wulfnet.enchanting2.table;

import org.jspecify.annotations.Nullable;

/** Implemented on {@code EnchantmentMenu} by {@code EnchantmentMenuMixin}. */
public interface TableMenuAccess {
	/**
	 * @return the server's outcome for the current item, lapis, power and selection; null on the client and before the
	 *         first one
	 */
	@Nullable
	TableOutcome enchanting2$outcome();

	/** The selection the server holds for this menu. Always empty on the client. */
	TableSelection enchanting2$selection();

	/**
	 * Server: replace the selection and recompute. An invalid pick enchants nothing, so a stale or forged selection can't
	 * buy anything the item and the table's power don't allow.
	 */
	void enchanting2$select(TableSelection selection);

	/**
	 * Server: the Enchant button. Recomputes first (power, levels, config may have changed), then enchants only when the
	 * outcome is OK and {@code selection}, {@code levels} and {@code lapis} equal what the server holds and would charge
	 * now. Anything else changes nothing and resends the panel.
	 *
	 * @return whether the item was enchanted
	 */
	boolean enchanting2$enchant(TableSelection selection, int levels, int lapis);

	/**
	 * Our rules apply to this menu: it's the server's, or the client's while connected to a server running Enchanting 2.
	 * A client on a vanilla server keeps the vanilla table.
	 */
	boolean enchanting2$handledByUs();
}
