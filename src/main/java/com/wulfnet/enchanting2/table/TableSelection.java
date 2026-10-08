package com.wulfnet.enchanting2.table;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * What the player picked at the table: enchantment ID → target level, sorted by ID. It arrives from the client, so the
 * constructor bounds it; whether each pick is allowed is {@link TableCalculator}'s call.
 *
 * @param levels registered ID (e.g. {@code minecraft:sharpness}) → the level the item should end up with
 */
public record TableSelection(Map<String, Integer> levels) {
	/** More picks than any registry holds is a forged packet; vanilla has 43 enchantments. */
	public static final int MAX_PICKS = 256;
	/** Vanilla's enchantment codec bounds levels to 1..255. */
	public static final int MAX_LEVEL = 255;
	/** {@code Identifier}s are short in practice; this only stops a forged packet from sending megabytes. */
	public static final int MAX_ID_LENGTH = 256;

	public static final TableSelection EMPTY = new TableSelection(Map.of());

	public TableSelection {
		Objects.requireNonNull(levels, "levels");
		if (levels.size() > MAX_PICKS) {
			throw new IllegalArgumentException(levels.size() + " picks, more than " + MAX_PICKS);
		}
		levels.forEach((id, level) -> {
			if (id == null || id.isBlank() || id.length() > MAX_ID_LENGTH) {
				throw new IllegalArgumentException("pick with an invalid ID: " + id);
			}
			if (level == null || level < 1 || level > MAX_LEVEL) {
				throw new IllegalArgumentException(id + " at level " + level);
			}
		});
		levels = Collections.unmodifiableMap(new TreeMap<>(levels));
	}

	public boolean isEmpty() {
		return levels.isEmpty();
	}

	/** A copy with {@code id} at {@code level}, replacing any earlier pick of it. */
	public TableSelection with(String id, int level) {
		Map<String, Integer> copy = new TreeMap<>(levels);
		copy.put(id, level);
		return new TableSelection(copy);
	}

	/** A copy without {@code id}. */
	public TableSelection without(String id) {
		Map<String, Integer> copy = new TreeMap<>(levels);
		copy.remove(id);
		return new TableSelection(copy);
	}
}
