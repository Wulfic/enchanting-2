package com.wulfnet.enchanting2.grindstone;

import com.wulfnet.enchanting2.grindstone.GrindstoneOutcome.Status;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * One row the panel lists for the current inputs, priced, possibly refused.
 *
 * @param level the enchantment's level on the source; 0 for Disenchant and Repair
 * @param cost  the price in XP levels, shown even when {@code free}; 0 for Disenchant and Repair (vanilla's are free)
 * @param free  creative with {@code creative_free}: nothing is charged
 */
public record GrindstoneOption(GrindstoneChoice choice, int level, int cost, boolean free, Status status) {
	/** The statuses a single row can have; the rest describe the inputs as a whole. */
	public static final Set<Status> ROW_STATUSES = EnumSet.of(Status.OK, Status.CURSE_NOT_EXTRACTABLE,
			Status.NOTHING_TO_SPLIT, Status.TOO_EXPENSIVE);

	public GrindstoneOption {
		Objects.requireNonNull(choice, "choice");
		Objects.requireNonNull(status, "status");
		if (!ROW_STATUSES.contains(status)) {
			throw new IllegalArgumentException(status + " isn't a row status");
		}
		if (level < 0 || cost < 0) {
			throw new IllegalArgumentException("level " + level + ", cost " + cost);
		}
		if (choice.action().takesEnchantment() != (level > 0)) {
			throw new IllegalArgumentException(choice + " at level " + level);
		}
	}

	public boolean ok() {
		return status == Status.OK;
	}

	/** XP levels taken when this row's result is taken. */
	public int levelsCharged() {
		return ok() && !free ? cost : 0;
	}
}
