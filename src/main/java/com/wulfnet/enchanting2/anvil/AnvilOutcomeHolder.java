package com.wulfnet.enchanting2.anvil;

import org.jspecify.annotations.Nullable;

/** Implemented on {@code AnvilMenu} by {@code AnvilMenuMixin}: the server's outcome for the current inputs. */
public interface AnvilOutcomeHolder {
	/** @return null on the client, and on the server before the first {@code createResult} */
	@Nullable
	AnvilOutcome enchanting2$outcome();
}
