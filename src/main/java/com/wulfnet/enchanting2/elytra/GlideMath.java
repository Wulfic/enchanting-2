package com.wulfnet.enchanting2.elytra;

/**
 * Speed's arithmetic (Phase 10cGA), MC-free so it's unit tested. A glide's top speed is set by its air drag d
 * (v* ∝ d / (1 − d)), so speeding it up by k means losing (1 − d) / k per tick instead of (1 − d).
 */
public final class GlideMath {
	/** The largest bonus (+100%, Speed X): double speed stays far inside the server's moved-too-quickly check. */
	public static final double MAX_BONUS = 1.0;
	/** A rocket pulls its flyer toward 1.5 × look at half the gap per tick, plus 0.1 × look: it settles at 1.7. */
	public static final double ROCKET_PULL = 0.85;

	private GlideMath() {
	}

	/** The speed factor k for a summed bonus: 1 + the bonus, clamped to [0, {@link #MAX_BONUS}]. */
	public static double factor(double bonus) {
		if (!Double.isFinite(bonus)) {
			throw new IllegalArgumentException("not a finite bonus: " + bonus);
		}
		return 1.0 + Math.clamp(bonus, 0.0, MAX_BONUS);
	}

	/** Drag {@code drag} (the part of the speed kept each tick) for a glide {@code factor} times as fast. */
	public static double drag(double drag, double factor) {
		return 1.0 - (1.0 - drag) / factor;
	}
}
