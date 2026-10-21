package com.wulfnet.enchanting2.platform.forge;

import com.wulfnet.enchanting2.Enchanting2;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.packs.repository.KnownPack;

/**
 * Forge builds every mod's data pack ({@code mod:<modid>}) with no known-pack identity, and vanilla loads registry
 * entries from such a pack as {@code Lifecycle.experimental()}: every new world then opens behind the "Experimental
 * settings" warning (TODO.md 13G, decisions.md "Forge mod packs get a KnownPack"). NeoForge names each mod pack
 * {@code KnownPack("neoforge", "mod/<modid>", version)}; this does the same for the packs claimed here, which
 * {@code PackLocationInfoMixin} reports. A Forge client's known packs are vanilla's only, so a server still sends these
 * entries in full.
 */
public final class ForgeKnownPacks {
	private static final Map<String, KnownPack> CLAIMED = new ConcurrentHashMap<>();

	private ForgeKnownPacks() {
	}

	/** Names the data pack of {@code modId} (Forge's pack id {@code mod:<modId>}). */
	public static void claim(String modId, String version) {
		String packId = "mod:" + modId;
		CLAIMED.put(packId, new KnownPack("forge", packId, version));
		Enchanting2.LOGGER.info("Forge data pack {} named as known pack forge:{}:{}", packId, packId, version);
	}

	/** The identity claimed for the pack with this id, if any. */
	public static Optional<KnownPack> claimed(String packId) {
		return Optional.ofNullable(CLAIMED.get(packId));
	}
}
