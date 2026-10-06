package com.wulfnet.enchanting2.experience;

import com.wulfnet.enchanting2.config.ExperienceSection;

/**
 * DESIGN §3. The one place that converts between levels and XP points. Pure Java, so plain JUnit covers it.
 * <p>
 * There is no level cap (owner, 2026-10-06) beyond {@code Integer.MAX_VALUE}. Points-to-next is clamped to
 * {@code [1, Integer.MAX_VALUE]}: vanilla's {@code 112 + 9(L - 30)} overflows {@code int} at {@link #VANILLA_CLAMP_LEVEL},
 * goes negative, and the client then hides the XP bar. Totals use exact {@code long} math. The largest possible total
 * (the vanilla curve up to {@code Integer.MAX_VALUE}) is about 4.4e18, under {@code Long.MAX_VALUE}, so an
 * {@link ArithmeticException} here means a bug, not a big player.
 */
public final class ExperienceCurve {
	/** The vanilla curve: what a client uses on a server without Enchanting 2. */
	public static final ExperienceSection VANILLA = new ExperienceSection(false,
			ExperienceSection.DEFAULTS.flattenAfterLevel(), ExperienceSection.DEFAULTS.fixedPointsPerLevel());

	/** First level whose vanilla points-to-next exceeds {@code Integer.MAX_VALUE}. */
	static final int VANILLA_CLAMP_LEVEL = 30 + (Integer.MAX_VALUE - 112) / 9 + 1;

	/** A level and the points earned toward the next one. */
	public record Position(int level, int pointsIntoLevel) {
	}

	private ExperienceCurve() {
	}

	/** Vanilla 1.21.11 {@code Player#getXpNeededForNextLevel}, without the overflow. Negative levels read as 0. */
	public static long vanillaPointsToNext(int level) {
		long l = Math.max(0, level);
		if (l >= 30) {
			return 112 + (l - 30) * 9;
		}
		if (l >= 15) {
			return 37 + (l - 15) * 5;
		}
		return 7 + l * 2;
	}

	/** Points needed to go from {@code level} to {@code level + 1}. Always in {@code [1, Integer.MAX_VALUE]}. */
	public static int pointsToNext(ExperienceSection section, int level) {
		if (section.enabled() && level >= section.flattenAfterLevel()) {
			return section.fixedPointsPerLevel();
		}
		return (int) Math.min(vanillaPointsToNext(level), Integer.MAX_VALUE);
	}

	/** Points needed to reach {@code level} from 0 points. Negative levels read as 0. */
	public static long totalPoints(ExperienceSection section, int level) {
		int l = Math.max(0, level);
		if (!section.enabled() || l <= section.flattenAfterLevel()) {
			return vanillaSum(l);
		}
		long flat = Math.multiplyExact((long) l - section.flattenAfterLevel(), section.fixedPointsPerLevel());
		return Math.addExact(vanillaSum(section.flattenAfterLevel()), flat);
	}

	public static long totalPoints(ExperienceSection section, Position position) {
		return Math.addExact(totalPoints(section, position.level()), Math.max(0, position.pointsIntoLevel()));
	}

	/**
	 * The position {@code total} points reach. Negative totals read as 0. At {@code Integer.MAX_VALUE} the level stops
	 * and the points stay one short of the next level, which is where vanilla's own loop leaves them.
	 */
	public static Position fromTotal(ExperienceSection section, long total) {
		if (total <= 0) {
			return new Position(0, 0);
		}
		// Largest level whose total is <= total. totalPoints is strictly increasing (points-to-next is >= 1).
		int low = 0;
		int high = Integer.MAX_VALUE;
		while (low < high) {
			int mid = (int) (((long) low + high + 1) >>> 1);
			if (totalPoints(section, mid) <= total) {
				low = mid;
			} else {
				high = mid - 1;
			}
		}
		long into = total - totalPoints(section, low);
		long cap = pointsToNext(section, low) - 1L;
		return new Position(low, (int) Math.min(into, cap));
	}

	/**
	 * Largest points-to-next whose progress float stays exact. One float step near 1 is {@code 2^-24}, so the stored
	 * progress overshoots {@code p / n} by at most {@code n * 2^-24 <= 0.125} points, and nothing reads it as {@code p + 1}.
	 */
	public static final int EXACT_PROGRESS_LIMIT = 1 << 21;

	public static boolean exactProgressFits(int pointsToNext) {
		return pointsToNext <= EXACT_PROGRESS_LIMIT;
	}

	/**
	 * The progress float for {@code points} into a level of {@code pointsToNext}: the smallest float {@code f} with
	 * {@code f * n >= p}. Vanilla stores {@code p / n} rounded to nearest, and its {@code floor(progress * n)} reads that
	 * back as {@code p - 1} whenever the float rounds down. The smallest float keeps the bar where vanilla draws it: 0
	 * points is exactly 0, never a visible sliver.
	 */
	public static float storedProgress(int points, int pointsToNext) {
		float progress = (float) ((double) points / pointsToNext);
		// Round-to-nearest is at most half a step low, so this runs at most once.
		while ((double) progress * pointsToNext < points) {
			progress = Math.nextUp(progress);
		}
		return progress;
	}

	/**
	 * Points into the level, from either stored form: ours (just at or above {@code p}), or vanilla's {@code p / n},
	 * which can sit just under {@code p}. The 0.25 offset rounds both to {@code p}. Clamped to {@code [0, n - 1]}; NaN
	 * reads as 0.
	 */
	public static int readPoints(float progress, int pointsToNext) {
		double points = Math.floor((double) progress * pointsToNext + 0.25);
		if (!(points > 0)) {
			return 0;
		}
		return (int) Math.min(points, pointsToNext - 1L);
	}

	/** Sum of vanilla points-to-next over levels {@code [0, level)}, with the per-level clamp applied. */
	private static long vanillaSum(int level) {
		return arithmeticSum(0, Math.min(level, 15), 7, 2, 0)
				+ arithmeticSum(15, Math.min(level, 30), 37, 5, 15)
				+ arithmeticSum(30, Math.min(level, VANILLA_CLAMP_LEVEL), 112, 9, 30)
				+ Math.multiplyExact((long) Math.max(0, level - VANILLA_CLAMP_LEVEL), Integer.MAX_VALUE);
	}

	/** Sum of {@code base + step * (k - origin)} for {@code k} in {@code [from, to)}; 0 if the range is empty. */
	private static long arithmeticSum(long from, long to, long base, long step, long origin) {
		if (to <= from) {
			return 0;
		}
		long n = to - from;
		long first = base + step * (from - origin);
		// n(n - 1) is even, so halve before the step multiply to keep the product small.
		return Math.addExact(Math.multiplyExact(n, first), Math.multiplyExact(step, n * (n - 1) / 2));
	}
}
