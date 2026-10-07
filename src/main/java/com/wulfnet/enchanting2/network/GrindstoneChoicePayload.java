package com.wulfnet.enchanting2.network;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.grindstone.GrindstoneChoice;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server: the row the player clicked in the grindstone panel of container {@code containerId}, or none to clear
 * it. Untrusted: the server applies it only to the player's open grindstone with that ID, and a choice that isn't a row
 * for the current inputs produces no result (DESIGN §6, decisions.md 2026-10-07 "Grindstone").
 */
public record GrindstoneChoicePayload(int containerId, Optional<GrindstoneChoice> choice)
		implements CustomPacketPayload {
	public static final Type<GrindstoneChoicePayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath(Enchanting2.MOD_ID, NetworkProtocol.path("grindstone_choice")));

	public static final StreamCodec<ByteBuf, GrindstoneChoicePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, GrindstoneChoicePayload::containerId,
			ByteBufCodecs.optional(GrindstonePayloadCodecs.CHOICE), GrindstoneChoicePayload::choice,
			GrindstoneChoicePayload::new);

	public GrindstoneChoicePayload {
		Objects.requireNonNull(choice, "choice");
		choice.ifPresent(GrindstonePayloadCodecs::checkIds);
	}

	@Override
	public Type<GrindstoneChoicePayload> type() {
		return TYPE;
	}
}
