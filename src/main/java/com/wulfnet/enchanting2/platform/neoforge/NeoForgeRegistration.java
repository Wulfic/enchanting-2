package com.wulfnet.enchanting2.platform.neoforge;

import com.wulfnet.enchanting2.platform.Registration;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Built-in registry entries through {@code RegisterEvent}; the returned {@link DeferredHolder} binds once it has run. */
public final class NeoForgeRegistration implements Registration {
	@Override
	public <T> Holder<T> register(Registry<T> registry, Identifier id, T value) {
		NeoForgeBus.mod().addListener(RegisterEvent.class, event -> event.register(registry.key(), id, () -> value));
		return DeferredHolder.create(registry.key(), id);
	}
}
