package com.wulfnet.enchanting2.platform.forge;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.network.NetworkProtocol;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Forge only: one {@code ownerSync} attachment's value for the receiving player's own entity, what Fabric's and
 * NeoForge's attachment sync send (Forge has none). {@code value} is the attachment's own stream codec output, encoded
 * by {@link ForgeAttachments} with the player's registries; empty means the value was removed.
 */
public record AttachmentSyncPayload(Identifier attachment, Optional<byte[]> value) implements CustomPacketPayload {
	/** Bytes in one value. Far above any real one (Last Stand's is one VarLong). */
	static final int MAX_VALUE_BYTES = 4096;

	public static final Type<AttachmentSyncPayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath(Enchanting2.MOD_ID, NetworkProtocol.path("attachment_sync")));

	public static final StreamCodec<ByteBuf, AttachmentSyncPayload> CODEC = StreamCodec.composite(
			Identifier.STREAM_CODEC, AttachmentSyncPayload::attachment,
			ByteBufCodecs.optional(ByteBufCodecs.byteArray(MAX_VALUE_BYTES)), AttachmentSyncPayload::value,
			AttachmentSyncPayload::new);

	@Override
	public Type<AttachmentSyncPayload> type() {
		return TYPE;
	}
}
