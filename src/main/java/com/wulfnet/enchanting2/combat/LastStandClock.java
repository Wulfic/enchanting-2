package com.wulfnet.enchanting2.combat;

/** Last Stand's numbers (Phase 10cFB), MC-free so they're unit tested. */
public final class LastStandClock {
	/** The shortest cooldown, level V's 2 minutes: VI's formula would reach 0 and make the wearer unkillable. */
	public static final long MIN_COOLDOWN_TICKS = 2400;

	private LastStandClock() {
	}

	/** {@code ticks} from the enchantment's formula, never under {@link #MIN_COOLDOWN_TICKS}. */
	public static long cooldown(float ticks) {
		if (!Float.isFinite(ticks)) {
			throw new IllegalArgumentException("not a finite cooldown: " + ticks);
		}
		return Math.max(MIN_COOLDOWN_TICKS, Math.round(ticks));
	}

	/** {@code ticks} as "m:ss", rounded up so the HUD never shows 0:00 while it's still cooling down. */
	public static String format(long ticks) {
		long seconds = Math.max(0, (ticks + 19) / 20);
		return seconds / 60 + ":" + String.format("%02d", seconds % 60);
	}
}
