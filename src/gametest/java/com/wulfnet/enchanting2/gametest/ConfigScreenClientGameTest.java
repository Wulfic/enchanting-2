package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.acquisition.AcquisitionRules;
import com.wulfnet.enchanting2.acquisition.EnchantmentFlagsDraft;
import com.wulfnet.enchanting2.acquisition.Source;
import com.wulfnet.enchanting2.client.ConfigEditing;
import com.wulfnet.enchanting2.client.ModMenuIntegration;
import com.wulfnet.enchanting2.config.ConfigView.Mode;
import com.wulfnet.enchanting2.config.EnchantmentOverrides;
import com.wulfnet.enchanting2.config.ServerConfigs;
import com.wulfnet.enchanting2.enchantment.ModEnchantments;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.Screen;

/**
 * Stage 9C on the real client: the config screen (with its Enchantments category) builds on the title screen, where
 * only vanilla's enchantments and ours are known, and in a hosted world, where the registry's are. A save from the
 * screen's path reaches the running server's rules; the test then saves the previous file back.
 */
public class ConfigScreenClientGameTest implements FabricClientGameTest {
	private static final String SHARPNESS = "minecraft:sharpness";
	/** In the GameTest datapack, so it only exists once a world is open. */
	private static final String THIRD_PARTY = "e2test:third_party";

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> {
			check(client.getSingleplayerServer() == null, "no world is open");
			Set<String> listed = ConfigEditing.enchantmentNames().keySet();
			check(listed.equals(ModEnchantments.knownWithoutRegistry()),
					"the title screen lists vanilla's enchantments and ours: " + listed);
			Mode mode = ConfigEditing.currentEnchantmentsView().mode();
			check(mode == Mode.EDIT_FILE, "the title screen edits enchantments.json5: " + mode);
		});
		openAndClose(context, "config-screen-title");

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getClientWorld().waitForChunksRender();
			context.runOnClient(client -> {
				Set<String> listed = ConfigEditing.enchantmentNames().keySet();
				check(listed.contains(THIRD_PARTY) && listed.containsAll(ModEnchantments.knownWithoutRegistry()),
						"a hosted world lists the registry's enchantments: " + listed);
				Mode mode = ConfigEditing.currentEnchantmentsView().mode();
				check(mode == Mode.EDIT_HOSTED, "the host edits enchantments.json5: " + mode);
			});
			openAndClose(context, "config-screen-hosted");

			EnchantmentOverrides before = world.getServer().computeOnServer(ServerConfigs::enchantments);
			// Flip whatever the run dir's file says, so a file left over from an earlier run can't hide a failure.
			boolean wasAllowed = new AcquisitionRules(before).allows(SHARPNESS, Source.TRADE);
			EnchantmentFlagsDraft draft = new EnchantmentFlagsDraft(before, Set.of(SHARPNESS));
			draft.set(SHARPNESS, EnchantmentFlagsDraft.Flag.TRADE, !wasAllowed);
			EnchantmentOverrides edited = draft.apply();
			context.runOnClient(client -> ConfigEditing.saveEnchantments(edited));
			// The save queues the server's re-read with server.execute, which only runs when the server ticks.
			context.waitTicks(2);
			boolean nowAllowed = world.getServer().computeOnServer(server ->
					new AcquisitionRules(ServerConfigs.enchantments(server)).allows(SHARPNESS, Source.TRADE));
			check(nowAllowed == !wasAllowed, "the saved trade: " + !wasAllowed + " reached the running server");

			context.runOnClient(client -> ConfigEditing.saveEnchantments(before));
			context.waitTicks(2);
			EnchantmentOverrides restored = world.getServer().computeOnServer(ServerConfigs::enchantments);
			check(restored.equals(before), "the previous enchantments.json5 is back: " + restored);
		}
	}

	/** Builds the screen the way Mod Menu does, shows it for a frame, screenshots it and closes it. */
	private static void openAndClose(ClientGameTestContext context, String screenshot) {
		context.runOnClient(client -> client.setScreen(
				new ModMenuIntegration().getModConfigScreenFactory().create(client.screen)));
		context.waitTick();
		Screen shown = context.computeOnClient(client -> client.screen);
		check(shown != null && shown.getClass().getName().contains("yacl"), "the config screen is open: " + shown);
		context.takeScreenshot(screenshot);
		context.runOnClient(client -> client.screen.onClose());
		context.waitTick();
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError(what);
		}
	}
}
