package com.wulfnet.enchanting2.effect;

import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FluidState;

/**
 * Phase 10cB, the boots that change what an entity can stand on. Both are read on both sides: the client moves its
 * own player, and the server repeats the move to check it, so they must agree.
 */
public final class Footing {
	/**
	 * Lava a walker can still be in and stand: {@code LiquidBlock}'s stable shape puts the feet half a block up, under a
	 * source's surface (8/9), so a standing walker's lava height is about 0.39. Deeper means it sank, and swims back up
	 * with lava's own physics until it's above the shape again.
	 */
	static final double SURFACE_DEPTH = 0.5;

	private Footing() {
	}

	/**
	 * Lava Walking: {@code entity} stands on {@code fluid} as a strider does, and moves at ground speed in it, when the
	 * fluid is lava, the entity wears {@code enchanting2:lava_walker}, isn't sneaking and hasn't sunk.
	 */
	public static boolean standsOnLava(LivingEntity entity, FluidState fluid) {
		return fluid.is(FluidTags.LAVA) && !entity.isDescending()
				&& entity.getFluidHeight(FluidTags.LAVA) <= SURFACE_DEPTH
				&& WornEffects.has(entity, ModEffects.LAVA_WALKER);
	}

	/** Snow Walker: the leather-boots effect on powder snow (walk on it, climb it, never freeze), from any boots. */
	public static boolean walksOnPowderSnow(Entity entity) {
		return entity instanceof LivingEntity living && WornEffects.has(living, ModEffects.POWDER_SNOW_WALKER);
	}
}
