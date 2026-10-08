package com.wulfnet.enchanting2.table;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Client facts the common table mixin needs, set by client init (like {@code GrindstoneBridge}): whether the server runs
 * Enchanting 2, in which case the client never rolls vanilla's offers.
 */
public final class TableBridge {
	private static volatile BooleanSupplier clientServerIsOurs = () -> false;

	private TableBridge() {
	}

	public static boolean clientServerIsOurs() {
		return clientServerIsOurs.getAsBoolean();
	}

	public static void setClientSource(BooleanSupplier source) {
		clientServerIsOurs = Objects.requireNonNull(source, "source");
	}
}
