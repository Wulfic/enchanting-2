package com.wulfnet.enchanting2.fortune;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MarkMovesTest {
	/** A set of marked positions; the unreadable variant answers "placed" everywhere and ignores changes. */
	private static final class SetStore implements MarkMoves.Store {
		final Set<Long> marked = new HashSet<>();
		final Set<Long> unreadable = new HashSet<>();

		@Override
		public boolean isMarked(long pos) {
			return unreadable.contains(pos) || marked.contains(pos);
		}

		@Override
		public void set(long pos, boolean placed) {
			if (unreadable.contains(pos)) {
				return;
			}
			if (placed) {
				marked.add(pos);
			} else {
				marked.remove(pos);
			}
		}
	}

	private static long[] column(long start, int length, long step) {
		long[] positions = new long[length];
		for (int i = 0; i < length; i++) {
			positions[i] = start + i * step;
		}
		return positions;
	}

	/** A 12-block column pushed up by one: every source but the bottom is also a destination. */
	@Test
	void anOverlappingColumnKeepsEveryMark() {
		SetStore store = new SetStore();
		long[] from = column(60, 12, 1);
		long[] to = column(61, 12, 1);
		for (long pos : from) {
			store.marked.add(pos);
		}
		MarkMoves.move(store, from, to, new long[0]);
		assertEquals(12, store.marked.size());
		assertFalse(store.isMarked(60));
		for (long pos : to) {
			assertTrue(store.isMarked(pos), "position " + pos);
		}
	}

	/** Pulled back down (sticky piston): sources now trail the destinations. */
	@Test
	void aPulledColumnKeepsEveryMark() {
		SetStore store = new SetStore();
		long[] from = column(61, 3, 1);
		long[] to = column(60, 3, 1);
		for (long pos : from) {
			store.marked.add(pos);
		}
		MarkMoves.move(store, from, to, new long[0]);
		assertEquals(Set.of(60L, 61L, 62L), store.marked);
	}

	@Test
	void onlyMarkedSourcesCarryAMarkAndStaleDestinationsAreCleared() {
		SetStore store = new SetStore();
		store.marked.add(10L);
		store.marked.add(21L);
		// 10 (placed) → 11, 20 (natural) → 21, which held a stale mark.
		MarkMoves.move(store, new long[] {10, 20}, new long[] {11, 21}, new long[0]);
		assertEquals(Set.of(11L), store.marked, "a natural block stays natural when moved");
	}

	@Test
	void destroyedPositionsAreCleared() {
		SetStore store = new SetStore();
		store.marked.add(5L);
		store.marked.add(99L);
		MarkMoves.move(store, new long[] {4}, new long[] {5}, new long[] {99});
		assertEquals(Set.of(), store.marked);
	}

	/** Fail closed: a block leaving an unreadable chunk counts as placed where it lands. */
	@Test
	void aBlockFromAnUnreadableChunkArrivesPlaced() {
		SetStore store = new SetStore();
		store.unreadable.add(1L);
		MarkMoves.move(store, new long[] {1}, new long[] {2}, new long[0]);
		assertTrue(store.isMarked(2));
		assertTrue(store.isMarked(1), "the unreadable chunk ignores the clear");
	}

	@Test
	void mismatchedArraysAreRefused() {
		SetStore store = new SetStore();
		store.marked.add(1L);
		assertThrows(IllegalArgumentException.class, () -> MarkMoves.move(store, new long[] {1}, new long[0], new long[0]));
		assertEquals(Set.of(1L), store.marked, "a refused move must not have cleared anything");
	}
}
