package com.wulfnet.enchanting2.anvil;

import com.wulfnet.enchanting2.anvil.TransferRules.Fate;
import com.wulfnet.enchanting2.cost.CostBreakdown;
import com.wulfnet.enchanting2.enchantment.EnchantRules.Verdict;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;

/**
 * What the anvil would do with its current inputs: the result, what goes back into the right slot when the result is
 * taken, the price, and the fate of every enchantment from the right slot (DESIGN §6). Computed on the server only.
 *
 * @param result         the result slot; empty unless {@link Status#OK}
 * @param rightAfterTake the right slot once the result is taken: the leftover book, the book untouched (nothing came
 *                       from it), the materials left over, or empty
 * @param breakdown      the price; present whenever an operation was priced, including {@link Status#TOO_EXPENSIVE}
 * @param entries        every enchantment from the right slot, in ID order, with what happens to it
 */
public record AnvilOutcome(Status status, ItemStack result, ItemStack rightAfterTake, int materialsUsed,
		Optional<CostBreakdown> breakdown, List<Entry> entries) {
	public enum Status {
		/** Nothing to show: empty left slot, or a combination the anvil doesn't do. */
		NONE,
		OK,
		/** A book or second item that changes nothing, with no repair or rename to do. */
		NOTHING_TO_DO,
		/** An entry {@link Fate#REFUSE}d the whole operation. */
		REFUSED,
		/** Above {@code maximum_operation_cost}. */
		TOO_EXPENSIVE,
		/**
		 * Enchanting a stack of more than one item (vanilla blocks it too), or a stack of books or items in the right
		 * slot. Renaming a stack is fine.
		 */
		STACKED_ITEM
	}

	/** @param conflictsWith the enchantment on the result it conflicts with, only for {@link Verdict#CONFLICT} */
	public record Entry(String id, int level, Verdict verdict, Optional<String> conflictsWith, Fate fate) {
		public Entry {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(verdict, "verdict");
			Objects.requireNonNull(conflictsWith, "conflictsWith");
			Objects.requireNonNull(fate, "fate");
		}
	}

	public AnvilOutcome {
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(result, "result");
		Objects.requireNonNull(rightAfterTake, "rightAfterTake");
		Objects.requireNonNull(breakdown, "breakdown");
		entries = List.copyOf(entries);
		if (result.isEmpty() == (status == Status.OK)) {
			throw new IllegalArgumentException("result is present exactly when the status is OK, got " + status);
		}
		if (status == Status.OK && breakdown.isEmpty()) {
			throw new IllegalArgumentException("an OK outcome is priced");
		}
		if (materialsUsed < 0) {
			throw new IllegalArgumentException("materialsUsed = " + materialsUsed);
		}
	}

	/** {@link Status#NONE} after {@link AnvilCalculator} threw; the caller has logged it. */
	public static AnvilOutcome failed() {
		return none();
	}

	static AnvilOutcome none() {
		return refused(Status.NONE, Optional.empty(), List.of());
	}

	static AnvilOutcome refused(Status status, Optional<CostBreakdown> breakdown, List<Entry> entries) {
		return new AnvilOutcome(status, ItemStack.EMPTY, ItemStack.EMPTY, 0, breakdown, entries);
	}

	public boolean ok() {
		return status == Status.OK;
	}

	/** XP levels taken when the result is taken; 0 unless {@link Status#OK}. */
	public int levelsCharged() {
		return ok() ? breakdown.orElseThrow().levelsCharged() : 0;
	}
}
