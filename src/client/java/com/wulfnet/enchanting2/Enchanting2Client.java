package com.wulfnet.enchanting2;

import net.fabricmc.api.ClientModInitializer;

public final class Enchanting2Client implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Enchanting2.LOGGER.info("Enchanting 2 client initialized");
	}
}
