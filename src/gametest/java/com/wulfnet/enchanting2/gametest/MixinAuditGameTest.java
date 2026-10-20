package com.wulfnet.enchanting2.gametest;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

/** Phase 12B: every server-side mixin target of ours loads with its mixins applied ({@link MixinTargets}). */
public class MixinAuditGameTest {
	@GameTest
	public void everyMixinTargetLoads(GameTestHelper helper) {
		List<String> failures = MixinTargets.loadAll("enchanting2.mixins.json", "enchanting2-gametest.mixins.json");
		helper.assertTrue(failures.isEmpty(), Component.literal("mixin targets failed: " + failures));
		helper.succeed();
	}
}
