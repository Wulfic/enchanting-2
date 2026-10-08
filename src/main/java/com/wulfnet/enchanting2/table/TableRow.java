package com.wulfnet.enchanting2.table;

import java.util.Objects;
import java.util.Optional;

/**
 * One enchantment the table lists for the item: where it stands now, how far this table's power can take it, and whether
 * it can be picked (or, if picked, whether the pick is valid). Computed on the server.
 *
 * @param id            the registered ID, e.g. {@code minecraft:sharpness}
 * @param currentLevel  its level on the item now, 0 if absent
 * @param maxLevel      the registry max level (DESIGN §8 plus {@code max_level_override})
 * @param maxSelectable the highest level this table's power sells ({@link TablePower#maxSelectable})
 * @param selectedLevel the picked level, 0 if not picked
 * @param conflictsWith the enchantment it conflicts with, only for {@link Status#CONFLICT}
 */
public record TableRow(String id, int currentLevel, int maxLevel, int maxSelectable, int selectedLevel, Status status,
		Optional<String> conflictsWith) {
	/**
	 * For an unpicked row: whether it can be picked at {@code currentLevel + 1}. For a picked row: whether the pick is
	 * valid; anything but {@link #OK} blocks the whole selection.
	 */
	public enum Status {
		OK,
		/** Unpicked: already at (or past) the max level. */
		MAXED,
		/** More bookshelves needed for the next level (unpicked) or the picked level. */
		NEEDS_POWER,
		/** Treasure with {@code treasure_requires_full_power}: needs 15 bookshelves. */
		NEEDS_FULL_POWER,
		/** Exclusive with an enchantment on the item or picked before it (in ID order). */
		CONFLICT,
		/** Picked: at or below the level the item already has (only a forged pick). */
		ALREADY_AT_LEVEL,
		/** Picked: above the max level (only a forged pick). */
		ABOVE_MAX,
		/** Picked: not offered at the table, or not applicable to the item (only a forged pick; never a row). */
		NOT_OFFERED
	}

	public TableRow {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(conflictsWith, "conflictsWith");
		if (conflictsWith.isPresent() != (status == Status.CONFLICT)) {
			throw new IllegalArgumentException("conflictsWith is set exactly when the status is CONFLICT, got " + status);
		}
		if (status == Status.NOT_OFFERED) {
			throw new IllegalArgumentException(id + ": a listed row is offered by definition");
		}
		if (currentLevel < 0 || maxLevel < 1 || maxSelectable < 1 || maxSelectable > maxLevel || selectedLevel < 0) {
			throw new IllegalArgumentException(id + ": current " + currentLevel + ", max " + maxLevel + ", selectable "
					+ maxSelectable + ", selected " + selectedLevel);
		}
	}

	public boolean selected() {
		return selectedLevel > 0;
	}

	public boolean ok() {
		return status == Status.OK;
	}
}
