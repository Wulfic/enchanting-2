package com.wulfnet.enchanting2.platform.neoforge;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.Enchanting2Client;
import com.wulfnet.enchanting2.client.config.FormScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** NeoForge's client entry point. FML builds it after {@link NeoForgeEntrypoint}, so common init has run. */
@Mod(value = Enchanting2.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeClientEntrypoint {
	public NeoForgeClientEntrypoint(ModContainer container) {
		Enchanting2Client.init();
		// The mod list's Config button opens our screen (Phase 13E), the one Fabric shows without YACL.
		container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new FormScreen(parent));
	}
}
