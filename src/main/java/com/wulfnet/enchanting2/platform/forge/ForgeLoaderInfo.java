package com.wulfnet.enchanting2.platform.forge;

import com.wulfnet.enchanting2.platform.LoaderInfo;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

public final class ForgeLoaderInfo implements LoaderInfo {
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
		return ModList.isLoaded(modId);
	}

	@Override
	public Optional<String> modVersion(String modId) {
		return ModList.getModContainerById(modId).map(c -> c.getModInfo().getVersion().toString());
	}
}
