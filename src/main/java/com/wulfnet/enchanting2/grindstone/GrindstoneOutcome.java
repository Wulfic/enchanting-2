package com.wulfnet.enchanting2.grindstone;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;

/**
 * What the grindstone would do with its current inputs and the player's pick (DESIGN §6): every row the panel lists,
 * the result, and what each input slot holds once the result is taken. Computed on the server only.
 *
 * @param options    every row for these inputs, in panel order; empty for {@link Status#NONE} and
 *                   {@link Status#STACKED_ITEM}
 * @param choice     the player's pick, as given (it may not be among {@code options})
 * @param result     the result slot; empty unless {@link Status#OK}
 * @param slot0After the top input once the result is taken; only meaningful when {@link Status#OK}
 * @param slot1After the bottom input once the result is taken; only meaningful when {@link Status#OK}
 */
public record GrindstoneOutcome(Status status, List<GrindstoneOption> options, Optional<GrindstoneChoice> choice,
		ItemStack result, ItemStack slot0After, ItemStack slot1After, int levelsCharged) {
	public enum Status {
		/** Nothing the grindstone does with these inputs (or the calculator failed; the caller logged it). */
		NONE,
		/** A source stack of more than one item; vanilla refuses it too. */
		STACKED_ITEM,
		/** There are rows, and the player hasn't picked one. */
		NO_CHOICE,
		/** The pick isn't a row for these inputs: stale (the inputs changed) or forged. */
		NOT_AVAILABLE,
		OK,
		/** Row: curses are cleansed, never extracted (DESIGN §6). */
		CURSE_NOT_EXTRACTABLE,
		/** Row: a book holding nothing but this entry; extracting would just move it onto another book. */
		NOTHING_TO_SPLIT,
		/** Row: above {@code maximum_operation_cost}. */
		TOO_EXPENSIVE
	}

	public GrindstoneOutcome {
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(choice, "choice");
		Objects.requireNonNull(result, "result");
		Objects.requireNonNull(slot0After, "slot0After");
		Objects.requireNonNull(slot1After, "slot1After");
		options = List.copyOf(options);
		if (result.isEmpty() == (status == Status.OK)) {
			throw new IllegalArgumentException("result is present exactly when the status is OK, got " + status);
		}
		if (status == Status.OK && choice.isEmpty()) {
			throw new IllegalArgumentException("an OK outcome has a choice");
		}
		if (levelsCharged < 0 || (levelsCharged > 0 && status != Status.OK)) {
			throw new IllegalArgumentException(levelsCharged + " levels charged for " + status);
		}
	}

	/** {@link Status#NONE} after {@link GrindstoneCalculator} threw; the caller has logged it. */
	public static GrindstoneOutcome failed(Optional<GrindstoneChoice> choice) {
		return refused(Status.NONE, List.of(), choice);
	}

	static GrindstoneOutcome refused(Status status, List<GrindstoneOption> options, Optional<GrindstoneChoice> choice) {
		return new GrindstoneOutcome(status, options, choice, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, 0);
	}

	public boolean ok() {
		return status == Status.OK;
	}

	/** Disenchant and Repair: vanilla's {@code onTake} runs (XP orbs, both inputs emptied). */
	public boolean vanillaTake() {
		return ok() && choice.orElseThrow().action().vanillaTake();
	}
}
