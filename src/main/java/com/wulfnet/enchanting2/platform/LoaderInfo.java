package com.wulfnet.enchanting2.platform;

import java.nio.file.Path;
import java.util.Optional;

/** What the running mod loader knows about the game and its mods. */
public interface LoaderInfo {
	/** The {@code config/} directory every mod writes into. */
	Path configDir();

	/** The game (or server) directory. */
	Path gameDir();

	boolean isModLoaded(String modId);

	/** The mod's version as its metadata states it; empty when no such mod is loaded. */
	Optional<String> modVersion(String modId);
}
