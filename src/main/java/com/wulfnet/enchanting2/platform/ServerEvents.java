package com.wulfnet.enchanting2.platform;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;

/** Server lifecycle, tick, entity and loot events. Listeners run on the server thread. */
public interface ServerEvents {
	/** Before the worlds load. */
	void onServerStarting(Consumer<MinecraftServer> listener);

	void onServerStopped(Consumer<MinecraftServer> listener);

	/** After {@code /reload} finishes; {@code false} when it failed and the previous data stays in use. */
	void onDataPackReloaded(BiConsumer<MinecraftServer, Boolean> listener);

	/** At the end of every server tick. */
	void onServerTickEnd(Consumer<MinecraftServer> listener);

	/** An entity added to a server level, loaded from disk or newly spawned. */
	void onEntityLoad(BiConsumer<Entity, ServerLevel> listener);

	/** A player entity replaced by a new one: respawn after death ({@code alive} false) or leaving the End (true). */
	void onPlayerCopy(PlayerCopy listener);

	/** Every loot table roll, after the table's own functions ran. The listener may change {@code drops}. */
	void onModifyDrops(DropsModifier listener);

	@FunctionalInterface
	interface PlayerCopy {
		void copy(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive);
	}

	@FunctionalInterface
	interface DropsModifier {
		void modify(Holder<LootTable> table, LootContext context, List<ItemStack> drops);
	}
}
