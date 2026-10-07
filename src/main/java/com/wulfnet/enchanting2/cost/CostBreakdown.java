package com.wulfnet.enchanting2.cost;

import java.util.List;
import java.util.Objects;

/**
 * A priced operation, line by line, so a screen can show where every level comes from (DESIGN §5: costs are shown
 * before the player commits). Amounts are XP levels. The totals are the sums of their lines, checked here, and
 * saturate at {@link Integer#MAX_VALUE} rather than wrap.
 *
 * @param enchantCost  the enchant, surcharge and curse-discount lines; what lapis is based on
 * @param total        every line; what the player pays unless {@code free}
 * @param lapis        lapis due at the table, before {@code free}; always 0 at the anvil
 * @param free         creative with {@code creative_free}: the lines are kept for display, nothing is charged
 * @param tooExpensive {@code total} is above {@code maximum_operation_cost}; never true for a creative player
 */
public record CostBreakdown(List<Line> lines, int enchantCost, int total, int lapis, boolean free,
		boolean tooExpensive) {
	public enum Kind {
		/** An enchantment added or raised: {@code from} 0 means newly added. */
		ENCHANT,
		/** {@code other_levels_surcharge} on {@code id}; {@code from}/{@code to} are the other levels before/after. */
		SURCHARGE,
		/**
		 * DESIGN §5a, the only negative line: {@code from} is the curses' summed percent, {@code to} the percent applied
		 * after {@code max_discount_percent}. Never touches rename or repair.
		 */
		CURSE_DISCOUNT,
		RENAME,
		/** {@code to} is the material count, or 0 for combining two items. */
		REPAIR
	}

	/**
	 * @param id     the enchantment, or empty for rename, repair and the curse discount
	 * @param amount {@code long} so a discount on a saturated cost still adds up exactly
	 */
	public record Line(Kind kind, String id, int from, int to, long amount) {
		public Line {
			Objects.requireNonNull(kind, "kind");
			Objects.requireNonNull(id, "id");
			if (kind == Kind.CURSE_DISCOUNT ? amount > 0 : amount < 0) {
				throw new IllegalArgumentException(kind + " line of " + amount);
			}
		}
	}

	public CostBreakdown {
		lines = List.copyOf(lines);
		long enchantSum = 0;
		long sum = 0;
		for (Line line : lines) {
			sum += line.amount;
			if (line.kind == Kind.ENCHANT || line.kind == Kind.SURCHARGE || line.kind == Kind.CURSE_DISCOUNT) {
				enchantSum += line.amount;
			}
		}
		if (enchantCost != saturate(enchantSum) || total != saturate(sum)) {
			throw new IllegalArgumentException("totals " + enchantCost + "/" + total + " don't match the lines "
					+ enchantSum + "/" + sum);
		}
		if (lapis < 0 || lapis > CostModel.MAX_LAPIS) {
			throw new IllegalArgumentException("lapis = " + lapis);
		}
	}

	/** XP levels to take from the player. */
	public int levelsCharged() {
		return free ? 0 : total;
	}

	/** Lapis to take from the player. */
	public int lapisCharged() {
		return free ? 0 : lapis;
	}

	static int saturate(long value) {
		return (int) Math.min(value, Integer.MAX_VALUE);
	}
}
