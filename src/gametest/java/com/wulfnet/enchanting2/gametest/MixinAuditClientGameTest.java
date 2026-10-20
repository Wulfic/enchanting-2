package com.wulfnet.enchanting2.gametest;

import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * Phase 12B: every mixin target of ours on the client, the client-only config included, loads with its mixins applied
 * ({@link MixinTargets}). No world needed.
 */
public class MixinAuditClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		List<String> failures = context.computeOnClient(client -> MixinTargets.loadAll("enchanting2.mixins.json",
				"enchanting2.client.mixins.json", "enchanting2-gametest.mixins.json"));
		if (!failures.isEmpty()) {
			throw new AssertionError("mixin targets failed: " + failures);
		}
	}
}
