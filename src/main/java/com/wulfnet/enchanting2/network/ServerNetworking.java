package com.wulfnet.enchanting2.network;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.config.ServerConfigs;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Payload registration, the join check, and config sync (DESIGN §2a). */
public final class ServerNetworking {
	private static final Logger LOG = LoggerFactory.getLogger(Enchanting2.MOD_ID);

	private ServerNetworking() {
	}

	/** Runs on both sides: payload types must be registered wherever they are sent or received. */
	public static void registerPayloads() {
		PayloadTypeRegistry.configurationS2C().register(ConfigSyncPayload.TYPE, ConfigSyncPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ConfigSyncPayload.TYPE, ConfigSyncPayload.CODEC);
	}

	public static void registerServer() {
		// Netty thread. Every client goes through configuration, the singleplayer host included.
		ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
			List<String> channels = ServerConfigurationNetworking.getSendable(handler).stream()
					.map(Identifier::toString).toList();
			NetworkProtocol.Verdict verdict = NetworkProtocol.classify(channels, ConfigSyncPayload.TYPE.id().toString());
			if (verdict.kind() == NetworkProtocol.Kind.MATCH) {
				ServerConfigurationNetworking.send(handler, ConfigSyncPayload.of(ServerConfigs.get(server)));
				return;
			}
			String message = NetworkProtocol.disconnectMessage(verdict, modVersion());
			LOG.info("Disconnecting {} at join ({}, client protocols {}): {}", handler.getOwner(), verdict.kind(),
					verdict.clientVersions(), message);
			handler.disconnect(Component.literal(message));
		});
		// Again at play join: a /reload while this player was still configuring missed them in syncToAll.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (ServerPlayNetworking.canSend(handler.player, ConfigSyncPayload.TYPE)) {
				ServerPlayNetworking.send(handler.player, ConfigSyncPayload.of(ServerConfigs.get(server)));
			}
		});
	}

	/** Sends the current config to every player whose client accepts it. Server thread. */
	public static void syncToAll(MinecraftServer server) {
		ConfigSyncPayload payload = ConfigSyncPayload.of(ServerConfigs.get(server));
		int sent = 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, ConfigSyncPayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
				sent++;
			} else {
				LOG.warn("{} can't receive the Enchanting 2 config; their client keeps the previous copy",
						player.getName().getString());
			}
		}
		LOG.debug("Sent the Enchanting 2 config to {} player(s)", sent);
	}

	private static String modVersion() {
		return FabricLoader.getInstance().getModContainer(Enchanting2.MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("(unknown version)");
	}
}
