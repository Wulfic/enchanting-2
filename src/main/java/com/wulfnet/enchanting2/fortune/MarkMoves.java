package com.wulfnet.enchanting2.fortune;

/**
 * Moves placed-block marks the way a piston moves blocks (DESIGN §6), over world positions, so a push across a chunk
 * border works. MC-free: positions are opaque {@code long}s ({@code BlockPos#asLong} on the MC side).
 */
public final class MarkMoves {
	/** Where marks live. An unreadable chunk answers "placed" and ignores changes. */
	public interface Store {
		boolean isMarked(long pos);

		void set(long pos, boolean placed);
	}

	/**
	 * Three passes, as in mcMMO: read every source, clear every source, destination and destroyed position, then mark
	 * each destination whose source was marked. A pushed column overlaps itself (each block moves into the next one's
	 * old position), so a single pass would clear marks it had just written. A destination whose source was natural
	 * ends up natural, so a stale mark there can't stick to the block that moved in.
	 *
	 * @param from      positions of the moved blocks
	 * @param to        their new positions, paired with {@code from} by index
	 * @param destroyed positions of blocks the move broke (they drop as items and are gone)
	 */
	public static void move(Store store, long[] from, long[] to, long[] destroyed) {
		if (from.length != to.length) {
			throw new IllegalArgumentException(from.length + " sources but " + to.length + " destinations");
		}
		boolean[] marked = new boolean[from.length];
		for (int i = 0; i < from.length; i++) {
			marked[i] = store.isMarked(from[i]);
		}
		for (long[] positions : new long[][] {from, to, destroyed}) {
			for (long pos : positions) {
				store.set(pos, false);
			}
		}
		for (int i = 0; i < to.length; i++) {
			if (marked[i]) {
				store.set(to[i], true);
			}
		}
	}

	private MarkMoves() {
	}
}
