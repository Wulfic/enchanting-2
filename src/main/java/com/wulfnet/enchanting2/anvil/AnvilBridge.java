package com.wulfnet.enchanting2.anvil;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;

/**
 * Whether this client is connected to a server that runs the Enchanting 2 anvil. If so, the client's {@code AnvilMenu}
 * must not compute its own result: vanilla's would disagree with the server's and desync the result slot. On a vanilla
 * server (no synced config) the client keeps vanilla (DESIGN §2a). Set by client init, like {@code PlayerExperience}.
 */
public final class AnvilBridge {
	private static volatile BooleanSupplier clientServerIsOurs = () -> false;
	private static volatile IntFunction<OptionalInt> clientLevelsCharged = containerId -> OptionalInt.empty();

	private AnvilBridge() {
	}

	public static boolean clientServerIsOurs() {
		return clientServerIsOurs.getAsBoolean();
	}

	public static void setClientSource(BooleanSupplier source) {
		clientServerIsOurs = Objects.requireNonNull(source, "source");
	}

	/**
	 * The levels the server's latest preview for this container would charge, exact past the short the cost slot syncs
	 * as. Empty when the client has no priced preview for it (then the synced cost is all there is).
	 */
	public static OptionalInt clientLevelsCharged(int containerId) {
		return clientLevelsCharged.apply(containerId);
	}

	public static void setClientPreviewSource(IntFunction<OptionalInt> source) {
		clientLevelsCharged = Objects.requireNonNull(source, "source");
	}
}
