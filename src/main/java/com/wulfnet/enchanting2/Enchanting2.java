package com.wulfnet.enchanting2;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Enchanting2 implements ModInitializer {
	public static final String MOD_ID = "enchanting2";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Enchanting 2 initialized");
	}
}
