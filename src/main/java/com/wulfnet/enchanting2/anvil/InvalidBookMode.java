package com.wulfnet.enchanting2.anvil;

/**
 * DESIGN §6: what the anvil does with book entries the item can't take. Spelled lower-case in {@code common.json5}.
 * Owner, 2026-10-07: only {@link #STRIP_INVALID} ever destroys an entry, and the preview says so first.
 */
public enum InvalidBookMode {
	/** No result unless every entry applies. */
	REJECT_ALL,
	/** Valid entries apply; the book stays in the right slot holding only what was skipped. The default. */
	APPLY_VALID,
	/** Valid entries apply; the book is consumed and skipped entries are destroyed. */
	STRIP_INVALID
}
