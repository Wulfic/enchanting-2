package com.wulfnet.enchanting2.grindstone;

import java.util.Optional;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/** Implemented on {@code GrindstoneMenu} by {@code GrindstoneMenuMixin}. */
public interface GrindstoneMenuAccess {
	/** @return the server's outcome for the current inputs and pick; null on the client and before the first result */
	@Nullable
	GrindstoneOutcome enchanting2$outcome();

	/** The pick the server holds for this menu. Always empty on the client. */
	Optional<GrindstoneChoice> enchanting2$choice();

	/**
	 * Server: replace the pick and recompute the result. A pick that isn't a row for the current inputs gives no result,
	 * so a stale or forged one can't take anything the inputs don't allow.
	 */
	void enchanting2$choose(Optional<GrindstoneChoice> choice);

	/** Server: what an Extract or Cleanse take does. The result slot calls it; nothing else should. */
	void enchanting2$applyTake(Player player, GrindstoneOutcome taking);

	/**
	 * Our rules apply to this menu: it's the server's, or the client's while connected to a server running Enchanting 2.
	 * A client on a vanilla server keeps the vanilla grindstone (no plain books, no picks).
	 */
	boolean enchanting2$handledByUs();
}
