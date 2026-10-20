package com.wulfnet.enchanting2.platform.neoforge;

import com.wulfnet.enchanting2.Enchanting2;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** NeoForge's entry point for both sides; the client half is {@code NeoForgeClientEntrypoint}. */
@Mod(Enchanting2.MOD_ID)
public final class NeoForgeEntrypoint {
	public NeoForgeEntrypoint(IEventBus modBus) {
		NeoForgeBus.set(modBus);
		Enchanting2.init();
	}
}
