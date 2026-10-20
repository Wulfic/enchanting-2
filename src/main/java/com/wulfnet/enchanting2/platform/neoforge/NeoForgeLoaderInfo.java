package com.wulfnet.enchanting2.platform.neoforge;

import com.wulfnet.enchanting2.platform.LoaderInfo;
import java.nio.file.Path;
import java.util.Optional;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

public final class NeoForgeLoaderInfo implements LoaderInfo {
	@Override
	public Path configDir() {
		return FMLPaths.CONFIGDIR.get();
	}

	@Override
	public Path gameDir() {
		return FMLPaths.GAMEDIR.get();
	}

	@Override
	public boolean isModLoaded(String modId) {
		return ModList.get().isLoaded(modId);
	}

	@Override
	public Optional<String> modVersion(String modId) {
		return ModList.get().getModContainerById(modId).map(c -> c.getModInfo().getVersion().toString());
	}
}
