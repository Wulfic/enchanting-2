package com.wulfnet.enchanting2.network;

import com.wulfnet.enchanting2.grindstone.GrindstoneAction;
import com.wulfnet.enchanting2.grindstone.GrindstoneChoice;
import com.wulfnet.enchanting2.grindstone.GrindstoneOption;
import com.wulfnet.enchanting2.grindstone.GrindstoneOutcome.Status;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Wire shapes for the grindstone payloads. Decoding runs the records' own checks, rethrown as a
 * {@link DecoderException}, so a malformed choice (Extract with no enchantment, a row status on the whole outcome) fails
 * to decode instead of reaching the menu.
 */
final class GrindstonePayloadCodecs {
	static final StreamCodec<ByteBuf, GrindstoneChoice> CHOICE = StreamCodec.composite(
			PayloadCodecs.ordinal(GrindstoneAction.class), GrindstoneChoice::action,
			ByteBufCodecs.optional(PayloadCodecs.ID), GrindstoneChoice::enchantment,
			GrindstonePayloadCodecs::choice);

	static final StreamCodec<ByteBuf, GrindstoneOption> OPTION = StreamCodec.composite(
			CHOICE, GrindstoneOption::choice,
			ByteBufCodecs.VAR_INT, GrindstoneOption::level,
			ByteBufCodecs.VAR_INT, GrindstoneOption::cost,
			ByteBufCodecs.BOOL, GrindstoneOption::free,
			PayloadCodecs.ordinal(Status.class), GrindstoneOption::status,
			GrindstonePayloadCodecs::option);

	private GrindstonePayloadCodecs() {
	}

	private static GrindstoneChoice choice(GrindstoneAction action, Optional<String> enchantment) {
		try {
			return new GrindstoneChoice(action, enchantment);
		} catch (IllegalArgumentException e) {
			throw new DecoderException("bad grindstone choice: " + e.getMessage(), e);
		}
	}

	private static GrindstoneOption option(GrindstoneChoice choice, int level, int cost, boolean free, Status status) {
		try {
			return new GrindstoneOption(choice, level, cost, free, status);
		} catch (IllegalArgumentException e) {
			throw new DecoderException("bad grindstone row: " + e.getMessage(), e);
		}
	}

	static void checkIds(GrindstoneChoice choice) {
		choice.enchantment().ifPresent(PayloadCodecs::checkId);
	}
}
