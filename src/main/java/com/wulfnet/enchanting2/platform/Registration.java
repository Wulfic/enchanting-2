package com.wulfnet.enchanting2.platform;

import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

/**
 * Entries in Minecraft's built-in registries. Fabric lets mod init register directly; NeoForge and Forge have frozen
 * them by then and take entries only in their registry event (Phase 13C/13D). Call during mod init.
 */
public interface Registration {
	/**
	 * Registers {@code value} under {@code id}. The value itself is usable at once; the returned supplier gives the
	 * registry's own holder once the entry exists, which is before any world loads (Fabric: at once; NeoForge and
	 * Forge: when the registry event has run). A supplier, not a holder: Forge hands out the registry's holder only
	 * after its event, and a stand-in holder wouldn't equal the one an effect decoded from a save carries.
	 */
	<T> Supplier<Holder<T>> register(Registry<T> registry, Identifier id, T value);
}
