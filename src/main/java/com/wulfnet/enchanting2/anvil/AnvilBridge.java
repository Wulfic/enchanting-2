package com.wulfnet.enchanting2.anvil;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Whether this client is connected to a server that runs the Enchanting 2 anvil. If so, the client's {@code AnvilMenu}
 * must not compute its own result: vanilla's would disagree with the server's and desync the result slot. On a vanilla
 * server (no synced config) the client keeps vanilla (DESIGN §2a). Set by client init, like {@code PlayerExperience}.
 */
public final class AnvilBridge {
	private static volatile BooleanSupplier clientServerIsOurs = () -> false;

	private AnvilBridge() {
	}

	public static boolean clientServerIsOurs() {
		return clientServerIsOurs.getAsBoolean();
	}

	public static void setClientSource(BooleanSupplier source) {
		clientServerIsOurs = Objects.requireNonNull(source, "source");
	}
}
