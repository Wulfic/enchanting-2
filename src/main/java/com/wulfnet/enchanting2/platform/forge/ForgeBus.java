package com.wulfnet.enchanting2.platform.forge;

import net.minecraftforge.eventbus.api.bus.BusGroup;
import org.jspecify.annotations.Nullable;

/**
 * The mod's bus group, which Forge hands only to the {@code @Mod} constructor. {@link ForgeEntrypoint} sets it before
 * {@code Enchanting2.init()} touches {@code Services}, whose Forge implementations register mod-bus listeners as they
 * are built (as {@code NeoForgeBus}). Game events have one static bus each ({@code SomeEvent.BUS}), used directly.
 */
public final class ForgeBus {
	private static @Nullable BusGroup mod;

	private ForgeBus() {
	}

	static void set(BusGroup group) {
		if (mod != null && mod != group) {
			throw new IllegalStateException("the Enchanting 2 mod bus group is already set");
		}
		mod = group;
	}

	public static BusGroup mod() {
		BusGroup group = mod;
		if (group == null) {
			// Services was loaded before the @Mod constructor ran: a listener would be lost, so fail loudly instead.
			throw new IllegalStateException("Enchanting 2's Forge services were used before its @Mod constructor ran");
		}
		return group;
	}
}
