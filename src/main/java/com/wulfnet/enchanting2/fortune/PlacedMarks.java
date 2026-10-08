package com.wulfnet.enchanting2.fortune;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/**
 * One chunk's placed blocks (DESIGN §6): the positions Fortune never multiplies. MC-free and immutable. Every change
 * returns a new value, because a chunk attachment is only saved when it's set again. Positions are chunk-local x and z
 * (0..15) and the world y. Storage is a 4096-bit set per 16-high section, so a chunk costs at most 512 bytes per
 * section however densely it's built.
 *
 * <p>{@link #unreadable} is the fail-closed state for data that couldn't be decoded: every position counts as placed,
 * changes are ignored, and the raw data is kept so it can be written back unchanged for a fixed version to read.
 */
public final class PlacedMarks {
	public static final PlacedMarks EMPTY = new PlacedMarks(Collections.emptyNavigableMap(), null);
	/** {@code long}s per section: 16 × 16 × 16 bits. */
	static final int WORDS = 64;

	/** Section Y → bits. Never holds an all-zero section. Never mutated after construction. */
	private final NavigableMap<Integer, long[]> sections;
	/** Non-null exactly for an unreadable value: the undecodable data, opaque here. */
	private final Object raw;

	private PlacedMarks(NavigableMap<Integer, long[]> sections, Object raw) {
		this.sections = sections;
		this.raw = raw;
	}

	/** Fail closed: data that couldn't be decoded. {@code raw} is kept as-is for writing back. */
	public static PlacedMarks unreadable(Object raw) {
		return new PlacedMarks(Collections.emptyNavigableMap(), Objects.requireNonNull(raw, "raw"));
	}

	/** From decoded sections. Arrays are copied, and all-zero sections are dropped. */
	static PlacedMarks ofSections(Map<Integer, long[]> decoded) {
		TreeMap<Integer, long[]> copy = new TreeMap<>();
		decoded.forEach((y, bits) -> {
			if (bits.length != WORDS) {
				throw new IllegalArgumentException("section " + y + " has " + bits.length + " words, expected " + WORDS);
			}
			if (!isZero(bits)) {
				copy.put(y, bits.clone());
			}
		});
		return copy.isEmpty() ? EMPTY : new PlacedMarks(Collections.unmodifiableNavigableMap(copy), null);
	}

	public boolean isUnreadable() {
		return raw != null;
	}

	/** The undecodable data of an {@link #unreadable} value. */
	public Object raw() {
		if (raw == null) {
			throw new IllegalStateException("only an unreadable value has raw data");
		}
		return raw;
	}

	/** Nothing marked and nothing to preserve, so no attachment needs to exist. */
	public boolean isEmpty() {
		return raw == null && sections.isEmpty();
	}

	/** Placed, or unknown because the data was unreadable. */
	public boolean isMarked(int x, int y, int z) {
		if (raw != null) {
			return true;
		}
		long[] bits = sections.get(sectionY(y));
		int bit = bit(x, y, z);
		return bits != null && (bits[bit >>> 6] & (1L << bit)) != 0;
	}

	public PlacedMarks withMark(int x, int y, int z) {
		return with(x, y, z, true);
	}

	public PlacedMarks withoutMark(int x, int y, int z) {
		return with(x, y, z, false);
	}

	/** Marked positions; 0 for an unreadable value. */
	public int count() {
		int count = 0;
		for (long[] bits : sections.values()) {
			for (long word : bits) {
				count += Long.bitCount(word);
			}
		}
		return count;
	}

	/** Section Y → a copy of its bits, for encoding. Empty for an unreadable value. */
	NavigableMap<Integer, long[]> sections() {
		TreeMap<Integer, long[]> copy = new TreeMap<>();
		sections.forEach((y, bits) -> copy.put(y, bits.clone()));
		return copy;
	}

	private PlacedMarks with(int x, int y, int z, boolean value) {
		if (raw != null) {
			return this;
		}
		int sectionY = sectionY(y);
		int bit = bit(x, y, z);
		long[] current = sections.get(sectionY);
		boolean set = current != null && (current[bit >>> 6] & (1L << bit)) != 0;
		if (set == value) {
			return this;
		}
		long[] bits = current == null ? new long[WORDS] : current.clone();
		bits[bit >>> 6] ^= 1L << bit;
		TreeMap<Integer, long[]> next = new TreeMap<>(sections);
		if (isZero(bits)) {
			next.remove(sectionY);
		} else {
			next.put(sectionY, bits);
		}
		return next.isEmpty() ? EMPTY : new PlacedMarks(Collections.unmodifiableNavigableMap(next), null);
	}

	private static int sectionY(int y) {
		return y >> 4;
	}

	private static int bit(int x, int y, int z) {
		checkLocal(x, z);
		return ((y & 15) << 8) | (z << 4) | x;
	}

	private static void checkLocal(int x, int z) {
		if ((x & ~15) != 0 || (z & ~15) != 0) {
			throw new IllegalArgumentException("chunk-local x and z must be 0..15, got " + x + ", " + z);
		}
	}

	private static boolean isZero(long[] bits) {
		for (long word : bits) {
			if (word != 0) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof PlacedMarks other) || !Objects.equals(raw, other.raw)
				|| !sections.keySet().equals(other.sections.keySet())) {
			return false;
		}
		for (Map.Entry<Integer, long[]> entry : sections.entrySet()) {
			if (!Arrays.equals(entry.getValue(), other.sections.get(entry.getKey()))) {
				return false;
			}
		}
		return true;
	}

	@Override
	public int hashCode() {
		int hash = Objects.hashCode(raw);
		for (Map.Entry<Integer, long[]> entry : sections.entrySet()) {
			hash = 31 * hash + entry.getKey() * 17 + Arrays.hashCode(entry.getValue());
		}
		return hash;
	}

	@Override
	public String toString() {
		return raw != null ? "PlacedMarks[unreadable]" : "PlacedMarks[" + count() + " in " + sections.size() + " sections]";
	}
}
