package com.wulfnet.enchanting2.network;

import org.jspecify.annotations.Nullable;

/** Implemented on {@code AnvilMenu} by {@code AnvilMenuMixin}: the last preview built for this menu on the server. */
public interface AnvilPreviewHolder {
	/**
	 * Kept whether or not the client could receive it, so a changed preview is detected by value either way.
	 *
	 * @return null on the client, and on the server before the first {@code createResult}
	 */
	@Nullable
	AnvilPreviewPayload enchanting2$lastPreview();
}
