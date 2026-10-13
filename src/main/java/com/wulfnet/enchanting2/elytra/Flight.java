package com.wulfnet.enchanting2.elytra;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.effect.ModEffects;
import com.wulfnet.enchanting2.effect.WornEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;

/**
 * Flight (Phase 10cGB, owner 2026-10-12): while gliding, holding jump does a share of a firework rocket's own boost each
 * tick, 1/5 per level, so V is exactly a rocket held down; 1 elytra durability per second of thrust. A rocket pulls the
 * velocity toward 1.5 x look (plus 0.1 x look) by half the gap each tick, which is why it can climb, and a share of that
 * never brakes a flyer below the rocket's cruise. Speed scales the target, as it does a rocket's. The push runs in the
 * glide physics on both sides (the client moves its own player); the server only charges durability. No packet: the
 * server has the client's input.
 */
public final class Flight {
	/** Flight X's share, two rockets' pull: it reaches the target in one tick; more would overshoot and oscillate. */
	static final double MAX_SHARE = 2.0;
	/** A rocket's pull per tick toward its target (vanilla's 0.5). */
	private static final double ROCKET_PULL = 0.5;
	/** A rocket's target along the look (vanilla's 1.5). */
	private static final double ROCKET_TARGET = 1.5;
	/** A rocket's extra push along the look each tick (vanilla's 0.1). */
	private static final double ROCKET_PUSH = 0.1;
	/** Thrust ticks per point of durability (owner: 1 per second). */
	static final int TICKS_PER_DURABILITY = 20;

	private Flight() {
	}

	/** Whether {@code entity} holds jump: the client's own key, or the input the client last sent the server. */
	public static boolean holdsJump(LivingEntity entity) {
		return entity instanceof ServerPlayer player ? player.getLastClientInput().jump() : entity.isJumping();
	}

	/** {@code entity}'s worn share of a rocket per tick, capped at Flight X's; 0 without Flight. */
	static double thrust(LivingEntity entity) {
		double thrust = WornEffects.sum(entity, ModEffects.FLIGHT_THRUST);
		if (!Double.isFinite(thrust)) {
			Enchanting2.LOGGER.warn("flight_thrust on {} sums to {}; ignoring it (a datapack's value?)",
					entity.getStringUUID(), thrust);
			return 0.0;
		}
		return Math.clamp(thrust, 0.0, MAX_SHARE);
	}

	/** One glide tick's {@code velocity} for {@code flyer}: its share of a rocket's boost while a player holds jump. */
	public static Vec3 push(LivingEntity flyer, Vec3 velocity) {
		if (!(flyer instanceof Player) || !holdsJump(flyer)) {
			return velocity;
		}
		double share = thrust(flyer);
		if (share == 0.0) {
			return velocity;
		}
		double factor = GlideSpeed.factor(flyer);
		Vec3 look = flyer.getLookAngle();
		Vec3 rocket = velocity.add(look.scale(ROCKET_PUSH * factor))
				.add(look.scale(ROCKET_TARGET * factor).subtract(velocity).scale(ROCKET_PULL));
		return velocity.add(rocket.subtract(velocity).scale(share));
	}

	/** Server: whether {@code entity} spent this glide tick thrusting (still gliding, Flight worn, jump held). */
	public static boolean thrusting(LivingEntity entity) {
		return entity.level() instanceof ServerLevel && entity instanceof Player && entity.isFallFlying()
				&& holdsJump(entity) && thrust(entity) > 0.0;
	}

	/** Server: one point of durability off the worn Flight elytra, after {@link #TICKS_PER_DURABILITY} thrust ticks. */
	public static void wear(LivingEntity entity, int thrustTicks) {
		if (thrustTicks % TICKS_PER_DURABILITY != 0) {
			return;
		}
		ItemStack elytra = entity.getItemBySlot(EquipmentSlot.CHEST);
		if (EnchantmentHelper.has(elytra, ModEffects.FLIGHT_THRUST)) {
			elytra.hurtAndBreak(1, entity, EquipmentSlot.CHEST);
		}
	}
}
