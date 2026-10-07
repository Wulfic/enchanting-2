package com.wulfnet.enchanting2.network;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.grindstone.GrindstoneChoice;
import com.wulfnet.enchanting2.grindstone.GrindstoneOption;
import com.wulfnet.enchanting2.grindstone.GrindstoneOutcome;
import com.wulfnet.enchanting2.grindstone.GrindstoneOutcome.Status;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: the grindstone panel for container {@code containerId}: every row, the player's pick as the server
 * holds it, and the outcome's status and charge (DESIGN §6). The server prices everything; the client only shows it.
 *
 * <p>Enums travel as ordinals. Reordering {@link Status} or {@code GrindstoneAction} changes the wire format and needs
 * a {@link NetworkProtocol#VERSION} bump.
 */
public record GrindstoneOptionsPayload(int containerId, Status status, List<GrindstoneOption> options,
		Optional<GrindstoneChoice> choice, int levelsCharged) implements CustomPacketPayload {
	/** Rows: one per enchantment on the source plus Disenchant, far below this. */
	public static final int MAX_OPTIONS = 1024;

	public static final Type<GrindstoneOptionsPayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath(Enchanting2.MOD_ID, NetworkProtocol.path("grindstone_options")));

	public static final StreamCodec<ByteBuf, GrindstoneOptionsPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, GrindstoneOptionsPayload::containerId,
			PayloadCodecs.ordinal(Status.class), GrindstoneOptionsPayload::status,
			GrindstonePayloadCodecs.OPTION.apply(ByteBufCodecs.list(MAX_OPTIONS)), GrindstoneOptionsPayload::options,
			ByteBufCodecs.optional(GrindstonePayloadCodecs.CHOICE), GrindstoneOptionsPayload::choice,
			ByteBufCodecs.VAR_INT, GrindstoneOptionsPayload::levelsCharged,
			GrindstoneOptionsPayload::new);

	/** Checks every size the codec would check, so an oversize payload fails on the server thread, not in Netty. */
	public GrindstoneOptionsPayload {
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(choice, "choice");
		options = List.copyOf(options);
		if (options.size() > MAX_OPTIONS) {
			throw new IllegalArgumentException(options.size() + " grindstone rows, max " + MAX_OPTIONS);
		}
		options.forEach(o -> GrindstonePayloadCodecs.checkIds(o.choice()));
		choice.ifPresent(GrindstonePayloadCodecs::checkIds);
		if (levelsCharged < 0) {
			throw new IllegalArgumentException("levelsCharged = " + levelsCharged);
		}
	}

	public static GrindstoneOptionsPayload of(int containerId, GrindstoneOutcome outcome) {
		return new GrindstoneOptionsPayload(containerId, outcome.status(), outcome.options(), outcome.choice(),
				outcome.levelsCharged());
	}

	@Override
	public Type<GrindstoneOptionsPayload> type() {
		return TYPE;
	}
}
