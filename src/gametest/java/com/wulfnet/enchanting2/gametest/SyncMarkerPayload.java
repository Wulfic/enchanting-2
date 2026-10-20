package com.wulfnet.enchanting2.gametest;

import io.netty.buffer.ByteBuf;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Both directions, GameTest mod only: a numbered marker for {@link Delivery}. One connection handles its packets in
 * the order they were sent, so the far side handling marker {@code n} means it has handled everything sent before it.
 * The server sends an {@code echo} marker straight back to its sender.
 */
public record SyncMarkerPayload(int sequence, boolean echo) implements CustomPacketPayload {
	public static final Type<SyncMarkerPayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath("enchanting2-gametest", "sync_marker"));
	public static final StreamCodec<ByteBuf, SyncMarkerPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SyncMarkerPayload::sequence,
			ByteBufCodecs.BOOL, SyncMarkerPayload::echo,
			SyncMarkerPayload::new);

	/** The highest marker the server has handled. */
	static final AtomicInteger SERVER_SEEN = new AtomicInteger();

	@Override
	public Type<SyncMarkerPayload> type() {
		return TYPE;
	}

	/** The GameTest mod's {@code main} entrypoint: the type both ways, and the server's receiver. */
	public static void register() {
		PayloadTypeRegistry.playC2S().register(TYPE, CODEC);
		PayloadTypeRegistry.playS2C().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> {
			SERVER_SEEN.accumulateAndGet(payload.sequence(), Math::max);
			if (payload.echo()) {
				ServerPlayNetworking.send(context.player(), new SyncMarkerPayload(payload.sequence(), false));
			}
		});
	}
}
