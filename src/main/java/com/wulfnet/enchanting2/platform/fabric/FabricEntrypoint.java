package com.wulfnet.enchanting2.platform.fabric;

import com.wulfnet.enchanting2.Enchanting2;
import net.fabricmc.api.ModInitializer;

/** fabric.mod.json {@code main}. */
public final class FabricEntrypoint implements ModInitializer {
	@Override
	public void onInitialize() {
		Enchanting2.init();
	}
}
