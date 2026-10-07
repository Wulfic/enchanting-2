package com.wulfnet.enchanting2.anvil;

import static com.wulfnet.enchanting2.anvil.InvalidBookMode.APPLY_VALID;
import static com.wulfnet.enchanting2.anvil.InvalidBookMode.REJECT_ALL;
import static com.wulfnet.enchanting2.anvil.InvalidBookMode.STRIP_INVALID;
import static com.wulfnet.enchanting2.anvil.TransferRules.Fate.APPLY;
import static com.wulfnet.enchanting2.anvil.TransferRules.Fate.CONSUME_DOMINATED;
import static com.wulfnet.enchanting2.anvil.TransferRules.Fate.KEEP_ON_BOOK;
import static com.wulfnet.enchanting2.anvil.TransferRules.Fate.LOSE;
import static com.wulfnet.enchanting2.anvil.TransferRules.Fate.REFUSE;
import static com.wulfnet.enchanting2.anvil.TransferRules.Source.BOOK;
import static com.wulfnet.enchanting2.anvil.TransferRules.Source.ITEM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wulfnet.enchanting2.anvil.TransferRules.Fate;
import com.wulfnet.enchanting2.anvil.TransferRules.Source;
import com.wulfnet.enchanting2.enchantment.EnchantRules.Verdict;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The owner's Phase 6 answers (decisions.md 2026-10-07 "Anvil"), cell by cell. */
class TransferRulesTest {
	private static final List<Verdict> INVALID = List.of(Verdict.INVALID_LEVEL, Verdict.ABOVE_MAX,
			Verdict.NOT_APPLICABLE, Verdict.CONFLICT);

	@Test
	void okAlwaysApplies() {
		for (Source source : Source.values()) {
			for (InvalidBookMode mode : InvalidBookMode.values()) {
				assertEquals(APPLY, TransferRules.decide(source, mode, Verdict.OK), source + " " + mode);
			}
		}
	}

	@Test
	void invalidBookEntries() {
		for (Verdict verdict : INVALID) {
			assertEquals(REFUSE, TransferRules.decide(BOOK, REJECT_ALL, verdict), verdict.name());
			assertEquals(KEEP_ON_BOOK, TransferRules.decide(BOOK, APPLY_VALID, verdict), verdict.name());
			assertEquals(LOSE, TransferRules.decide(BOOK, STRIP_INVALID, verdict), verdict.name());
		}
	}

	/** There's no leftover item to return, so only strip_invalid lets a sacrifice's entry go. */
	@Test
	void invalidSacrificeEntries() {
		for (Verdict verdict : INVALID) {
			assertEquals(REFUSE, TransferRules.decide(ITEM, REJECT_ALL, verdict), verdict.name());
			assertEquals(REFUSE, TransferRules.decide(ITEM, APPLY_VALID, verdict), verdict.name());
			assertEquals(LOSE, TransferRules.decide(ITEM, STRIP_INVALID, verdict), verdict.name());
		}
	}

	@Test
	void dominatedEntriesAreNeverLostOrRefused() {
		assertEquals(CONSUME_DOMINATED, TransferRules.decide(BOOK, REJECT_ALL, Verdict.ALREADY_AT_LEVEL));
		assertEquals(KEEP_ON_BOOK, TransferRules.decide(BOOK, APPLY_VALID, Verdict.ALREADY_AT_LEVEL));
		assertEquals(CONSUME_DOMINATED, TransferRules.decide(BOOK, STRIP_INVALID, Verdict.ALREADY_AT_LEVEL));
		for (InvalidBookMode mode : InvalidBookMode.values()) {
			assertEquals(CONSUME_DOMINATED, TransferRules.decide(ITEM, mode, Verdict.ALREADY_AT_LEVEL), mode.name());
		}
	}

	/** The invariants the cells above add up to, so a new verdict or mode can't slip past them. */
	@Test
	void onlyStripInvalidLosesAndOnlyBooksKeep() {
		for (Source source : Source.values()) {
			for (InvalidBookMode mode : InvalidBookMode.values()) {
				for (Verdict verdict : Verdict.values()) {
					Fate fate = TransferRules.decide(source, mode, verdict);
					String cell = source + " " + mode + " " + verdict;
					if (mode != STRIP_INVALID) {
						assertNotEquals(LOSE, fate, cell);
					}
					if (source == ITEM) {
						assertNotEquals(KEEP_ON_BOOK, fate, cell);
					}
					assertEquals(verdict == Verdict.OK, fate == APPLY, cell);
					if (verdict == Verdict.ALREADY_AT_LEVEL) {
						assertTrue(EnumSet.of(KEEP_ON_BOOK, CONSUME_DOMINATED).contains(fate), cell);
					}
				}
			}
		}
	}

	@Test
	void nullsAreRejected() {
		assertThrows(NullPointerException.class, () -> TransferRules.decide(null, APPLY_VALID, Verdict.OK));
		assertThrows(NullPointerException.class, () -> TransferRules.decide(BOOK, null, Verdict.OK));
		assertThrows(NullPointerException.class, () -> TransferRules.decide(BOOK, APPLY_VALID, null));
	}
}
