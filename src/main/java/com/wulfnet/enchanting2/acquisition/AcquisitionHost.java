package com.wulfnet.enchanting2.acquisition;

import net.minecraft.server.MinecraftServer;

/**
 * Implemented on {@code MappedRegistry} by {@code MappedRegistryMixin}. Each server builds its own enchantment registry
 * and stamps itself on it, so code that only has an enchantment holder (vanilla's {@code selectEnchantment} has no level
 * or server) can still find that server's config, with no static world state (DESIGN §2a). Read through
 * {@link Acquisition}, not directly.
 */
public interface AcquisitionHost {
	/** @return the server that owns this registry, or null when none is running on it (a client copy, a stopped server) */
	MinecraftServer enchanting2$acquisitionServer();

	void enchanting2$setAcquisitionServer(MinecraftServer server);
}
