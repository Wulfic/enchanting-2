package com.wulfnet.enchanting2.platform.forge;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.platform.Registration;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.RegistryObject;

/**
 * Built-in registry entries through {@code RegisterEvent}. Forge has no holder that binds later (NeoForge's
 * {@code DeferredHolder}); its {@link RegistryObject} gives the registry's own holder once the event has run.
 */
public final class ForgeRegistration implements Registration {
	@Override
	public <T> Supplier<Holder<T>> register(Registry<T> registry, Identifier id, T value) {
		RegisterEvent.getBus(ForgeBus.mod()).addListener(event -> event.register(registry.key(), id, () -> value));
		RegistryObject<T> entry = RegistryObject.create(id, registry.key(), Enchanting2.MOD_ID);
		return () -> entry.getHolder().orElseThrow(() -> new IllegalStateException(
				"registry entry " + id + " in " + registry.key().identifier() + " is used before Forge registered it"));
	}
}
