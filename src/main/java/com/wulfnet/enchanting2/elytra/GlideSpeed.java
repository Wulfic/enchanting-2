package com.wulfnet.enchanting2.elytra;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.effect.ModEffects;
import com.wulfnet.enchanting2.effect.WornEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Speed (Phase 10cGA, owner 2026-10-10): +10% elytra flight speed per level, gliding and firework boosts alike. Both
 * run on the client for its own player, so this reads only synced data (the worn elytra's enchantments).
 */
public final class GlideSpeed {
	private GlideSpeed() {
	}

	/** The speed factor k for {@code flyer}: 1 without Speed. */
	public static double factor(LivingEntity flyer) {
		double bonus = WornEffects.sum(flyer, ModEffects.GLIDE_SPEED);
		if (bonus == 0.0) {
			return 1.0;
		}
		if (!Double.isFinite(bonus)) {
			Enchanting2.LOGGER.warn("glide_speed on {} sums to {}; ignoring it (a datapack's value?)",
					flyer.getStringUUID(), bonus);
			return 1.0;
		}
		return GlideMath.factor(bonus);
	}

	/** A rocket's new velocity {@code boosted} for {@code flyer}, pulled toward 1.7 × k along the look instead of 1.7. */
	public static Vec3 rocketBoost(LivingEntity flyer, Vec3 boosted) {
		double factor = factor(flyer);
		return factor == 1.0 ? boosted
				: boosted.add(flyer.getLookAngle().scale(GlideMath.ROCKET_PULL * (factor - 1.0)));
	}
}
