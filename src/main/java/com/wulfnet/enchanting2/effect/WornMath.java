package com.wulfnet.enchanting2.effect;

/**
 * Phase 10cA: how the worn reductions (Clarity, Sustenance, Endurance) and Sure Footing's powder snow factor combine.
 * MC-free so the rules are unit tested; {@link WornEffects} sums the pieces and applies the result.
 */
public final class WornMath {
	private WornMath() {
	}

	/**
	 * What a summed reduction leaves: {@code 1 − sum}, clamped to [0, 1]. A negative sum (a datapack's negative value)
	 * counts as none, so a reduction never makes anything worse; one of 1 or more leaves nothing.
	 */
	public static double remaining(double reductionSum) {
		requireFinite(reductionSum);
		return 1.0 - Math.clamp(reductionSum, 0.0, 1.0);
	}

	/**
	 * A finite effect's duration times {@code remaining}, rounded, at least 1 tick. Rounded rather than floored: the
	 * level fractions are floats (2/3 is 0.6666667), so a floor turns Clarity II on 300 ticks into 99.
	 */
	public static int scaledTicks(int duration, double remaining) {
		if (duration < 0) {
			throw new IllegalArgumentException("not a finite duration: " + duration);
		}
		requireFinite(remaining);
		return (int) Math.max(1L, Math.round(duration * Math.clamp(remaining, 0.0, 1.0)));
	}

	/**
	 * The stuck-in-block speed factor with {@code movement_efficiency} applied, as {@code getBlockSpeedFactor} does for
	 * soul sand: {@code lerp(efficiency, factor, 1)}, so efficiency 1 means no slowdown at all.
	 */
	public static double stuckFactor(double vanillaFactor, double efficiency) {
		requireFinite(efficiency);
		double t = Math.clamp(efficiency, 0.0, 1.0);
		return vanillaFactor + t * (1.0 - vanillaFactor);
	}

	private static void requireFinite(double value) {
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException("not a finite value: " + value);
		}
	}
}
