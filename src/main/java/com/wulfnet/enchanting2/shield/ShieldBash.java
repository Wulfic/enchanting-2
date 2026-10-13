package com.wulfnet.enchanting2.shield;

import com.mojang.datafixers.util.Pair;
import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.effect.BashEffect;
import com.wulfnet.enchanting2.effect.ModEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Phase 10cCC: Steady Stance and Shield Bash, the two shield enchantments that act while the shield is up rather than
 * when it blocks. Both read the item in use, so a shield in the other hand that isn't raised does nothing.
 */
public final class ShieldBash {
	/** Slowness IV (owner). */
	static final int SLOWNESS_AMPLIFIER = 3;

	private ShieldBash() {
	}

	/** Steady Stance: the walking speed multiplier while using {@code useItem}, no slowdown with the enchantment. */
	public static float useSpeedMultiplier(ItemStack useItem, float vanilla) {
		return EnchantmentHelper.has(useItem, ModEffects.STEADY_STANCE) ? Math.max(vanilla, 1.0F) : vanilla;
	}

	/** Whether an attack click while using {@code useItem} should go through as a bash (the client's input check). */
	public static boolean canBash(ItemStack useItem) {
		return EnchantmentHelper.has(useItem, ModEffects.SHIELD_BASH);
	}

	/**
	 * After {@code player}'s attack on {@code target}: with a Shield Bash shield raised and the hit landed (the player
	 * is now the target's last attacker, and wasn't before or the last-hurt stamp moved off {@code stampBefore}), the
	 * target is knocked back along the player's facing and slowed, and the shield goes down on its cooldown as an axe's
	 * disable puts it.
	 */
	public static void afterAttack(Player player, Entity target, LivingEntity attackerBefore, int stampBefore) {
		if (!(player.level() instanceof ServerLevel) || !(target instanceof LivingEntity living)
				|| !player.isUsingItem()) {
			return;
		}
		ItemStack shield = player.getUseItem();
		Pair<BashEffect, Integer> bash = EnchantmentHelper.getHighestLevel(shield, ModEffects.SHIELD_BASH);
		boolean landed = living.getLastHurtByMob() == player
				&& (attackerBefore != player || living.getLastHurtByMobTimestamp() != stampBefore);
		if (bash == null || !landed) {
			return;
		}
		int level = bash.getSecond();
		float knockback = bash.getFirst().knockback().calculate(level);
		int slowness = Math.round(bash.getFirst().slowness().calculate(level));
		if (!Float.isFinite(knockback)) {
			Enchanting2.LOGGER.warn("Shield Bash knockback is {} at level {}; ignoring it (a datapack's value?)",
					knockback, level);
			knockback = 0.0F;
		}
		if (knockback > 0.0F) {
			float yaw = player.getYRot() * Mth.DEG_TO_RAD;
			living.knockback(knockback, Mth.sin(yaw), -Mth.cos(yaw));
			living.hurtMarked = true;
		}
		if (slowness > 0) {
			living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, slowness, SLOWNESS_AMPLIFIER), player);
		}
		player.getCooldowns().addCooldown(shield, bash.getFirst().cooldown());
		player.stopUsingItem();
	}
}
