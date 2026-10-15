package com.wulfnet.enchanting2.shield;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.effect.ModEffects;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.phys.Vec3;

/**
 * Deflect (Phase 10cCB, owner 2026-10-12): a projectile a raised Deflect shield would block flies back at its shooter
 * at the same speed instead of hitting, owned by the blocker (kill credit, arrows not picked up), once per projectile.
 * It goes through vanilla's deflection path (the one breezes use), so the projectile never hits and is never
 * discarded, whatever its type. Server only: the roll is random, and the server's velocity reaches the client.
 */
public final class ShieldDeflect {
	/** The projectiles a Deflect shield sends back (datapacks can add to it). */
	public static final TagKey<EntityType<?>> DEFLECTABLE = TagKey.create(Registries.ENTITY_TYPE,
			Identifier.fromNamespaceAndPath(Enchanting2.MOD_ID, "deflectable"));
	/** Our deflection, told apart from vanilla's by identity in {@link #afterDeflect}. */
	public static final ProjectileDeflection AT_SHOOTER = ShieldDeflect::atShooter;

	private ShieldDeflect() {
	}

	/**
	 * What {@code target} does to {@code projectile} about to hit it: {@link #AT_SHOOTER} when it's a living entity
	 * whose raised shield would block the projectile and the shield's Deflect chance wins this projectile's only roll,
	 * else {@link ProjectileDeflection#NONE}.
	 */
	public static ProjectileDeflection deflection(Entity target, Projectile projectile) {
		if (!(target instanceof LivingEntity blocker) || !(target.level() instanceof ServerLevel level)
				//? if >=26.1 {
				/*|| !projectile.is(DEFLECTABLE)) {
				*///?} else
				|| !projectile.getType().is(DEFLECTABLE)) {
			return ProjectileDeflection.NONE;
		}
		ItemStack shield = blocker.getItemBlockingWith();
		if (shield == null) {
			return ProjectileDeflection.NONE;
		}
		double chance = ShieldEffects.sumOnItem(shield, ModEffects.DEFLECT_CHANCE);
		DeflectState state = (DeflectState) projectile;
		if (chance <= 0.0 || state.enchanting2$deflectRolled() || !wouldBlock(blocker, level, shield, projectile)) {
			return ProjectileDeflection.NONE;
		}
		state.enchanting2$markDeflectRolled();
		if (!Double.isFinite(chance)) {
			Enchanting2.LOGGER.warn("deflect_chance sums to {} on {}'s shield; ignoring it (a datapack's value?)", chance,
					blocker.getStringUUID());
			return ProjectileDeflection.NONE;
		}
		return blocker.getRandom().nextDouble() < chance ? AT_SHOOTER : ProjectileDeflection.NONE;
	}

	/**
	 * Whether vanilla's {@code applyItemBlocking} would block {@code projectile}'s hit: not a piercing arrow, not a
	 * damage type the shield lets through, and inside its blocking angle. Arrows are judged as arrow damage, anything
	 * else as a thrown projectile; both are {@code #is_projectile}, which is what shields care about.
	 */
	private static boolean wouldBlock(LivingEntity blocker, ServerLevel level, ItemStack shield, Projectile projectile) {
		BlocksAttacks blocking = shield.get(DataComponents.BLOCKS_ATTACKS);
		if (blocking == null || projectile instanceof AbstractArrow arrow && arrow.getPierceLevel() > 0) {
			return false;
		}
		DamageSource source = projectile instanceof AbstractArrow arrow
				? level.damageSources().arrow(arrow, projectile.getOwner())
				: level.damageSources().thrown(projectile, projectile.getOwner());
		// 26.1 turned bypassed_by from a damage type tag into a HolderSet (vanilla LivingEntity checks it the same way).
		//? if >=26.1 {
		/*if (blocking.bypassedBy().map(types -> types.contains(source.typeHolder())).orElse(false)) {
		*///?} else
		if (blocking.bypassedBy().map(source::is).orElse(false)) {
			return false;
		}
		Vec3 view = blocker.calculateViewVector(0.0F, blocker.getYHeadRot());
		Vec3 toward = projectile.position().subtract(blocker.position());
		double angle = Math.acos(new Vec3(toward.x, 0.0, toward.z).normalize().dot(view));
		return blocking.resolveBlockedDamage(source, 1.0F, angle) > 0.0F;
	}

	/**
	 * Same speed, aimed at the middle of the shooter with the projectile's drop over that distance added back; with no
	 * shooter left in this level, straight back the way it came. Rotation and sync as vanilla's {@code REVERSE}.
	 */
	private static void atShooter(Projectile projectile, Entity deflector, RandomSource random) {
		Vec3 velocity = projectile.getDeltaMovement();
		double speed = velocity.length();
		if (speed < 1.0E-7) {
			return;
		}
		Entity shooter = projectile.getOwner();
		Vec3 direction;
		if (shooter != null && shooter.isAlive() && shooter != deflector && shooter.level() == projectile.level()) {
			Vec3 toward = shooter.position().add(0.0, shooter.getBbHeight() * 0.5, 0.0).subtract(projectile.position());
			double ticks = toward.length() / speed;
			direction = toward.add(0.0, 0.5 * projectile.getGravity() * ticks * ticks, 0.0).normalize();
		} else {
			direction = velocity.scale(-1.0).normalize();
		}
		Vec3 back = direction.scale(speed);
		projectile.setDeltaMovement(back);
		projectile.setYRot((float) (Mth.atan2(back.x, back.z) * Mth.RAD_TO_DEG));
		projectile.setXRot((float) (Mth.atan2(back.y, back.horizontalDistance()) * Mth.RAD_TO_DEG));
		projectile.yRotO = projectile.getYRot();
		projectile.xRotO = projectile.getXRot();
		projectile.needsSync = true;
	}

	/**
	 * After vanilla's {@code deflect} put the old owner back: the blocker owns the projectile now, an arrow can't be
	 * picked up, and the shield answers as for a block (its sound; 1 durability on a player's, Unbreaking applies).
	 */
	public static void afterDeflect(Projectile projectile, Entity deflector) {
		if (!(deflector instanceof LivingEntity blocker) || !(blocker.level() instanceof ServerLevel level)) {
			return;
		}
		projectile.setOwner(blocker);
		if (projectile instanceof AbstractArrow arrow) {
			arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		}
		ItemStack shield = blocker.getItemBlockingWith();
		if (shield == null) {
			Enchanting2.LOGGER.debug("{} deflected {} but no longer holds a raised shield", blocker.getStringUUID(),
					projectile.getStringUUID());
			return;
		}
		BlocksAttacks blocking = shield.get(DataComponents.BLOCKS_ATTACKS);
		if (blocking != null) {
			blocking.onBlocked(level, blocker);
		}
		if (blocker instanceof Player) {
			shield.hurtAndBreak(1, blocker, blocker.getUsedItemHand());
		}
	}
}
