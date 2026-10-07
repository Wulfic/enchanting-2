package com.wulfnet.enchanting2.network;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.anvil.AnvilOutcome;
import com.wulfnet.enchanting2.anvil.AnvilOutcome.Entry;
import com.wulfnet.enchanting2.anvil.AnvilOutcome.Status;
import com.wulfnet.enchanting2.anvil.TransferRules.Fate;
import com.wulfnet.enchanting2.cost.CostBreakdown;
import com.wulfnet.enchanting2.enchantment.EnchantRules.Verdict;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: what the anvil in container {@code containerId} would do, for the preview panel (DESIGN §6). The
 * server computes it, so the client needs no pricing table and can't misprice. The total travels here as a VarInt:
 * vanilla syncs the cost {@code DataSlot} as a short, which wraps past 32,767.
 *
 * <p>Enums travel as ordinals. Reordering {@link Status}, {@link Verdict}, {@link Fate} or {@link CostBreakdown.Kind}
 * changes the wire format and needs a {@link NetworkProtocol#VERSION} bump.
 *
 * @param breakdown present whenever the operation was priced, as in {@link AnvilOutcome#breakdown()}
 */
public record AnvilPreviewPayload(int containerId, Status status, Optional<CostBreakdown> breakdown,
		List<Entry> entries) implements CustomPacketPayload {
	/** Per list. Far above any real anvil (one entry per enchantment, at most a few lines per entry). */
	public static final int MAX_ENTRIES = 1024;
	public static final int MAX_LINES = 1024;
	/** Characters in an enchantment ID. */
	public static final int MAX_ID_CHARS = PayloadCodecs.MAX_ID_CHARS;

	public static final Type<AnvilPreviewPayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath(Enchanting2.MOD_ID, NetworkProtocol.path("anvil_preview")));

	private static final StreamCodec<ByteBuf, String> ID = PayloadCodecs.ID;

	private static final StreamCodec<ByteBuf, CostBreakdown.Line> LINE = StreamCodec.composite(
			PayloadCodecs.ordinal(CostBreakdown.Kind.class), CostBreakdown.Line::kind,
			ID, CostBreakdown.Line::id,
			ByteBufCodecs.VAR_INT, CostBreakdown.Line::from,
			ByteBufCodecs.VAR_INT, CostBreakdown.Line::to,
			ByteBufCodecs.VAR_LONG, CostBreakdown.Line::amount,
			CostBreakdown.Line::new);

	/** Decoding runs {@code CostBreakdown}'s checks, so totals that don't match their lines fail to decode. */
	private static final StreamCodec<ByteBuf, CostBreakdown> BREAKDOWN = StreamCodec.composite(
			LINE.apply(ByteBufCodecs.list(MAX_LINES)), CostBreakdown::lines,
			ByteBufCodecs.VAR_INT, CostBreakdown::enchantCost,
			ByteBufCodecs.VAR_INT, CostBreakdown::total,
			ByteBufCodecs.VAR_INT, CostBreakdown::lapis,
			ByteBufCodecs.BOOL, CostBreakdown::free,
			ByteBufCodecs.BOOL, CostBreakdown::tooExpensive,
			CostBreakdown::new);

	private static final StreamCodec<ByteBuf, Entry> ENTRY = StreamCodec.composite(
			ID, Entry::id,
			ByteBufCodecs.VAR_INT, Entry::level,
			PayloadCodecs.ordinal(Verdict.class), Entry::verdict,
			ByteBufCodecs.optional(ID), Entry::conflictsWith,
			PayloadCodecs.ordinal(Fate.class), Entry::fate,
			Entry::new);

	public static final StreamCodec<ByteBuf, AnvilPreviewPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, AnvilPreviewPayload::containerId,
			PayloadCodecs.ordinal(Status.class), AnvilPreviewPayload::status,
			ByteBufCodecs.optional(BREAKDOWN), AnvilPreviewPayload::breakdown,
			ENTRY.apply(ByteBufCodecs.list(MAX_ENTRIES)), AnvilPreviewPayload::entries,
			AnvilPreviewPayload::new);

	/**
	 * Checks every size the codec would check, so an oversize preview fails here, on the server thread where the
	 * caller logs it, rather than inside Netty when it is sent.
	 */
	public AnvilPreviewPayload {
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(breakdown, "breakdown");
		entries = List.copyOf(entries);
		if (entries.size() > MAX_ENTRIES) {
			throw new IllegalArgumentException(entries.size() + " entries, max " + MAX_ENTRIES);
		}
		if (breakdown.isPresent()) {
			List<CostBreakdown.Line> lines = breakdown.get().lines();
			if (lines.size() > MAX_LINES) {
				throw new IllegalArgumentException(lines.size() + " cost lines, max " + MAX_LINES);
			}
			lines.forEach(line -> PayloadCodecs.checkId(line.id()));
		}
		for (Entry entry : entries) {
			PayloadCodecs.checkId(entry.id());
			entry.conflictsWith().ifPresent(PayloadCodecs::checkId);
		}
	}

	public static AnvilPreviewPayload of(int containerId, AnvilOutcome outcome) {
		return new AnvilPreviewPayload(containerId, outcome.status(), outcome.breakdown(), outcome.entries());
	}

	@Override
	public Type<AnvilPreviewPayload> type() {
		return TYPE;
	}
}
