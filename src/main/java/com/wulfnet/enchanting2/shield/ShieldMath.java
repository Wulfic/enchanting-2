package com.wulfnet.enchanting2.shield;

/** Phase 10cC: the shield enchantments' arithmetic, MC-free so it's unit tested. */
public final class ShieldMath {
	/** The highest mob effect amplifier vanilla saves (it's stored as a byte-sized int). */
	static final int MAX_AMPLIFIER = 255;

	private ShieldMath() {
	}

	/**
	 * Bulwark's effect amplifier for {@code amount} absorption health: one health per step, so amplifier n gives n + 1.
	 * Rounded to whole health; -1 (no effect) under half a health, capped at {@link #MAX_AMPLIFIER}.
	 */
	public static int bulwarkAmplifier(double amount) {
		if (!Double.isFinite(amount)) {
			throw new IllegalArgumentException("not a finite amount: " + amount);
		}
		long health = Math.round(amount);
		return (int) Math.clamp(health - 1, -1, MAX_AMPLIFIER);
	}

	/** Riposte: {@code amount} with a fractional {@code bonus} added; a negative bonus (a datapack's) adds nothing. */
	public static float withBonus(float amount, double bonus) {
		if (!Double.isFinite(bonus)) {
			throw new IllegalArgumentException("not a finite bonus: " + bonus);
		}
		return (float) (amount * (1.0 + Math.max(0.0, bonus)));
	}
}
