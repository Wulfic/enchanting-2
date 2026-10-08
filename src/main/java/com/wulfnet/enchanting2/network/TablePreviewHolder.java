package com.wulfnet.enchanting2.network;

import org.jspecify.annotations.Nullable;

/** Implemented on {@code EnchantmentMenu} by {@code EnchantmentMenuMixin}: the last panel built for this menu. */
public interface TablePreviewHolder {
	/**
	 * Kept whether or not the client could receive it, so a change is detected by value either way.
	 *
	 * @return null on the client, and on the server before the first one
	 */
	@Nullable
	TablePreviewPayload enchanting2$lastPreview();
}
