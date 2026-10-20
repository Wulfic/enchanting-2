package com.wulfnet.enchanting2.fortune;

import com.mojang.serialization.Codec;
import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.platform.Attachment;
import com.wulfnet.enchanting2.platform.Attachments;
import com.wulfnet.enchanting2.platform.Services;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * The placed-block tracker (DESIGN §6), stored per chunk in a Fabric attachment so it loads, unloads and saves with its
 * chunk. Server only. Every hook goes through here. A mark belongs to a position, so in-place conversions (dirt →
 * path, log → stripped) keep it. A chunk without the attachment holds only natural blocks (pre-install blocks count
 * as natural, DESIGN §2a).
 */
public final class PlacedBlocks {
	/** Chunk → its marks. Persistent, never synced. An emptied set is removed, so most chunks carry nothing. */
	public static final Attachment<PlacedMarks> CHUNK = Services.ATTACHMENTS.register(Attachments.Spec.persistent(
			Identifier.fromNamespaceAndPath(Enchanting2.MOD_ID, "placed_blocks"), PlacedMarksCodec.INSTANCE));
	/** A falling block that left a placed position carries the mark here, and writes it back where it lands. */
	public static final Attachment<Boolean> FALLING = Services.ATTACHMENTS.register(Attachments.Spec.persistent(
			Identifier.fromNamespaceAndPath(Enchanting2.MOD_ID, "placed_falling"), Codec.BOOL));

	private PlacedBlocks() {
	}

	/** Loads the class, which registers both attachment types, during mod init (before any world loads). */
	public static void register() {
		Enchanting2.LOGGER.debug("Placed-block attachments: {}, {}", CHUNK.id(), FALLING.id());
	}

	public static void mark(ServerLevel level, BlockPos pos) {
		setPlaced(level, pos, true);
	}

	public static void clear(ServerLevel level, BlockPos pos) {
		setPlaced(level, pos, false);
	}

	public static FortuneRule.Origin origin(ServerLevel level, BlockPos pos) {
		PlacedMarks marks = marks(level.getChunkAt(pos));
		if (marks.isUnreadable()) {
			Enchanting2.LOGGER.debug("Placed-block data unreadable in chunk {} of {}: {} counts as placed",
					level.getChunkAt(pos).getPos(), level.dimension().identifier(), pos);
			return FortuneRule.Origin.UNREADABLE;
		}
		return marks.isMarked(pos.getX() & 15, pos.getY(), pos.getZ() & 15) ? FortuneRule.Origin.PLACED
				: FortuneRule.Origin.NATURAL;
	}

	/** A piston move: see {@link MarkMoves#move}. {@code to} pairs with {@code from} by index. */
	public static void move(ServerLevel level, List<BlockPos> from, List<BlockPos> to, List<BlockPos> destroyed) {
		MarkMoves.move(new MarkMoves.Store() {
			@Override
			public boolean isMarked(long pos) {
				return origin(level, BlockPos.of(pos)) != FortuneRule.Origin.NATURAL;
			}

			@Override
			public void set(long pos, boolean placed) {
				PlacedBlocks.setPlaced(level, BlockPos.of(pos), placed);
			}
		}, packed(from), packed(to), packed(destroyed));
	}

	/** Whether a falling block entity carries a placed mark. */
	public static boolean isPlacedFalling(Entity entity) {
		return Boolean.TRUE.equals(FALLING.get(entity));
	}

	public static void setPlacedFalling(Entity entity) {
		FALLING.set(entity, Boolean.TRUE);
	}

	/** Marks or clears one position. A block arriving at a position replaces whatever mark was there. */
	public static void setPlaced(ServerLevel level, BlockPos pos, boolean placed) {
		LevelChunk chunk = level.getChunkAt(pos);
		PlacedMarks current = marks(chunk);
		int x = pos.getX() & 15;
		int z = pos.getZ() & 15;
		PlacedMarks next = placed ? current.withMark(x, pos.getY(), z) : current.withoutMark(x, pos.getY(), z);
		if (next == current) {
			return;
		}
		// set marks the chunk unsaved; mutating the stored value in place would not.
		if (next.isEmpty()) {
			CHUNK.remove(chunk);
		} else {
			CHUNK.set(chunk, next);
		}
	}

	private static PlacedMarks marks(LevelChunk chunk) {
		PlacedMarks marks = CHUNK.get(chunk);
		return marks == null ? PlacedMarks.EMPTY : marks;
	}

	private static long[] packed(List<BlockPos> positions) {
		return positions.stream().mapToLong(BlockPos::asLong).toArray();
	}
}
