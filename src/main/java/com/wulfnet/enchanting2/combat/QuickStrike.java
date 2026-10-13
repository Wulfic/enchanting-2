package com.wulfnet.enchanting2.combat;

import com.mojang.datafixers.util.Pair;
import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.effect.ModEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.phys.Vec3;

/**
 * Quick Strike (Phase 10cFA, owner 2026-10-12): a landed melee hit has a chance to land again at once. The second hit
 * repeats the damage only: the same source and amount through {@code hurtServer}, past the i-frames the first one set,
 * with the victim's velocity put back. Fire Aspect, Knockback, other on-hit effects, the Critical Strike roll and
 * durability all happen outside {@code hurtServer}, so it never repeats them; Vampirism checks {@link #isRepeating()}.
 * It never chains: the repeat goes straight to {@code hurtServer}, and only the attack paths roll.
 */
public final class QuickStrike {
	private static final ThreadLocal<Boolean> REPEATING = ThreadLocal.withInitial(() -> false);

	private QuickStrike() {
	}

	/** True during a Quick Strike's second hit. */
	public static boolean isRepeating() {
		return REPEATING.get();
	}

	/**
	 * After {@code attacker}'s melee hit on {@code target} with {@code source} for {@code amount}, which
	 * {@code landed} or not: rolls the weapon's chance and lands the hit again.
	 */
	public static void afterHit(LivingEntity attacker, Entity target, ServerLevel level, DamageSource source,
			float amount, boolean landed) {
		if (!landed || !(target instanceof LivingEntity victim) || !victim.isAlive()) {
			return;
		}
		ItemStack weapon = attacker.getWeaponItem();
		Pair<LevelBasedValue, Integer> quickStrike = EnchantmentHelper.getHighestLevel(weapon,
				ModEffects.EXTRA_HIT_CHANCE);
		if (quickStrike == null) {
			return;
		}
		float chance = quickStrike.getFirst().calculate(quickStrike.getSecond());
		if (!Float.isFinite(chance)) {
			Enchanting2.LOGGER.warn("Quick Strike chance is {} at level {}; ignoring it (a datapack's value?)", chance,
					quickStrike.getSecond());
			return;
		}
		if (attacker.getRandom().nextFloat() >= chance) {
			return;
		}
		Vec3 motion = victim.getDeltaMovement();
		REPEATING.set(true);
		try {
			victim.invulnerableTime = 0;
			victim.hurtServer(level, source, amount);
		} finally {
			REPEATING.set(false);
		}
		victim.setDeltaMovement(motion);
	}
}
