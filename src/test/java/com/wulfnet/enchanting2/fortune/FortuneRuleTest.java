package com.wulfnet.enchanting2.fortune;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wulfnet.enchanting2.fortune.FortuneRule.Decision;
import com.wulfnet.enchanting2.fortune.FortuneRule.Origin;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class FortuneRuleTest {
	private static final SilkFortuneMode SILK_WINS = SilkFortuneMode.SILK_WINS;
	private static final SilkFortuneMode MULTIPLIES = SilkFortuneMode.FORTUNE_MULTIPLIES_SILK;

	private static Decision decide(int level, boolean silk, SilkFortuneMode mode, boolean tagged, boolean silkTagged,
			boolean excluded, Origin origin, boolean silkChanged) {
		return FortuneRule.decide(level, silk, mode, tagged, silkTagged, excluded, () -> origin, () -> silkChanged);
	}

	@Test
	void naturalTaggedBlockWithFortuneMultiplies() {
		assertEquals(Decision.MULTIPLY, decide(5, false, SILK_WINS, true, false, false, Origin.NATURAL, false));
		assertEquals(Decision.MULTIPLY, decide(1, false, MULTIPLIES, true, false, false, Origin.NATURAL, true));
	}

	@Test
	void eachGateRefusesOnItsOwn() {
		assertEquals(Decision.NO_FORTUNE, decide(0, false, SILK_WINS, true, false, false, Origin.NATURAL, false));
		assertEquals(Decision.EXCLUDED, decide(5, false, SILK_WINS, true, true, true, Origin.NATURAL, false));
		assertEquals(Decision.NOT_TAGGED, decide(5, false, SILK_WINS, false, false, false, Origin.NATURAL, false));
		assertEquals(Decision.PLACED, decide(5, false, SILK_WINS, true, false, false, Origin.PLACED, false));
		assertEquals(Decision.UNREADABLE, decide(5, false, SILK_WINS, true, false, false, Origin.UNREADABLE, false));
	}

	/** Owner, 2026-10-08: SILK_WINS still multiplies when Silk Touch didn't change the drop (logs, sand). */
	@Test
	void silkWinsOnlyWhenSilkChangedTheDrop() {
		assertEquals(Decision.MULTIPLY, decide(5, true, SILK_WINS, true, false, false, Origin.NATURAL, false));
		assertEquals(Decision.SILK_CHANGED_DROP, decide(5, true, SILK_WINS, true, false, false, Origin.NATURAL, true));
		// The silk tag (ores) only counts under FORTUNE_MULTIPLIES_SILK.
		assertEquals(Decision.NOT_TAGGED, decide(5, true, SILK_WINS, false, true, false, Origin.NATURAL, true));
	}

	@Test
	void fortuneMultipliesSilkCoversTheSilkTagButStillNeedsANaturalBlock() {
		assertEquals(Decision.MULTIPLY, decide(5, true, MULTIPLIES, false, true, false, Origin.NATURAL, true));
		assertEquals(Decision.MULTIPLY, decide(5, true, MULTIPLIES, true, false, false, Origin.NATURAL, true));
		assertEquals(Decision.PLACED, decide(5, true, MULTIPLIES, false, true, false, Origin.PLACED, true));
		assertEquals(Decision.UNREADABLE, decide(5, true, MULTIPLIES, false, true, false, Origin.UNREADABLE, true));
		assertEquals(Decision.EXCLUDED, decide(5, true, MULTIPLIES, false, true, true, Origin.NATURAL, false));
		// Without Silk Touch on the tool, the silk tag means nothing.
		assertEquals(Decision.NOT_TAGGED, decide(5, false, MULTIPLIES, false, true, false, Origin.NATURAL, false));
	}

	/** Every combination: a placed or unreadable block, or a disqualified one, never multiplies. */
	@Test
	void truthTable() {
		boolean[] bools = {false, true};
		for (int level : new int[] {0, 1, 5}) {
			for (boolean silk : bools) {
				for (SilkFortuneMode mode : SilkFortuneMode.values()) {
					for (boolean tagged : bools) {
						for (boolean silkTagged : bools) {
							for (boolean excluded : bools) {
								for (Origin origin : Origin.values()) {
									for (boolean changed : bools) {
										Decision d = decide(level, silk, mode, tagged, silkTagged, excluded, origin, changed);
										boolean eligibleTag = tagged || (silk && mode == MULTIPLIES && silkTagged);
										boolean silkBlocks = silk && mode == SILK_WINS && changed;
										boolean expected = level > 0 && !excluded && eligibleTag && origin == Origin.NATURAL
												&& !silkBlocks;
										assertEquals(expected, d == Decision.MULTIPLY, level + " " + silk + " " + mode + " "
												+ tagged + " " + silkTagged + " " + excluded + " " + origin + " " + changed
												+ " → " + d);
									}
								}
							}
						}
					}
				}
			}
		}
	}

	/** The re-roll and the chunk lookup cost something, so they're only asked once every cheap check passed. */
	@Test
	void expensiveFactsAreAskedLastAndOnlyWhenNeeded() {
		AtomicBoolean originAsked = new AtomicBoolean();
		AtomicBoolean rerolled = new AtomicBoolean();
		Supplier<Origin> origin = () -> {
			originAsked.set(true);
			return Origin.NATURAL;
		};
		BooleanSupplier changed = () -> {
			rerolled.set(true);
			return false;
		};
		FortuneRule.decide(5, true, SILK_WINS, false, false, false, origin, changed);
		assertFalse(originAsked.get() || rerolled.get(), "asked for an untagged block");
		FortuneRule.decide(5, false, SILK_WINS, true, false, false, origin, changed);
		assertTrue(originAsked.get());
		assertFalse(rerolled.get(), "re-rolled for a tool without Silk Touch");
		FortuneRule.decide(5, true, MULTIPLIES, true, false, false, origin, changed);
		assertFalse(rerolled.get(), "re-rolled under FORTUNE_MULTIPLIES_SILK");
		FortuneRule.decide(5, true, SILK_WINS, true, false, false, () -> Origin.PLACED, changed);
		assertFalse(rerolled.get(), "re-rolled for a placed block");
		FortuneRule.decide(5, true, SILK_WINS, true, false, false, origin, changed);
		assertTrue(rerolled.get());
	}

	@Test
	void multiplierMatchesVanillaOreDrops() {
		// nextInt(level + 2) − 1, floored at 0, plus 1: every roll at Fortune V.
		int[] expected = {1, 1, 2, 3, 4, 5, 6};
		for (int roll = 0; roll < 7; roll++) {
			int fixed = roll;
			assertEquals(expected[roll], FortuneRule.multiplier(5, bound -> {
				assertEquals(7, bound);
				return fixed;
			}));
		}
		assertEquals(1, FortuneRule.multiplier(0, bound -> {
			throw new AssertionError("rolled without Fortune");
		}));
	}

	@Test
	void fortuneFiveAveragesTwentyTwoSevenths() {
		Random random = new Random(42);
		int samples = 200_000;
		long total = 0;
		int min = Integer.MAX_VALUE;
		int max = 0;
		for (int i = 0; i < samples; i++) {
			int m = FortuneRule.multiplier(5, random::nextInt);
			total += m;
			min = Math.min(min, m);
			max = Math.max(max, m);
		}
		assertEquals(22.0 / 7.0, (double) total / samples, 0.02);
		assertEquals(1, min);
		assertEquals(6, max);
	}
}
