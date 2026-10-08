package com.wulfnet.enchanting2;

import com.wulfnet.enchanting2.config.ServerConfigs;
import com.wulfnet.enchanting2.enchantment.MaxLevelHook;
import com.wulfnet.enchanting2.fortune.PlacedBlocks;
import com.wulfnet.enchanting2.network.ServerNetworking;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Enchanting2 implements ModInitializer {
	public static final String MOD_ID = "enchanting2";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ServerNetworking.registerPayloads();
		ServerNetworking.registerServer();
		ServerConfigs.register();
		MaxLevelHook.register();
		PlacedBlocks.register();
		LOGGER.info("Enchanting 2 initialized");
	}
}
