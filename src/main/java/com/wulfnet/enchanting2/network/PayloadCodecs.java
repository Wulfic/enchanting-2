package com.wulfnet.enchanting2.network;

import com.wulfnet.enchanting2.cost.CostBreakdown;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Codec pieces shared by our payloads. */
final class PayloadCodecs {
	/** Characters in an enchantment ID. */
	static final int MAX_ID_CHARS = 1024;

	static final StreamCodec<ByteBuf, String> ID = ByteBufCodecs.stringUtf8(MAX_ID_CHARS);

	/** Cost lines per breakdown. Far above any real operation (a few lines per enchantment). */
	static final int MAX_LINES = 1024;

	private static final StreamCodec<ByteBuf, CostBreakdown.Line> LINE = StreamCodec.composite(
			ordinal(CostBreakdown.Kind.class), CostBreakdown.Line::kind,
			ID, CostBreakdown.Line::id,
			ByteBufCodecs.VAR_INT, CostBreakdown.Line::from,
			ByteBufCodecs.VAR_INT, CostBreakdown.Line::to,
			ByteBufCodecs.VAR_LONG, CostBreakdown.Line::amount,
			CostBreakdown.Line::new);

	/**
	 * Decoding runs {@code CostBreakdown}'s checks, so totals that don't match their lines fail to decode. Reordering
	 * {@link CostBreakdown.Kind} changes the wire format.
	 */
	static final StreamCodec<ByteBuf, CostBreakdown> BREAKDOWN = StreamCodec.composite(
			LINE.apply(ByteBufCodecs.list(MAX_LINES)), CostBreakdown::lines,
			ByteBufCodecs.VAR_INT, CostBreakdown::enchantCost,
			ByteBufCodecs.VAR_INT, CostBreakdown::total,
			ByteBufCodecs.VAR_INT, CostBreakdown::lapis,
			ByteBufCodecs.BOOL, CostBreakdown::free,
			ByteBufCodecs.BOOL, CostBreakdown::tooExpensive,
			CostBreakdown::new);

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

	/** Throws for a breakdown {@link #BREAKDOWN} would refuse, so the caller sees it before Netty does. */
	static void checkBreakdown(CostBreakdown breakdown) {
		if (breakdown.lines().size() > MAX_LINES) {
			throw new IllegalArgumentException(breakdown.lines().size() + " cost lines, max " + MAX_LINES);
		}
		breakdown.lines().forEach(line -> checkId(line.id()));
	}

	/** Throws for an ID the codec would refuse, so an oversize payload fails where it's built, not inside Netty. */
	static void checkId(String id) {
		if (id.length() > MAX_ID_CHARS) {
			throw new IllegalArgumentException("ID of " + id.length() + " characters, max " + MAX_ID_CHARS);
		}
	}
}
