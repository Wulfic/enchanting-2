package com.wulfnet.enchanting2.network;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.cost.CostBreakdown;
import com.wulfnet.enchanting2.table.TableOutcome;
import com.wulfnet.enchanting2.table.TableOutcome.Status;
import com.wulfnet.enchanting2.table.TablePower;
import com.wulfnet.enchanting2.table.TableRow;
import com.wulfnet.enchanting2.table.TableSelection;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: the table panel for container {@code containerId}: the selection as the server holds it (the client
 * echoes it back in {@link TableEnchantPayload}), every row, the first invalid pick, the price, and the power. The server
 * prices everything; the client only shows it.
 *
 * <p>Enums travel as ordinals. Reordering {@link Status}, {@link TableRow.Status} or {@link CostBreakdown.Kind} changes
 * the wire format and needs a {@link NetworkProtocol#VERSION} bump.
 */
public record TablePreviewPayload(int containerId, TableSelection selection, Status status, List<TableRow> rows,
		Optional<TableOutcome.Invalid> invalid, Optional<CostBreakdown> cost, int shelves)
		implements CustomPacketPayload {
	/** One row per offered enchantment; vanilla offers 40. */
	public static final int MAX_ROWS = 1024;

	public static final Type<TablePreviewPayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath(Enchanting2.MOD_ID, NetworkProtocol.path("table_preview")));

	public static final StreamCodec<ByteBuf, TablePreviewPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, TablePreviewPayload::containerId,
			TablePayloadCodecs.SELECTION, TablePreviewPayload::selection,
			PayloadCodecs.ordinal(Status.class), TablePreviewPayload::status,
			TablePayloadCodecs.ROW.apply(ByteBufCodecs.list(MAX_ROWS)), TablePreviewPayload::rows,
			ByteBufCodecs.optional(TablePayloadCodecs.INVALID), TablePreviewPayload::invalid,
			ByteBufCodecs.optional(PayloadCodecs.BREAKDOWN), TablePreviewPayload::cost,
			ByteBufCodecs.VAR_INT, TablePreviewPayload::shelves,
			TablePreviewPayload::new);

	/** Checks every size the codec would check, so an oversize panel fails on the server thread, not in Netty. */
	public TablePreviewPayload {
		Objects.requireNonNull(selection, "selection");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(invalid, "invalid");
		Objects.requireNonNull(cost, "cost");
		rows = List.copyOf(rows);
		// TableOutcome's invariants, so the client never reads a price that isn't there.
		if (invalid.isPresent() != (status == Status.INVALID_PICK) || cost.isPresent() != status.priced()) {
			throw new IllegalArgumentException(status + " with invalid " + invalid.isPresent() + ", cost "
					+ cost.isPresent());
		}
		if (rows.size() > MAX_ROWS) {
			throw new IllegalArgumentException(rows.size() + " table rows, max " + MAX_ROWS);
		}
		TablePayloadCodecs.checkIds(selection);
		for (TableRow row : rows) {
			PayloadCodecs.checkId(row.id());
			row.conflictsWith().ifPresent(PayloadCodecs::checkId);
		}
		invalid.ifPresent(i -> {
			PayloadCodecs.checkId(i.id());
			i.conflictsWith().ifPresent(PayloadCodecs::checkId);
		});
		cost.ifPresent(PayloadCodecs::checkBreakdown);
		if (shelves < 0 || shelves > TablePower.MAX_SHELVES) {
			throw new IllegalArgumentException("shelves = " + shelves);
		}
	}

	public static TablePreviewPayload of(int containerId, TableSelection selection, TableOutcome outcome) {
		return new TablePreviewPayload(containerId, selection, outcome.status(), outcome.rows(), outcome.invalid(),
				outcome.cost(), outcome.shelves());
	}

	@Override
	public Type<TablePreviewPayload> type() {
		return TYPE;
	}

	public int tier() {
		return TablePower.tier(shelves);
	}

	/** XP levels an Enchant would take now, as the server priced it; 0 unless the selection is priced and OK. */
	public int levelsCharged() {
		return status == Status.OK ? cost.orElseThrow().levelsCharged() : 0;
	}

	/** Lapis an Enchant would take now; 0 unless OK. */
	public int lapisCharged() {
		return status == Status.OK ? cost.orElseThrow().lapisCharged() : 0;
	}
}
