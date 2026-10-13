package com.wulfnet.enchanting2;

import com.wulfnet.enchanting2.anvil.AnvilBridge;
import com.wulfnet.enchanting2.client.AnvilPreviewState;
import com.wulfnet.enchanting2.client.ClientConfigState;
import com.wulfnet.enchanting2.client.ClientSettings;
import com.wulfnet.enchanting2.client.EnchantmentDescriptions;
import com.wulfnet.enchanting2.client.GrindstoneOptionsState;
import com.wulfnet.enchanting2.client.LastStandHud;
import com.wulfnet.enchanting2.client.TablePreviewState;
import com.wulfnet.enchanting2.config.CommonConfig;
import com.wulfnet.enchanting2.experience.ExperienceCurve;
import com.wulfnet.enchanting2.experience.PlayerExperience;
import com.wulfnet.enchanting2.grindstone.GrindstoneBridge;
import com.wulfnet.enchanting2.table.TableBridge;
import net.fabricmc.api.ClientModInitializer;

public final class Enchanting2Client implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientSettings.load();
		ClientConfigState.register();
		AnvilPreviewState.register();
		GrindstoneOptionsState.register();
		TablePreviewState.register();
		EnchantmentDescriptions.register();
		LastStandHud.register();
		// No synced copy means a vanilla server (or not connected): draw the vanilla curve.
		PlayerExperience.setClientSource(() -> ClientConfigState.serverCopy()
				.map(CommonConfig::experience)
				.orElse(ExperienceCurve.VANILLA));
		// Same signal: a synced config means the server runs our anvil, so the client mustn't compute its own result.
		AnvilBridge.setClientSource(() -> ClientConfigState.serverCopy().isPresent());
		GrindstoneBridge.setClientSource(() -> ClientConfigState.serverCopy().isPresent());
		TableBridge.setClientSource(() -> ClientConfigState.serverCopy().isPresent());
		Enchanting2.LOGGER.info("Enchanting 2 client initialized");
	}
}
