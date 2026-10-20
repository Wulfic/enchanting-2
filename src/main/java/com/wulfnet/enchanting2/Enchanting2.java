package com.wulfnet.enchanting2;

import com.wulfnet.enchanting2.acquisition.Acquisition;
import com.wulfnet.enchanting2.bucket.InfiniteBuckets;
import com.wulfnet.enchanting2.combat.LastStand;
import com.wulfnet.enchanting2.config.ServerConfigs;
import com.wulfnet.enchanting2.drops.KeepOnDeath;
import com.wulfnet.enchanting2.effect.HeadDrops;
import com.wulfnet.enchanting2.effect.ModEffects;
import com.wulfnet.enchanting2.effect.NightVisionTicker;
import com.wulfnet.enchanting2.enchantment.ItemWideningHook;
import com.wulfnet.enchanting2.enchantment.MaxLevelHook;
import com.wulfnet.enchanting2.fortune.FortuneDrops;
import com.wulfnet.enchanting2.fortune.PlacedBlocks;
import com.wulfnet.enchanting2.mobs.MobHealth;
import com.wulfnet.enchanting2.network.ServerNetworking;
import com.wulfnet.enchanting2.shield.BulwarkEffect;
import com.wulfnet.enchanting2.spawner.CollectedSpawners;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common mod init. Each loader's entry point ({@code platform/<loader>/}) calls {@link #init()} once. */
public final class Enchanting2 {
	public static final String MOD_ID = "enchanting2";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private Enchanting2() {
	}

	public static void init() {
		ModEffects.register();
		BulwarkEffect.register();
		KeepOnDeath.register();
		LastStand.register();
		CollectedSpawners.register();
		InfiniteBuckets.register();
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
		MobHealth.register();
		LOGGER.info("Enchanting 2 initialized");
	}
}
