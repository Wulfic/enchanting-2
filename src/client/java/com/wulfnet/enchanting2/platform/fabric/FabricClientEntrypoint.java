package com.wulfnet.enchanting2.platform.fabric;

import com.wulfnet.enchanting2.Enchanting2Client;
import net.fabricmc.api.ClientModInitializer;

/** fabric.mod.json {@code client}. */
public final class FabricClientEntrypoint implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Enchanting2Client.init();
	}
}
