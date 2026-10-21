package com.wulfnet.enchanting2.platform;

import io.netty.buffer.ByteBuf;
import java.util.function.Consumer;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client side of our payloads (registered by {@link Networking}). */
public interface ClientNetworking {
	/**
	 * Handles a payload the server sends in play, and with {@code configuration} also in the configuration phase (as
	 * {@code Networking.registerToClient} registered it). One handler for both: NeoForge allows one per type.
	 * {@code codec} is the one {@code registerToClient} was given: Forge takes a payload's codec and its receiving
	 * side's handler in one registration (Phase 13D); Fabric and NeoForge already have it and ignore it here.
	 */
	<T extends CustomPacketPayload> void onReceive(CustomPacketPayload.Type<T> type, StreamCodec<ByteBuf, T> codec,
			boolean configuration, Consumer<T> handler);

	/** A new connection entering its configuration phase (not the hand-off from configuration to play). */
	void onConfigurationStart(Runnable listener);

	/** Leaving a world or server during play. */
	void onDisconnect(Runnable listener);

	/** Whether the server accepts the payload. */
	boolean canSend(CustomPacketPayload.Type<?> type);

	void send(CustomPacketPayload payload);
}
