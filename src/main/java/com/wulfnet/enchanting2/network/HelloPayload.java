package com.wulfnet.enchanting2.network;

import com.wulfnet.enchanting2.Enchanting2;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Both ways in the configuration phase, on loaders that only report the channels both sides share (NeoForge): the
 * sender's {@link NetworkProtocol#VERSION}, so a client on another protocol is told "version mismatch" rather than
 * "install the mod" (decisions.md 2026-10-20 "third round").
 * <p>
 * <b>Frozen forever:</b> the ID has no {@code v<N>/} prefix and the wire format is one VarInt. Every future build must
 * still read an old client's hello, or the gap this closes reopens. Never bump, rename or extend it.
 */
public record HelloPayload(int protocol) implements CustomPacketPayload {
	public static final Type<HelloPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Enchanting2.MOD_ID, "hello"));

	public static final StreamCodec<ByteBuf, HelloPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, HelloPayload::protocol,
			HelloPayload::new);

	/** This build's hello. */
	public static HelloPayload ours() {
		return new HelloPayload(NetworkProtocol.VERSION);
	}

	@Override
	public Type<HelloPayload> type() {
		return TYPE;
	}
}
