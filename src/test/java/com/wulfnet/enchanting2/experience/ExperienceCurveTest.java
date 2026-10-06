package com.wulfnet.enchanting2.experience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wulfnet.enchanting2.config.ExperienceSection;
import com.wulfnet.enchanting2.experience.ExperienceCurve.Position;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ExperienceCurveTest {
	private static final ExperienceSection DEFAULTS = ExperienceSection.DEFAULTS;
	private static final ExperienceSection VANILLA = ExperienceCurve.VANILLA;
	private static final ExperienceSection FLAT_FROM_ZERO = new ExperienceSection(true, 0, 50);
	private static final int MAX = Integer.MAX_VALUE;

	/** Vanilla 1.21.11 Player#getXpNeededForNextLevel, transcribed from javap (int math, overflow included). */
	private static int javapFormula(int level) {
		if (level >= 30) {
			return 112 + (level - 30) * 9;
		}
		return level >= 15 ? 37 + (level - 15) * 5 : 7 + level * 2;
	}

	@Test
	void vanillaMatchesTheGameAndTheDesignTable() {
		for (int level = 0; level <= 10_000; level++) {
			assertEquals(javapFormula(level), ExperienceCurve.vanillaPointsToNext(level), "level " + level);
			long design = level <= 15 ? 2L * level + 7 : level <= 30 ? 5L * level - 38 : 9L * level - 158;
			assertEquals(design, ExperienceCurve.vanillaPointsToNext(level), "DESIGN §3 table, level " + level);
		}
	}

	@Test
	void flattenedLevelsCostTheFixedPoints() {
		for (int level : List.of(30, 31, 49, 99, 9_999, 1_000_000, MAX - 1, MAX)) {
			assertEquals(112, ExperienceCurve.pointsToNext(DEFAULTS, level), "level " + level);
		}
	}

	@Test
	void belowTheThresholdIsVanilla() {
		for (int level = 0; level < 30; level++) {
			assertEquals(javapFormula(level), ExperienceCurve.pointsToNext(DEFAULTS, level), "level " + level);
		}
		assertEquals(107, ExperienceCurve.pointsToNext(DEFAULTS, 29));
	}

	@Test
	void totalsAboveThirtyAreLinear() {
		assertEquals(1395, ExperienceCurve.totalPoints(DEFAULTS, 30));
		for (int level : List.of(31, 32, 50, 100, 10_000, MAX)) {
			assertEquals(1395 + 112L * (level - 30), ExperienceCurve.totalPoints(DEFAULTS, level), "level " + level);
		}
	}

	@Test
	void flattenFromZeroFixesEveryLevel() {
		for (int level : List.of(0, 1, 15, 30, 1000)) {
			assertEquals(50, ExperienceCurve.pointsToNext(FLAT_FROM_ZERO, level));
			assertEquals(50L * level, ExperienceCurve.totalPoints(FLAT_FROM_ZERO, level));
		}
	}

	@Test
	void disabledIsVanillaPastThirty() {
		assertEquals(121, ExperienceCurve.pointsToNext(VANILLA, 31));
		assertEquals(javapFormula(9_999), ExperienceCurve.pointsToNext(VANILLA, 9_999));
	}

	@Test
	void vanillaOverflowIsClampedNeverNegative() {
		int clamp = ExperienceCurve.VANILLA_CLAMP_LEVEL;
		assertEquals(238_609_312, clamp);
		assertTrue(javapFormula(clamp) < 0, "the game's own formula overflows here");
		assertEquals(2_147_483_641, ExperienceCurve.pointsToNext(VANILLA, clamp - 1));
		assertEquals(MAX, ExperienceCurve.pointsToNext(VANILLA, clamp));
		assertEquals(MAX, ExperienceCurve.pointsToNext(VANILLA, MAX));
		assertTrue(ExperienceCurve.totalPoints(VANILLA, MAX) > 0);
	}

	@Test
	void closedFormSumsMatchBruteForce() {
		List<Integer> checkpoints = List.of(0, 1, 14, 15, 16, 29, 30, 31, 1000, 1_000_000, 100_000_000,
				ExperienceCurve.VANILLA_CLAMP_LEVEL - 1, ExperienceCurve.VANILLA_CLAMP_LEVEL,
				ExperienceCurve.VANILLA_CLAMP_LEVEL + 10);
		int last = checkpoints.get(checkpoints.size() - 1);
		long vanilla = 0;
		long defaults = 0;
		int next = 0;
		for (int level = 0; level <= last; level++) {
			if (level == checkpoints.get(next)) {
				assertEquals(vanilla, ExperienceCurve.totalPoints(VANILLA, level), "vanilla total at " + level);
				assertEquals(defaults, ExperienceCurve.totalPoints(DEFAULTS, level), "default total at " + level);
				next++;
			}
			vanilla += ExperienceCurve.pointsToNext(VANILLA, level);
			defaults += ExperienceCurve.pointsToNext(DEFAULTS, level);
		}
		assertEquals(checkpoints.size(), next);
	}

	@Test
	void positionsRoundTripLosslessly() {
		for (ExperienceSection section : List.of(DEFAULTS, VANILLA, FLAT_FROM_ZERO)) {
			for (int level : List.of(0, 1, 15, 16, 29, 30, 31, 1000, ExperienceCurve.VANILLA_CLAMP_LEVEL, MAX - 1)) {
				int needed = ExperienceCurve.pointsToNext(section, level);
				for (int points : List.of(0, 1, needed - 1)) {
					Position position = new Position(level, points);
					long total = ExperienceCurve.totalPoints(section, position);
					assertEquals(position, ExperienceCurve.fromTotal(section, total), section + " " + position);
				}
			}
		}
	}

	@Test
	void pointsAcrossThirtyLandOnTheRightLevel() {
		long start = ExperienceCurve.totalPoints(DEFAULTS, 29);
		long gained = 107 + 112 * 3 + 5;
		assertEquals(new Position(33, 5), ExperienceCurve.fromTotal(DEFAULTS, start + gained));
		assertEquals(new Position(29, 0), ExperienceCurve.fromTotal(DEFAULTS, start + gained - gained));
	}

	@Test
	void addThenRemoveIsLossless() {
		for (int level : List.of(5, 25, 29, 30, 40, 5_000)) {
			Position before = new Position(level, 3);
			long total = ExperienceCurve.totalPoints(DEFAULTS, before);
			for (long n : List.of(1L, 112L, 500L, 100_000L)) {
				Position up = ExperienceCurve.fromTotal(DEFAULTS, total + n);
				long back = ExperienceCurve.totalPoints(DEFAULTS, up) - n;
				assertEquals(before, ExperienceCurve.fromTotal(DEFAULTS, back), "level " + level + " +/- " + n);
			}
		}
	}

	@Test
	void outOfRangeInputsClampInsteadOfFailing() {
		assertEquals(new Position(0, 0), ExperienceCurve.fromTotal(DEFAULTS, -5));
		assertEquals(0, ExperienceCurve.totalPoints(DEFAULTS, -3));
		assertEquals(7, ExperienceCurve.pointsToNext(VANILLA, -1));
		assertEquals(new Position(MAX, 111), ExperienceCurve.fromTotal(DEFAULTS, Long.MAX_VALUE));
		assertEquals(new Position(MAX, MAX - 1), ExperienceCurve.fromTotal(VANILLA, Long.MAX_VALUE));
	}

	/** What every vanilla reader computes ({@code /xp query}, the client bar): {@code Mth.floor(progress * (float) n)}. */
	private static int vanillaFloor(float progress, int needed) {
		return (int) Math.floor(progress * (float) needed);
	}

	private static void assertExactAt(int needed, int points) {
		float stored = ExperienceCurve.storedProgress(points, needed);
		String at = points + "/" + needed;
		assertTrue(stored >= 0 && stored < 1, "stored progress in [0, 1) at " + at + ": " + stored);
		assertEquals(points, vanillaFloor(stored, needed), "vanilla floor of our form at " + at);
		double ahead = (double) stored * needed - points;
		assertTrue(ahead >= 0 && ahead <= 0.125, "bar never visibly ahead at " + at + ": +" + ahead + " points");
		assertEquals(points, ExperienceCurve.readPoints(stored, needed), "readPoints of our form at " + at);
		assertEquals(points, ExperienceCurve.readPoints((float) points / (float) needed, needed),
				"readPoints of vanilla's p/n form at " + at);
	}

	@Test
	void storedProgressReadsBackExactlyForEverySmallCost() {
		for (int needed = 1; needed <= 2_000; needed++) {
			for (int points = 0; points < needed; points++) {
				assertExactAt(needed, points);
			}
		}
	}

	@Test
	void storedProgressReadsBackExactlyUpToTheFloatLimit() {
		Random random = new Random(42);
		int limit = ExperienceCurve.EXACT_PROGRESS_LIMIT;
		for (int needed : List.of(112, 1_000_000, 233_489, limit - 1, limit)) {
			for (int points : List.of(0, 1, needed / 2, needed - 2, needed - 1)) {
				assertExactAt(needed, Math.max(0, points));
			}
			for (int i = 0; i < 20_000; i++) {
				assertExactAt(needed, random.nextInt(needed));
			}
		}
	}

	@Test
	void zeroPointsIsAnEmptyBar() {
		for (int needed : List.of(1, 7, 112, ExperienceCurve.EXACT_PROGRESS_LIMIT)) {
			assertEquals(0.0F, ExperienceCurve.storedProgress(0, needed), "needed " + needed);
		}
	}

	@Test
	void exactProgressFitsOnlyUpToTwoToTheTwentyOne() {
		assertTrue(ExperienceCurve.exactProgressFits(1));
		assertTrue(ExperienceCurve.exactProgressFits(1 << 21));
		assertFalse(ExperienceCurve.exactProgressFits((1 << 21) + 1));
		assertFalse(ExperienceCurve.exactProgressFits(MAX));
		assertTrue(ExperienceSection.MAX_FIXED_POINTS_PER_LEVEL <= ExperienceCurve.EXACT_PROGRESS_LIMIT,
				"every configurable fixed cost must be exact");
	}

	@Test
	void readPointsClampsBadProgress() {
		assertEquals(0, ExperienceCurve.readPoints(-0.5F, 112));
		assertEquals(111, ExperienceCurve.readPoints(1.0F, 112));
		assertEquals(111, ExperienceCurve.readPoints(7.0F, 112));
		assertEquals(0, ExperienceCurve.readPoints(Float.NaN, 112));
	}
}
