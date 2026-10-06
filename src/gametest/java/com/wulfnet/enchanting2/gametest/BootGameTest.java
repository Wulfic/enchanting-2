package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.Enchanting2;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

public class BootGameTest {
	@GameTest
	public void modIsLoadedOnTheServer(GameTestHelper helper) {
		helper.assertTrue(FabricLoader.getInstance().isModLoaded(Enchanting2.MOD_ID),
				Component.literal("enchanting2 is not loaded on the server"));
		helper.succeed();
	}
}
