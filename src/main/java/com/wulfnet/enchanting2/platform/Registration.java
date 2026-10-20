package com.wulfnet.enchanting2.platform;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

/**
 * Entries in Minecraft's built-in registries. Fabric lets mod init register directly; NeoForge has frozen them by then
 * and takes entries only in its registry event (Phase 13C). Call during mod init.
 */
public interface Registration {
	/**
	 * Registers {@code value} under {@code id} and returns its holder. The value itself is usable at once; the holder
	 * is bound before any world loads (Fabric: at once; NeoForge: when the registry event runs).
	 */
	<T> Holder<T> register(Registry<T> registry, Identifier id, T value);
}
