package com.wulfnet.enchanting2.platform.fabric;

import com.wulfnet.enchanting2.platform.Registration;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

public final class FabricRegistration implements Registration {
	@Override
	public <T> Holder<T> register(Registry<T> registry, Identifier id, T value) {
		return Registry.registerForHolder(registry, id, value);
	}
}
