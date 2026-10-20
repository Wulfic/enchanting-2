package com.wulfnet.enchanting2.platform.fabric;

import com.wulfnet.enchanting2.platform.LoaderInfo;
import java.nio.file.Path;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;

public final class FabricLoaderInfo implements LoaderInfo {
	@Override
	public Path configDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	@Override
	public Path gameDir() {
		return FabricLoader.getInstance().getGameDir();
	}

	@Override
	public boolean isModLoaded(String modId) {
		return FabricLoader.getInstance().isModLoaded(modId);
	}

	@Override
	public Optional<String> modVersion(String modId) {
		return FabricLoader.getInstance().getModContainer(modId)
				.map(c -> c.getMetadata().getVersion().getFriendlyString());
	}
}
