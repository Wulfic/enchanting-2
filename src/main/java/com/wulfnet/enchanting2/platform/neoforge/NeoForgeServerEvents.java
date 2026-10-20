package com.wulfnet.enchanting2.platform.neoforge;

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
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class NeoForgeServerEvents implements ServerEvents {
	/** Read by {@link DropsLootModifier}, which runs them for every loot roll. */
	static final List<DropsModifier> DROPS = new CopyOnWriteArrayList<>();

	private final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> lootModifiers =
			DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Enchanting2.MOD_ID);

	public NeoForgeServerEvents() {
		// The codec of the one modifier data/enchanting2/loot_modifiers/drops.json names (src/neoforge/resources).
		lootModifiers.register(DropsLootModifier.NAME, () -> DropsLootModifier.CODEC);
		lootModifiers.register(NeoForgeBus.mod());
	}

	@Override
	public void onServerStarting(Consumer<MinecraftServer> listener) {
		// Before the worlds load, like Fabric's SERVER_STARTING (NeoForge's ServerStartingEvent comes after them).
		NeoForge.EVENT_BUS.addListener(ServerAboutToStartEvent.class, event -> listener.accept(event.getServer()));
	}

	@Override
	public void onServerStopped(Consumer<MinecraftServer> listener) {
		NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> listener.accept(event.getServer()));
	}

	@Override
	public void onDataPackReloaded(BiConsumer<MinecraftServer, Boolean> listener) {
		// NeoForge fires this with no player once a /reload has succeeded, and per player at join (skipped here). A
		// failed /reload fires nothing; vanilla logs it and keeps the old data, so the listener only ever sees true.
		NeoForge.EVENT_BUS.addListener(OnDatapackSyncEvent.class, event -> {
			if (event.getPlayer() == null) {
				listener.accept(event.getPlayerList().getServer(), true);
			}
		});
	}

	@Override
	public void onServerTickEnd(Consumer<MinecraftServer> listener) {
		NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> listener.accept(event.getServer()));
	}

	@Override
	public void onEntityLoad(BiConsumer<net.minecraft.world.entity.Entity, ServerLevel> listener) {
		// Spawned or loaded from disk, as Fabric's ENTITY_LOAD; NeoForge also fires it on the client, skipped here.
		NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, event -> {
			if (event.getLevel() instanceof ServerLevel level) {
				listener.accept(event.getEntity(), level);
			}
		});
	}

	@Override
	public void onPlayerCopy(PlayerCopy listener) {
		NeoForge.EVENT_BUS.addListener(PlayerEvent.Clone.class, event -> {
			if (event.getOriginal() instanceof ServerPlayer oldPlayer && event.getEntity() instanceof ServerPlayer newPlayer) {
				listener.copy(oldPlayer, newPlayer, !event.isWasDeath());
			}
		});
	}

	@Override
	public void onModifyDrops(DropsModifier listener) {
		DROPS.add(listener);
	}
}
