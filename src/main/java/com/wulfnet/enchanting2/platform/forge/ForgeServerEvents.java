package com.wulfnet.enchanting2.platform.forge;

import com.mojang.serialization.MapCodec;
import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.platform.ServerEvents;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.listener.Priority;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public final class ForgeServerEvents implements ServerEvents {
	/** Read by {@link DropsLootModifier}, which runs them for every loot roll. */
	static final List<DropsModifier> DROPS = new CopyOnWriteArrayList<>();

	private final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> lootModifiers =
			DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Enchanting2.MOD_ID);

	public ForgeServerEvents() {
		// The codec of the one modifier data/enchanting2/loot_modifiers/drops.json names (src/forge/resources).
		lootModifiers.register(DropsLootModifier.NAME, () -> DropsLootModifier.CODEC);
		lootModifiers.register(ForgeBus.mod());
	}

	@Override
	public void onServerStarting(Consumer<MinecraftServer> listener) {
		// Before the worlds load, like Fabric's SERVER_STARTING (Forge's ServerStartingEvent comes after them).
		ServerAboutToStartEvent.BUS.addListener(event -> listener.accept(event.getServer()));
	}

	@Override
	public void onServerStopped(Consumer<MinecraftServer> listener) {
		ServerStoppedEvent.BUS.addListener(event -> listener.accept(event.getServer()));
	}

	@Override
	public void onDataPackReloaded(BiConsumer<MinecraftServer, Boolean> listener) {
		// As on NeoForge: fired with no player once a /reload has succeeded, and per player at join (skipped here). A
		// failed /reload fires nothing; vanilla logs it and keeps the old data, so the listener only ever sees true.
		OnDatapackSyncEvent.BUS.addListener(event -> {
			if (event.getPlayer() == null) {
				listener.accept(event.getPlayerList().getServer(), true);
			}
		});
	}

	@Override
	public void onServerTickEnd(Consumer<MinecraftServer> listener) {
		TickEvent.ServerTickEvent.Post.BUS.addListener(event -> listener.accept(event.server()));
	}

	@Override
	public void onEntityLoad(BiConsumer<Entity, ServerLevel> listener) {
		// Spawned or loaded from disk, as Fabric's ENTITY_LOAD; Forge also fires it on the client, skipped here.
		EntityJoinLevelEvent.BUS.addListener(event -> {
			if (event.getLevel() instanceof ServerLevel level) {
				listener.accept(event.getEntity(), level);
			}
		});
	}

	@Override
	public void onPlayerCopy(PlayerCopy listener) {
		PlayerEvent.Clone.BUS.addListener(event -> {
			if (event.getOriginal() instanceof ServerPlayer oldPlayer && event.getEntity() instanceof ServerPlayer newPlayer) {
				listener.copy(oldPlayer, newPlayer, !event.isWasDeath());
			}
		});
	}

	@Override
	public void onModifyDrops(DropsModifier listener) {
		DROPS.add(listener);
	}

	@Override
	public void onFluidPlacesBlock(FluidPlacement listener) {
		// Fired just before the block is set, with whatever state earlier listeners left; lowest priority sees the last.
		// Typed: a bare lambda also fits the cancellable bus's Predicate overload.
		Consumer<BlockEvent.FluidPlaceBlockEvent> forward = event -> {
			if (event.getLevel() instanceof ServerLevel level) {
				listener.place(level, event.getPos(), event.getNewState());
			}
		};
		BlockEvent.FluidPlaceBlockEvent.BUS.addListener(Priority.LOWEST, forward);
	}
}
