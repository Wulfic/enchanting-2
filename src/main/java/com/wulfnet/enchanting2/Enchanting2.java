package com.wulfnet.enchanting2;

import com.wulfnet.enchanting2.acquisition.Acquisition;
import com.wulfnet.enchanting2.config.ServerConfigs;
import com.wulfnet.enchanting2.effect.HeadDrops;
import com.wulfnet.enchanting2.effect.ModEffects;
import com.wulfnet.enchanting2.effect.NightVisionTicker;
import com.wulfnet.enchanting2.enchantment.ItemWideningHook;
import com.wulfnet.enchanting2.enchantment.MaxLevelHook;
import com.wulfnet.enchanting2.fortune.FortuneDrops;
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
		ModEffects.register();
		ServerNetworking.registerPayloads();
		ServerNetworking.registerServer();
		ServerConfigs.register();
		MaxLevelHook.register();
		ItemWideningHook.register();
		PlacedBlocks.register();
		FortuneDrops.register();
		Acquisition.register();
		HeadDrops.register();
		NightVisionTicker.register();
		LOGGER.info("Enchanting 2 initialized");
	}
}
