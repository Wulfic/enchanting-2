package com.wulfnet.enchanting2.platform.neoforge;

import net.neoforged.bus.api.IEventBus;
import org.jspecify.annotations.Nullable;

/**
 * The mod's event bus, which NeoForge hands only to the {@code @Mod} constructor. {@link NeoForgeEntrypoint} sets it
 * before {@code Enchanting2.init()} touches {@code Services}, whose NeoForge implementations register mod-bus listeners
 * as they are built. Game-bus listeners go on {@code NeoForge.EVENT_BUS} directly.
 */
public final class NeoForgeBus {
	private static @Nullable IEventBus mod;

	private NeoForgeBus() {
	}

	static void set(IEventBus bus) {
		if (mod != null && mod != bus) {
			throw new IllegalStateException("the Enchanting 2 mod bus is already set");
		}
		mod = bus;
	}

	public static IEventBus mod() {
		IEventBus bus = mod;
		if (bus == null) {
			// Services was loaded before the @Mod constructor ran: a listener would be lost, so fail loudly instead.
			throw new IllegalStateException("Enchanting 2's NeoForge services were used before its @Mod constructor ran");
		}
		return bus;
	}
}
