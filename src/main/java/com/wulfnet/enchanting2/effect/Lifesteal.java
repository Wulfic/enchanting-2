package com.wulfnet.enchanting2.effect;

import com.mojang.datafixers.util.Pair;
import com.wulfnet.enchanting2.Enchanting2;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.LevelBasedValue;

/**
 * DESIGN §9 Vampirism (owner, 2026-10-09): a direct melee hit heals the attacker a fraction of the health its victim
 * actually lost, so overkill never counts. Every hit, no cooldown, any victim. A thrown trident's hit isn't direct (the
 * trident is the direct entity), so it never heals. Called by {@code LivingEntityMixin} after {@code hurtServer}.
 */
public final class Lifesteal {
	private Lifesteal() {
	}

	/** {@code lost} is the victim's health + absorption before the hit minus after it. Never throws. */
	public static void afterHurt(LivingEntity victim, DamageSource source, float lost) {
		if (!(lost > 0) || !source.isDirect() || !(source.getEntity() instanceof LivingEntity attacker)
				|| attacker == victim || !attacker.isAlive()) {
			return;
		}
		ItemStack weapon = source.getWeaponItem();
		if (weapon == null || weapon.isEmpty()) {
			return;
		}
		try {
			Pair<LevelBasedValue, Integer> lifesteal = EnchantmentHelper.getHighestLevel(weapon, ModEffects.LIFESTEAL);
			if (lifesteal == null) {
				return;
			}
			float heal = healAmount(lost, lifesteal.getFirst().calculate(lifesteal.getSecond()));
			if (heal > 0) {
				attacker.heal(heal);
			}
		} catch (RuntimeException e) {
			Enchanting2.LOGGER.error("Lifesteal failed for {} hitting {}; the hit stands without healing",
					attacker.getStringUUID(), victim.getStringUUID(), e);
		}
	}

	/** {@code lost × fraction}; 0 for a non-positive or non-finite result, so a bad datapack value never harms. */
	public static float healAmount(float lost, float fraction) {
		float heal = lost * fraction;
		return Float.isFinite(heal) && heal > 0 ? heal : 0;
	}
}
