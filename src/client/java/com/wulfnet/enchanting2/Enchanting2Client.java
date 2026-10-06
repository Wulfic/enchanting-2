package com.wulfnet.enchanting2;

import com.wulfnet.enchanting2.client.ClientConfigState;
import com.wulfnet.enchanting2.config.CommonConfig;
import com.wulfnet.enchanting2.experience.ExperienceCurve;
import com.wulfnet.enchanting2.experience.PlayerExperience;
import net.fabricmc.api.ClientModInitializer;

public final class Enchanting2Client implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientConfigState.register();
		// No synced copy means a vanilla server (or not connected): draw the vanilla curve.
		PlayerExperience.setClientSource(() -> ClientConfigState.serverCopy()
				.map(CommonConfig::experience)
				.orElse(ExperienceCurve.VANILLA));
		Enchanting2.LOGGER.info("Enchanting 2 client initialized");
	}
}
