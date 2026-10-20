package com.wulfnet.enchanting2.network;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.config.ServerConfigs;
import com.wulfnet.enchanting2.grindstone.GrindstoneMenuAccess;
import com.wulfnet.enchanting2.platform.Networking;
import com.wulfnet.enchanting2.platform.Services;
import com.wulfnet.enchanting2.table.TableMenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Payload registration, the join check, and config sync (DESIGN §2a). */
public final class ServerNetworking {
	private static final Logger LOG = LoggerFactory.getLogger(Enchanting2.MOD_ID);

	private ServerNetworking() {
	}

	/** Runs on both sides: payload types must be registered wherever they are sent or received. */
	public static void registerPayloads() {
		Networking net = Services.NETWORK;
		net.registerToClient(ConfigSyncPayload.TYPE, ConfigSyncPayload.CODEC, true);
		net.registerToClient(AnvilPreviewPayload.TYPE, AnvilPreviewPayload.CODEC, false);
		net.registerToClient(GrindstoneOptionsPayload.TYPE, GrindstoneOptionsPayload.CODEC, false);
		net.registerToServer(GrindstoneChoicePayload.TYPE, GrindstoneChoicePayload.CODEC);
		net.registerToClient(TablePreviewPayload.TYPE, TablePreviewPayload.CODEC, false);
		net.registerToServer(TableSelectPayload.TYPE, TableSelectPayload.CODEC);
		net.registerToServer(TableEnchantPayload.TYPE, TableEnchantPayload.CODEC);
	}

	/**
	 * Applies a grindstone pick to the player's open grindstone, if its container ID matches; anything else (closed,
	 * another menu, a stale ID, a forged packet) is ignored. Server thread.
	 *
	 * @return whether it was applied
	 */
	public static boolean handleGrindstoneChoice(ServerPlayer player, GrindstoneChoicePayload payload) {
		AbstractContainerMenu menu = player.containerMenu;
		if (!(menu instanceof GrindstoneMenuAccess grindstone) || menu.containerId != payload.containerId()) {
			LOG.debug("Ignoring a grindstone pick from {} for container {}: their open menu is {} ({})",
					player.getName().getString(), payload.containerId(), menu.getClass().getSimpleName(),
					menu.containerId);
			return false;
		}
		grindstone.enchanting2$choose(payload.choice());
		return true;
	}

	/** Stores a table selection on the player's open table, if its container ID matches. Server thread. */
	public static boolean handleTableSelect(ServerPlayer player, TableSelectPayload payload) {
		TableMenuAccess table = openTable(player, payload.containerId(), "selection");
		if (table == null) {
			return false;
		}
		table.enchanting2$select(payload.selection());
		return true;
	}

	/**
	 * The Enchant button on the player's open table, if its container ID matches. The menu enchants only when the
	 * payload's selection, levels and lapis equal what it would charge now. Server thread.
	 *
	 * @return whether the item was enchanted
	 */
	public static boolean handleTableEnchant(ServerPlayer player, TableEnchantPayload payload) {
		TableMenuAccess table = openTable(player, payload.containerId(), "enchant");
		return table != null && table.enchanting2$enchant(payload.selection(), payload.levels(), payload.lapis());
	}

	/** The player's open table with that container ID; null (logged) for anything else: closed, stale, forged. */
	private static @Nullable TableMenuAccess openTable(ServerPlayer player, int containerId, String what) {
		AbstractContainerMenu menu = player.containerMenu;
		if (!(menu instanceof TableMenuAccess table) || menu.containerId != containerId) {
			LOG.debug("Ignoring a table {} from {} for container {}: their open menu is {} ({})", what,
					player.getName().getString(), containerId, menu.getClass().getSimpleName(), menu.containerId);
			return null;
		}
		return table;
	}

	public static void registerServer() {
		Networking net = Services.NETWORK;
		// Netty thread. Every client goes through configuration, the singleplayer host included.
		net.onConfigure(connection -> {
			NetworkProtocol.Verdict verdict = NetworkProtocol.classify(connection.sendableChannels(),
					ConfigSyncPayload.TYPE.id().toString());
			if (verdict.kind() == NetworkProtocol.Kind.MATCH) {
				connection.send(ConfigSyncPayload.of(ServerConfigs.get(connection.server())));
				return;
			}
			String message = NetworkProtocol.disconnectMessage(verdict, modVersion());
			LOG.info("Disconnecting {} at join ({}, client protocols {}): {}", connection.describe(), verdict.kind(),
					verdict.clientVersions(), message);
			connection.disconnect(Component.literal(message));
		});
		// Server thread.
		net.onReceive(GrindstoneChoicePayload.TYPE, ServerNetworking::handleGrindstoneChoice);
		net.onReceive(TableSelectPayload.TYPE, ServerNetworking::handleTableSelect);
		net.onReceive(TableEnchantPayload.TYPE, ServerNetworking::handleTableEnchant);
		// Again at play join: a /reload while this player was still configuring missed them in syncToAll.
		net.onPlayJoin(player -> {
			if (net.canSend(player, ConfigSyncPayload.TYPE)) {
				net.send(player, ConfigSyncPayload.of(ServerConfigs.get(player.level().getServer())));
			}
		});
	}

	/** Sends the current config to every player whose client accepts it. Server thread. */
	public static void syncToAll(MinecraftServer server) {
		ConfigSyncPayload payload = ConfigSyncPayload.of(ServerConfigs.get(server));
		int sent = 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (Services.NETWORK.canSend(player, ConfigSyncPayload.TYPE)) {
				Services.NETWORK.send(player, payload);
				sent++;
			} else {
				LOG.warn("{} can't receive the Enchanting 2 config; their client keeps the previous copy",
						player.getName().getString());
			}
		}
		LOG.debug("Sent the Enchanting 2 config to {} player(s)", sent);
	}

	private static String modVersion() {
		return Services.LOADER.modVersion(Enchanting2.MOD_ID).orElse("(unknown version)");
	}
}
