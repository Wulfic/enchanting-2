package com.wulfnet.enchanting2.platform.neoforge;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.Enchanting2Client;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

/** NeoForge's client entry point. FML builds it after {@link NeoForgeEntrypoint}, so common init has run. */
@Mod(value = Enchanting2.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeClientEntrypoint {
	public NeoForgeClientEntrypoint() {
		Enchanting2Client.init();
	}
}
