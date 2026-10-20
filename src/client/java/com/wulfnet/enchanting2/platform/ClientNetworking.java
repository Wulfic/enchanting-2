package com.wulfnet.enchanting2.platform;

import java.util.function.Consumer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client side of our payloads (registered by {@link Networking}). */
public interface ClientNetworking {
	/** Handles a payload the server sends in play. */
	<T extends CustomPacketPayload> void onReceive(CustomPacketPayload.Type<T> type, Consumer<T> handler);

	/** Handles a payload the server sends in the configuration phase. */
	<T extends CustomPacketPayload> void onReceiveInConfiguration(CustomPacketPayload.Type<T> type,
			Consumer<T> handler);

	/** A new connection entering its configuration phase (not the hand-off from configuration to play). */
	void onConfigurationStart(Runnable listener);

	/** Leaving a world or server during play. */
	void onDisconnect(Runnable listener);

	/** Whether the server accepts the payload. */
	boolean canSend(CustomPacketPayload.Type<?> type);

	void send(CustomPacketPayload payload);
}
