package com.wulfnet.enchanting2.client;

import blue.endless.jankson.api.SyntaxError;
import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.config.CommonConfig;
import com.wulfnet.enchanting2.config.ConfigFiles;
import com.wulfnet.enchanting2.network.ConfigSyncPayload;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The connected server's config, kept apart from this client's own file (DESIGN §2a). Cleared when a new connection
 * starts and when play ends, so one server's settings never leak into the next. A client has one connection at a
 * time, so one field is enough.
 */
public final class ClientConfigState {
	private static final Logger LOG = LoggerFactory.getLogger(Enchanting2.MOD_ID);
	private static volatile CommonConfig serverCopy;

	private ClientConfigState() {
	}

	public static void register() {
		// Registering these is also what tells the server this client has Enchanting 2 (NetworkProtocol).
		ClientConfigurationNetworking.registerGlobalReceiver(ConfigSyncPayload.TYPE,
				(payload, context) -> accept(payload));
		ClientPlayNetworking.registerGlobalReceiver(ConfigSyncPayload.TYPE, (payload, context) -> accept(payload));
		// Not the configuration DISCONNECT event: it may also fire on the hand-off to play, which would drop the copy.
		ClientConfigurationConnectionEvents.INIT.register((handler, client) -> clear());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
	}

	/** Empty when not connected, or connected to a server that never sent one (a vanilla server). */
	public static Optional<CommonConfig> serverCopy() {
		return Optional.ofNullable(serverCopy);
	}

	static void accept(ConfigSyncPayload payload) {
		try {
			serverCopy = ConfigFiles.parseSynced(payload.json(), CommonConfig.CODEC);
			LOG.debug("Received the server's Enchanting 2 config");
		} catch (SyntaxError e) {
			LOG.warn("The server sent an Enchanting 2 config that doesn't parse ({}); keeping the previous copy",
					e.getMessage());
		}
	}

	private static void clear() {
		serverCopy = null;
	}
}
