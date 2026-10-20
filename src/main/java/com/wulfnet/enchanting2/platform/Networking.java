package com.wulfnet.enchanting2.platform;

import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Server side of our payloads. Registration runs on both sides, during mod init. */
public interface Networking {
	/** A payload the server sends in play; {@code configuration} also allows it in the configuration phase. */
	<T extends CustomPacketPayload> void registerToClient(CustomPacketPayload.Type<T> type, StreamCodec<ByteBuf, T> codec,
			boolean configuration);

	/**
	 * A payload the client sends in play, and its handler, which runs on the server thread. One call for both: NeoForge
	 * takes the handler at registration, and keeping the pair together keeps it typed.
	 */
	<T extends CustomPacketPayload> void registerToServer(CustomPacketPayload.Type<T> type,
			StreamCodec<ByteBuf, T> codec, BiConsumer<ServerPlayer, T> handler);

	/** Each client in its configuration phase, the singleplayer host included. Netty thread. */
	void onConfigure(Consumer<ConfigurationConnection> listener);

	/** A player entering play. Server thread. */
	void onPlayJoin(Consumer<ServerPlayer> listener);

	/** Whether this player's client accepts the payload. */
	boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type);

	void send(ServerPlayer player, CustomPacketPayload payload);

	/** A connection in its configuration phase. */
	interface ConfigurationConnection {
		MinecraftServer server();

		/**
		 * Channels the client said it can receive, as {@code namespace:path}. Fabric: every one. NeoForge: only those
		 * this server registered too (it keeps no others), which still tells a matching client from one without the mod.
		 */
		List<String> sendableChannels();

		void send(CustomPacketPayload payload);

		void disconnect(Component reason);

		/** Who is connecting, for log lines. */
		String describe();
	}
}
