package com.wulfnet.enchanting2.platform.fabric;

import com.wulfnet.enchanting2.platform.Registration;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

public final class FabricRegistration implements Registration {
	@Override
	public <T> Supplier<Holder<T>> register(Registry<T> registry, Identifier id, T value) {
		Holder<T> holder = Registry.registerForHolder(registry, id, value);
		return () -> holder;
	}
}
