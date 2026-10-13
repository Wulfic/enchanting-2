package com.wulfnet.enchanting2.movement;

import com.wulfnet.enchanting2.effect.ModEffects;
import com.wulfnet.enchanting2.effect.WornEffects;
import net.minecraft.world.entity.player.Player;

/**
 * Double Jump (Phase 10cGC, DESIGN §9): one extra jump in the air, ready again on landing. The client moves its own
 * player, so the client jumps; no packet is needed. The jump breaks the fall on both sides by itself: vanilla's
 * {@code checkFallDistanceAccumulation} caps the fall distance at 1 every tick the player isn't falling faster than
 * 0.5 blocks a tick, and the server can't police a client's movement beyond its usual checks anyway.
 */
public final class DoubleJump {
	private DoubleJump() {
	}

	/** Whether {@code player} may jump in the air now: boots on, airborne, free to move, not used since landing. */
	public static boolean canAirJump(Player player) {
		return !player.onGround() && !player.isInWater() && !player.isInLava() && !player.onClimbable()
				&& !player.isPassenger() && !player.getAbilities().flying && !player.isFallFlying()
				&& !((AirJumpState) player).enchanting2$airJumpUsed()
				&& WornEffects.has(player, ModEffects.DOUBLE_JUMP);
	}

	/** {@code player} jumps in the air (the caller has checked {@link #canAirJump}). */
	public static void jump(Player player) {
		player.jumpFromGround();
		player.resetFallDistance();
		((AirJumpState) player).enchanting2$setAirJumpUsed(true);
	}

	/** Each tick, both sides: back on the ground, in water or lava, or on a ladder, the air jump is ready again. */
	public static void tick(Player player) {
		if (player.onGround() || player.isInWater() || player.isInLava() || player.onClimbable()) {
			((AirJumpState) player).enchanting2$setAirJumpUsed(false);
		}
	}
}
