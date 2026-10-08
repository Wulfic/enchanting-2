package com.wulfnet.enchanting2.fortune;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PlacedMarksTest {
	@Test
	void everyPositionOfASectionRoundTrips() {
		PlacedMarks marks = PlacedMarks.EMPTY;
		for (int y = 16; y < 32; y++) {
			for (int z = 0; z < 16; z++) {
				for (int x = 0; x < 16; x++) {
					assertFalse(marks.isMarked(x, y, z));
					marks = marks.withMark(x, y, z);
					assertTrue(marks.isMarked(x, y, z));
				}
			}
		}
		assertEquals(4096, marks.count());
		assertFalse(marks.isMarked(0, 15, 0));
		assertFalse(marks.isMarked(15, 32, 15));
		for (int y = 16; y < 32; y++) {
			for (int z = 0; z < 16; z++) {
				for (int x = 0; x < 16; x++) {
					marks = marks.withoutMark(x, y, z);
				}
			}
		}
		assertSame(PlacedMarks.EMPTY, marks);
	}

	@Test
	void worldBottomAndTopSectionsAreSeparate() {
		PlacedMarks marks = PlacedMarks.EMPTY.withMark(3, -64, 4).withMark(3, 319, 4);
		assertTrue(marks.isMarked(3, -64, 4));
		assertTrue(marks.isMarked(3, 319, 4));
		assertFalse(marks.isMarked(3, -48, 4));
		assertFalse(marks.isMarked(3, 303, 4));
		assertFalse(marks.isMarked(3, 0, 4));
		assertEquals(2, marks.count());
		assertEquals(2, marks.sections().size());
	}

	@Test
	void changesNeverMutateTheOriginal() {
		PlacedMarks one = PlacedMarks.EMPTY.withMark(1, 1, 1);
		PlacedMarks two = one.withMark(2, 2, 2);
		assertFalse(one.isMarked(2, 2, 2));
		assertTrue(two.isMarked(1, 1, 1));
		PlacedMarks cleared = two.withoutMark(1, 1, 1);
		assertTrue(two.isMarked(1, 1, 1));
		assertFalse(cleared.isMarked(1, 1, 1));
		one.sections().get(0)[0] = -1L;
		assertEquals(1, one.count(), "sections() must hand out a copy");
	}

	@Test
	void redundantChangesReturnTheSameValue() {
		PlacedMarks marks = PlacedMarks.EMPTY.withMark(5, 70, 5);
		assertSame(marks, marks.withMark(5, 70, 5));
		assertSame(marks, marks.withoutMark(6, 70, 5));
		assertSame(PlacedMarks.EMPTY, PlacedMarks.EMPTY.withoutMark(0, 0, 0));
	}

	@Test
	void outOfChunkCoordinatesAreRefused() {
		assertThrows(IllegalArgumentException.class, () -> PlacedMarks.EMPTY.withMark(16, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> PlacedMarks.EMPTY.isMarked(0, 0, -1));
	}

	@Test
	void unreadableIsPlacedEverywhereAndIgnoresChanges() {
		PlacedMarks unreadable = PlacedMarks.unreadable("raw");
		assertTrue(unreadable.isUnreadable());
		assertFalse(unreadable.isEmpty(), "its raw data must still be saved");
		assertTrue(unreadable.isMarked(0, -64, 0));
		assertTrue(unreadable.isMarked(15, 319, 15));
		assertSame(unreadable, unreadable.withoutMark(0, 0, 0));
		assertSame(unreadable, unreadable.withMark(0, 0, 0));
		assertEquals("raw", unreadable.raw());
		assertThrows(IllegalStateException.class, PlacedMarks.EMPTY::raw);
	}

	@Test
	void ofSectionsDropsEmptySectionsAndChecksLength() {
		assertSame(PlacedMarks.EMPTY, PlacedMarks.ofSections(Map.of(3, new long[PlacedMarks.WORDS])));
		assertThrows(IllegalArgumentException.class, () -> PlacedMarks.ofSections(Map.of(0, new long[63])));
		long[] bits = new long[PlacedMarks.WORDS];
		bits[0] = 1L;
		PlacedMarks marks = PlacedMarks.ofSections(Map.of(-4, bits));
		bits[0] = 0L;
		assertTrue(marks.isMarked(0, -64, 0), "ofSections must copy its input");
	}

	@Test
	void equalityIsByContent() {
		assertEquals(PlacedMarks.EMPTY.withMark(1, 2, 3), PlacedMarks.EMPTY.withMark(1, 2, 3));
		assertEquals(PlacedMarks.EMPTY.withMark(1, 2, 3).hashCode(), PlacedMarks.EMPTY.withMark(1, 2, 3).hashCode());
		assertFalse(PlacedMarks.EMPTY.withMark(1, 2, 3).equals(PlacedMarks.EMPTY.withMark(1, 2, 4)));
		assertFalse(PlacedMarks.EMPTY.equals(PlacedMarks.unreadable("x")));
	}
}
