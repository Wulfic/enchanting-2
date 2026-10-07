package com.wulfnet.enchanting2.anvil;

import com.wulfnet.enchanting2.enchantment.EnchantRules.Verdict;
import java.util.Objects;

/**
 * DESIGN §6 and the owner's Phase 6 answers (decisions.md 2026-10-07 "Anvil"): what happens to one enchantment from the
 * right slot, given its {@code EnchantRules} verdict against the result so far. Pure, so the whole table is unit-tested.
 *
 * <p>A dominated entry ({@link Verdict#ALREADY_AT_LEVEL}: the target already has that ID at the same or a higher level)
 * is never a loss. Only {@link InvalidBookMode#STRIP_INVALID} destroys anything else.
 */
public final class TransferRules {
	/** Where the enchantment comes from. */
	public enum Source {
		/** An enchanted book. Under {@code apply_valid} it can go back to the right slot holding what was skipped. */
		BOOK,
		/** A second copy of the same item. It is always consumed, so there is nothing to hand back. */
		ITEM
	}

	public enum Fate {
		/** Goes onto the result. */
		APPLY,
		/** Stays on the book, which goes back to the right slot. */
		KEEP_ON_BOOK,
		/** Consumed with its source; the result already has it at that level or higher, so nothing is lost. */
		CONSUME_DOMINATED,
		/** Destroyed with its source. Only under {@code strip_invalid}, and the preview shows it first. */
		LOSE,
		/** The whole operation has no result. */
		REFUSE
	}

	private TransferRules() {
	}

	public static Fate decide(Source source, InvalidBookMode mode, Verdict verdict) {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(mode, "mode");
		Objects.requireNonNull(verdict, "verdict");
		return switch (verdict) {
			case OK -> Fate.APPLY;
			case ALREADY_AT_LEVEL -> source == Source.BOOK && mode == InvalidBookMode.APPLY_VALID
					? Fate.KEEP_ON_BOOK
					: Fate.CONSUME_DOMINATED;
			case INVALID_LEVEL, ABOVE_MAX, NOT_APPLICABLE, CONFLICT -> switch (mode) {
				case REJECT_ALL -> Fate.REFUSE;
				case APPLY_VALID -> source == Source.BOOK ? Fate.KEEP_ON_BOOK : Fate.REFUSE;
				case STRIP_INVALID -> Fate.LOSE;
			};
		};
	}
}
