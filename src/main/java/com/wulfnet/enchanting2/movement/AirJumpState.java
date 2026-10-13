package com.wulfnet.enchanting2.movement;

/**
 * Phase 10cGC: whether a player has used Double Jump's air jump since it last touched the ground, held on
 * {@code Player} by {@code PlayerMixin}. Transient: a relog mid-air just allows one more.
 */
public interface AirJumpState {
	boolean enchanting2$airJumpUsed();

	void enchanting2$setAirJumpUsed(boolean used);
}
