package com.wulfnet.enchanting2.network;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Codec pieces shared by our payloads. */
final class PayloadCodecs {
	/** Characters in an enchantment ID. */
	static final int MAX_ID_CHARS = 1024;

	static final StreamCodec<ByteBuf, String> ID = ByteBufCodecs.stringUtf8(MAX_ID_CHARS);

	private PayloadCodecs() {
	}

	/**
	 * An enum as its ordinal, bounds-checked on decode. Reordering the enum changes the wire format and needs a
	 * {@link NetworkProtocol#VERSION} bump.
	 */
	static <E extends Enum<E>> StreamCodec<ByteBuf, E> ordinal(Class<E> type) {
		E[] values = type.getEnumConstants();
		return ByteBufCodecs.idMapper(i -> {
			if (i < 0 || i >= values.length) {
				throw new DecoderException(
						type.getSimpleName() + " ordinal " + i + " out of range 0.." + (values.length - 1));
			}
			return values[i];
		}, Enum::ordinal);
	}

	/** Throws for an ID the codec would refuse, so an oversize payload fails where it's built, not inside Netty. */
	static void checkId(String id) {
		if (id.length() > MAX_ID_CHARS) {
			throw new IllegalArgumentException("ID of " + id.length() + " characters, max " + MAX_ID_CHARS);
		}
	}
}
