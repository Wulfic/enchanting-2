package com.wulfnet.enchanting2.grindstone;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;

/**
 * Client facts the common grindstone mixins need, set by client init (like {@code AnvilBridge}): whether the server
 * runs Enchanting 2 (then the client neither computes vanilla results nor refuses plain books), and the levels the
 * server's latest panel says the pick costs.
 */
public final class GrindstoneBridge {
	private static volatile BooleanSupplier clientServerIsOurs = () -> false;
	private static volatile IntFunction<OptionalInt> clientLevelsCharged = containerId -> OptionalInt.empty();

	private GrindstoneBridge() {
	}

	public static boolean clientServerIsOurs() {
		return clientServerIsOurs.getAsBoolean();
	}

	public static void setClientSource(BooleanSupplier source) {
		clientServerIsOurs = Objects.requireNonNull(source, "source");
	}

	/** Levels the server's latest panel for this container would charge; empty when there's no OK pick. */
	public static OptionalInt clientLevelsCharged(int containerId) {
		return clientLevelsCharged.apply(containerId);
	}

	public static void setClientPreviewSource(IntFunction<OptionalInt> source) {
		clientLevelsCharged = Objects.requireNonNull(source, "source");
	}
}
