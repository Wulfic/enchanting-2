package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.platform.Services;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

public class BootGameTest {
	@GameTest
	public void modIsLoadedOnTheServer(GameTestHelper helper) {
		helper.assertTrue(Services.LOADER.isModLoaded(Enchanting2.MOD_ID),
				Component.literal("enchanting2 is not loaded on the server"));
		helper.succeed();
	}
}
