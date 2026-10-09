package com.wulfnet.enchanting2.mixin;

import com.wulfnet.enchanting2.acquisition.AcquisitionHost;
import net.minecraft.core.MappedRegistry;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Every registry gets the field; only the enchantment registry is ever stamped ({@code Acquisition.register}). */
@Mixin(MappedRegistry.class)
abstract class MappedRegistryMixin implements AcquisitionHost {
	// Volatile: worldgen threads roll mob spawn gear while the server thread stamps or clears it.
	@Unique
	private volatile MinecraftServer enchanting2$acquisitionServer;

	@Override
	public MinecraftServer enchanting2$acquisitionServer() {
		return enchanting2$acquisitionServer;
	}

	@Override
	public void enchanting2$setAcquisitionServer(MinecraftServer server) {
		this.enchanting2$acquisitionServer = server;
	}
}
