package com.wulfnet.enchanting2.network;

import com.wulfnet.enchanting2.table.TableOutcome;
import com.wulfnet.enchanting2.table.TableRow;
import com.wulfnet.enchanting2.table.TableSelection;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Wire shapes for the table payloads. Decoding runs the records' own checks, rethrown as a {@link DecoderException}, so
 * a malformed selection (level 0, too many picks) or row fails to decode instead of reaching the menu.
 */
final class TablePayloadCodecs {
	static final StreamCodec<ByteBuf, TableSelection> SELECTION = ByteBufCodecs
			.<ByteBuf, String, Integer, Map<String, Integer>>map(size -> new TreeMap<>(), PayloadCodecs.ID,
					ByteBufCodecs.VAR_INT, TableSelection.MAX_PICKS)
			.map(TablePayloadCodecs::selection, TableSelection::levels);

	static final StreamCodec<ByteBuf, TableRow> ROW = StreamCodec.composite(
			PayloadCodecs.ID, TableRow::id,
			ByteBufCodecs.VAR_INT, TableRow::currentLevel,
			ByteBufCodecs.VAR_INT, TableRow::maxLevel,
			ByteBufCodecs.VAR_INT, TableRow::maxSelectable,
			ByteBufCodecs.VAR_INT, TableRow::selectedLevel,
			PayloadCodecs.ordinal(TableRow.Status.class), TableRow::status,
			ByteBufCodecs.optional(PayloadCodecs.ID), TableRow::conflictsWith,
			TablePayloadCodecs::row);

	static final StreamCodec<ByteBuf, TableOutcome.Invalid> INVALID = StreamCodec.composite(
			PayloadCodecs.ID, TableOutcome.Invalid::id,
			ByteBufCodecs.VAR_INT, TableOutcome.Invalid::level,
			PayloadCodecs.ordinal(TableRow.Status.class), TableOutcome.Invalid::reason,
			ByteBufCodecs.optional(PayloadCodecs.ID), TableOutcome.Invalid::conflictsWith,
			TablePayloadCodecs::invalid);

	private TablePayloadCodecs() {
	}

	private static TableSelection selection(Map<String, Integer> levels) {
		try {
			return new TableSelection(levels);
		} catch (IllegalArgumentException e) {
			throw new DecoderException("bad table selection: " + e.getMessage(), e);
		}
	}

	private static TableRow row(String id, int currentLevel, int maxLevel, int maxSelectable, int selectedLevel,
			TableRow.Status status, Optional<String> conflictsWith) {
		try {
			return new TableRow(id, currentLevel, maxLevel, maxSelectable, selectedLevel, status, conflictsWith);
		} catch (IllegalArgumentException e) {
			throw new DecoderException("bad table row: " + e.getMessage(), e);
		}
	}

	private static TableOutcome.Invalid invalid(String id, int level, TableRow.Status reason,
			Optional<String> conflictsWith) {
		try {
			return new TableOutcome.Invalid(id, level, reason, conflictsWith);
		} catch (IllegalArgumentException e) {
			throw new DecoderException("bad table pick: " + e.getMessage(), e);
		}
	}

	/** Throws for a selection {@link #SELECTION} would refuse. */
	static void checkIds(TableSelection selection) {
		selection.levels().keySet().forEach(PayloadCodecs::checkId);
	}
}
