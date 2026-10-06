package com.wulfnet.enchanting2.config;

/**
 * Implemented on {@code MinecraftServer} by {@code MinecraftServerMixin}, so the config belongs to one server
 * instance and an integrated server that closes and opens another world starts clean (DESIGN §2a). Read through
 * {@link ServerConfigs}, not directly.
 */
public interface ServerConfigHolder {
	/** @return the current config, or null before {@code SERVER_STARTING} */
	CommonConfig enchanting2$config();

	void enchanting2$setConfig(CommonConfig config);
}
