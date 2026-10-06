package com.wulfnet.enchanting2;

import com.wulfnet.enchanting2.client.ClientConfigState;
import net.fabricmc.api.ClientModInitializer;

public final class Enchanting2Client implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientConfigState.register();
		Enchanting2.LOGGER.info("Enchanting 2 client initialized");
	}
}
