package com.wulfnet.enchanting2.platform.fabric;

import com.wulfnet.enchanting2.platform.ServerEvents;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public final class FabricServerEvents implements ServerEvents {
	@Override
	public void onServerStarting(Consumer<MinecraftServer> listener) {
		ServerLifecycleEvents.SERVER_STARTING.register(listener::accept);
	}

	@Override
	public void onServerStopped(Consumer<MinecraftServer> listener) {
		ServerLifecycleEvents.SERVER_STOPPED.register(listener::accept);
	}

	@Override
	public void onDataPackReloaded(BiConsumer<MinecraftServer, Boolean> listener) {
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register(
				(server, resources, success) -> listener.accept(server, success));
	}

	@Override
	public void onServerTickEnd(Consumer<MinecraftServer> listener) {
		ServerTickEvents.END_SERVER_TICK.register(listener::accept);
	}

	@Override
	public void onEntityLoad(BiConsumer<Entity, ServerLevel> listener) {
		ServerEntityEvents.ENTITY_LOAD.register(listener::accept);
	}

	@Override
	public void onPlayerCopy(PlayerCopy listener) {
		ServerPlayerEvents.COPY_FROM.register(listener::copy);
	}

	@Override
	public void onModifyDrops(DropsModifier listener) {
		LootTableEvents.MODIFY_DROPS.register(listener::modify);
	}

	@Override
	public void onFluidPlacesBlock(FluidPlacement listener) {
		// Never fired here: generators place through vanilla's LiquidBlock and LavaFluid, which mixins hook directly.
	}
}
