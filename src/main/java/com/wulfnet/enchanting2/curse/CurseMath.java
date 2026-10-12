package com.wulfnet.enchanting2.curse;

/**
 * DESIGN §5a, Phase 10e (owner, 2026-10-12): how Weight's and Hunger's per-piece values combine. MC-free so the rules
 * are unit tested; {@link CurseEffects} sums the pieces and applies the result.
 */
public final class CurseMath {
	private CurseMath() {
	}

	/**
	 * Weight's {@code add_multiplied_base} movement speed modifier: minus the pieces' summed fraction, capped at
	 * {@code maxSlowdownPercent}. A negative sum (a datapack's negative penalty) counts as none, so a curse never
	 * speeds anyone up; a non-finite one is the caller's to reject.
	 */
	public static double weightModifier(double penaltySum, int maxSlowdownPercent) {
		requireFinite(penaltySum);
		if (maxSlowdownPercent < 0 || maxSlowdownPercent > 100) {
			throw new IllegalArgumentException("maxSlowdownPercent = " + maxSlowdownPercent);
		}
		return -Math.min(Math.max(penaltySum, 0.0), maxSlowdownPercent / 100.0);
	}

	/** Hunger's multiplier on exhaustion: 1 plus the pieces' summed fraction, uncapped (owner), never below 1. */
	public static float exhaustionFactor(double exhaustionSum) {
		requireFinite(exhaustionSum);
		return (float) (1.0 + Math.max(exhaustionSum, 0.0));
	}

	private static void requireFinite(double sum) {
		if (!Double.isFinite(sum)) {
			throw new IllegalArgumentException("not a finite sum: " + sum);
		}
	}
}
