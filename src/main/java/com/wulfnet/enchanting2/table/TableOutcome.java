package com.wulfnet.enchanting2.table;

import com.wulfnet.enchanting2.cost.CostBreakdown;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;

/**
 * What the table would do with its item, lapis, power and the player's selection (DESIGN §6): every row the panel lists,
 * the first invalid pick, the price, and the enchanted item. Computed on the server only.
 *
 * @param rows    every offered enchantment applicable to the item, in ID order
 * @param invalid the first pick (in ID order) that blocks the selection; present exactly for {@link Status#INVALID_PICK}
 * @param cost    the price of the selection; present from {@link Status#TOO_EXPENSIVE} on (the selection itself is valid)
 * @param result  the enchanted item; empty unless {@link Status#OK}
 * @param shelves bookshelves counted around the table, 0..15
 */
public record TableOutcome(Status status, List<TableRow> rows, Optional<Invalid> invalid, Optional<CostBreakdown> cost,
		ItemStack result, int shelves) {
	/** In the order they are checked: the first that applies wins. */
	public enum Status {
		/** No item, or one the table can't work on (or the calculator failed; the caller logged it). */
		NONE,
		/** No offered enchantment applies to the item. */
		NOTHING_TO_ENCHANT,
		/** There are rows, and nothing is picked. */
		NO_SELECTION,
		/** A pick isn't allowed; {@link #invalid} names it. Nothing is enchanted, not even the valid picks. */
		INVALID_PICK,
		/** Above {@code maximum_operation_cost}. */
		TOO_EXPENSIVE,
		INSUFFICIENT_LEVELS,
		INSUFFICIENT_LAPIS,
		OK;

		/** The selection was valid, so it has a price. */
		public boolean priced() {
			return compareTo(TOO_EXPENSIVE) >= 0;
		}
	}

	/**
	 * @param level         the picked level
	 * @param conflictsWith only for {@link TableRow.Status#CONFLICT}
	 */
	public record Invalid(String id, int level, TableRow.Status reason, Optional<String> conflictsWith) {
		public Invalid {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(reason, "reason");
			Objects.requireNonNull(conflictsWith, "conflictsWith");
			if (reason == TableRow.Status.OK || reason == TableRow.Status.MAXED) {
				throw new IllegalArgumentException(reason + " doesn't make a pick invalid");
			}
			if (conflictsWith.isPresent() != (reason == TableRow.Status.CONFLICT)) {
				throw new IllegalArgumentException("conflictsWith is set exactly for CONFLICT, got " + reason);
			}
		}
	}

	public TableOutcome {
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(invalid, "invalid");
		Objects.requireNonNull(cost, "cost");
		Objects.requireNonNull(result, "result");
		rows = List.copyOf(rows);
		if (invalid.isPresent() != (status == Status.INVALID_PICK)) {
			throw new IllegalArgumentException("invalid is set exactly for INVALID_PICK, got " + status);
		}
		if (cost.isPresent() != status.priced()) {
			throw new IllegalArgumentException("cost is set exactly when the selection is priced, got " + status);
		}
		if (result.isEmpty() == (status == Status.OK)) {
			throw new IllegalArgumentException("result is present exactly when the status is OK, got " + status);
		}
		if (shelves < 0 || shelves > TablePower.MAX_SHELVES) {
			throw new IllegalArgumentException("shelves = " + shelves);
		}
	}

	/** {@link Status#NONE} with nothing listed: no item, an unsupported one, or a failure the caller has logged. */
	public static TableOutcome none(int shelves) {
		return new TableOutcome(Status.NONE, List.of(), Optional.empty(), Optional.empty(), ItemStack.EMPTY, shelves);
	}

	public boolean ok() {
		return status == Status.OK;
	}

	public int tier() {
		return TablePower.tier(shelves);
	}

	/** XP levels the enchant takes; 0 unless {@link Status#OK}. */
	public int levelsCharged() {
		return ok() ? cost.orElseThrow().levelsCharged() : 0;
	}

	/** Lapis the enchant takes; 0 unless {@link Status#OK}. */
	public int lapisCharged() {
		return ok() ? cost.orElseThrow().lapisCharged() : 0;
	}
}
