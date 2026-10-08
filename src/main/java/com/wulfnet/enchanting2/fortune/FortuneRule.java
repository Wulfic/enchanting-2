package com.wulfnet.enchanting2.fortune;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

/**
 * DESIGN §6 Fortune on all mining tools, as a pure decision. The MC side gathers the facts. The two expensive ones (the
 * chunk's marks and the Silk-free loot re-roll) are suppliers, asked only once every cheaper check has passed.
 */
public final class FortuneRule {
	/** Whether a position holds a natural block, as the chunk's {@link PlacedMarks} say. */
	public enum Origin {
		NATURAL,
		PLACED,
		/** The chunk's marks couldn't be read: treated as placed (fail closed). */
		UNREADABLE
	}

	/** {@link #MULTIPLY}, or why not. The reason goes in the debug log. */
	public enum Decision {
		MULTIPLY,
		NO_FORTUNE,
		/** Hard-excluded whatever the tags say (ancient debris). */
		EXCLUDED,
		/** Not in the tool's {@code fortune_multiplies} tag (nor, under {@code FORTUNE_MULTIPLIES_SILK}, the silk tag). */
		NOT_TAGGED,
		PLACED,
		UNREADABLE,
		/** {@code SILK_WINS} and Silk Touch changed what the block drops. */
		SILK_CHANGED_DROP
	}

	/**
	 * @param fortuneLevel    Fortune on the tool
	 * @param hasSilk         Silk Touch on the tool
	 * @param tagged          the block is in a {@code fortune_multiplies/<kind>} tag of one of the tool's kinds
	 * @param silkTagged      the block is in {@code fortune_multiplies_silk}
	 * @param excluded        hard-excluded (ancient debris)
	 * @param origin          the position's origin, asked after the tag checks
	 * @param silkChangedDrop whether a Silk-free roll drops different items, asked last and only under {@code SILK_WINS}
	 */
	public static Decision decide(int fortuneLevel, boolean hasSilk, SilkFortuneMode mode, boolean tagged,
			boolean silkTagged, boolean excluded, Supplier<Origin> origin, BooleanSupplier silkChangedDrop) {
		Objects.requireNonNull(mode, "mode");
		if (fortuneLevel <= 0) {
			return Decision.NO_FORTUNE;
		}
		if (excluded) {
			return Decision.EXCLUDED;
		}
		boolean silkMultiplies = hasSilk && mode == SilkFortuneMode.FORTUNE_MULTIPLIES_SILK;
		if (!tagged && !(silkMultiplies && silkTagged)) {
			return Decision.NOT_TAGGED;
		}
		switch (origin.get()) {
			case PLACED -> {
				return Decision.PLACED;
			}
			case UNREADABLE -> {
				return Decision.UNREADABLE;
			}
			case NATURAL -> {
			}
		}
		if (hasSilk && !silkMultiplies && silkChangedDrop.getAsBoolean()) {
			return Decision.SILK_CHANGED_DROP;
		}
		return Decision.MULTIPLY;
	}

	/**
	 * Vanilla's ore formula ({@code ApplyBonusCount.OreDrops}): {@code 1 + max(0, nextInt(level + 2) − 1)}, 22/7 ≈ 3.14 on
	 * average at Fortune V. {@code nextInt} returns 0 (inclusive) to its bound (exclusive).
	 */
	public static int multiplier(int fortuneLevel, IntUnaryOperator nextInt) {
		if (fortuneLevel <= 0) {
			return 1;
		}
		return 1 + Math.max(0, nextInt.applyAsInt(fortuneLevel + 2) - 1);
	}

	private FortuneRule() {
	}
}
