package com.wulfnet.enchanting2.shield;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.effect.BlockAbsorption;
import com.wulfnet.enchanting2.effect.ModEffects;
import com.wulfnet.enchanting2.effect.RiposteBonus;
import com.wulfnet.enchanting2.effect.WornMath;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.LevelBasedValue;

/**
 * Phase 10cC: what a shield's enchantments do when it blocks. Everything reads the blocking stack alone (never other
 * equipment), and only before vanilla damages it, since a block can break the shield.
 */
public final class ShieldEffects {
	private ShieldEffects() {
	}

	/** One shield's block effects, summed over its enchantments; durations take the longest. */
	public record Snapshot(double retaliation, double absorption, int absorptionTicks, int absorptionCooldown,
			double riposteBonus, int riposteWindow) {
		static final Snapshot NONE = new Snapshot(0.0, 0.0, 0, 0, 0.0, 0);

		boolean isEmpty() {
			return retaliation == 0.0 && absorption == 0.0 && riposteBonus == 0.0;
		}
	}

	/** Reads {@code shield}'s block effects. {@link Snapshot#NONE} without any. */
	public static Snapshot read(ItemStack shield) {
		double[] sums = new double[3];
		int[] ticks = new int[3];
		EnchantmentHelper.runIterationOnItem(shield, (enchantment, level) -> {
			LevelBasedValue retaliation = enchantment.value().effects().get(ModEffects.BLOCK_RETALIATION);
			if (retaliation != null) {
				sums[0] += retaliation.calculate(level);
			}
			BlockAbsorption absorption = enchantment.value().effects().get(ModEffects.BLOCK_ABSORPTION);
			if (absorption != null) {
				sums[1] += absorption.amount().calculate(level);
				ticks[0] = Math.max(ticks[0], absorption.duration());
				ticks[1] = Math.max(ticks[1], absorption.cooldown());
			}
			RiposteBonus riposte = enchantment.value().effects().get(ModEffects.RIPOSTE);
			if (riposte != null) {
				sums[2] += riposte.damageBonus().calculate(level);
				ticks[2] = Math.max(ticks[2], riposte.window());
			}
		});
		Snapshot snapshot = new Snapshot(sums[0], sums[1], ticks[0], ticks[1], sums[2], ticks[2]);
		return snapshot.isEmpty() ? Snapshot.NONE : snapshot;
	}

	/**
	 * After vanilla resolved a block of {@code blocked} damage from {@code source}: Spiked hurts a melee attacker,
	 * Bulwark grants absorption off cooldown, Riposte arms the blocker's next melee hit.
	 */
	public static void afterBlock(LivingEntity blocker, ServerLevel level, DamageSource source, float blocked,
			Snapshot shield) {
		if (blocked <= 0.0F || shield.isEmpty()) {
			return;
		}
		if (!Double.isFinite(shield.retaliation()) || !Double.isFinite(shield.absorption())
				|| !Double.isFinite(shield.riposteBonus())) {
			Enchanting2.LOGGER.warn("A shield blocking for {} has a non-finite effect ({}); ignoring it (a datapack's?)",
					blocker.getStringUUID(), shield);
			return;
		}
		spiked(blocker, level, source, shield.retaliation());
		ShieldState state = (ShieldState) blocker;
		long now = level.getGameTime();
		if (shield.absorption() > 0.0 && now >= state.enchanting2$bulwarkReadyAt()) {
			int amplifier = ShieldMath.bulwarkAmplifier(shield.absorption());
			if (amplifier >= 0) {
				blocker.addEffect(new MobEffectInstance(BulwarkEffect.holder(), shield.absorptionTicks(), amplifier,
						false, false, false));
				state.enchanting2$setBulwarkReadyAt(now + shield.absorptionCooldown());
			}
		}
		if (shield.riposteBonus() > 0.0) {
			state.enchanting2$setRiposte(shield.riposteBonus(), now + shield.riposteWindow());
		}
	}

	/**
	 * Spiked: Thorns for shields (owner). Melee only: a projectile's hit never retaliates, and neither does a thorns
	 * hit, so two Spiked shields can't hurt each other back and forth.
	 */
	private static void spiked(LivingEntity blocker, ServerLevel level, DamageSource source, double damage) {
		if (damage <= 0.0 || source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypes.THORNS)
				|| !(source.getDirectEntity() instanceof LivingEntity attacker) || attacker == blocker) {
			return;
		}
		attacker.hurtServer(level, level.damageSources().thorns(blocker), (float) damage);
	}

	/** Riposte: {@code amount} for a hit from {@code source}, raised and used up when it's its attacker's next melee. */
	public static float riposte(ServerLevel level, DamageSource source, float amount) {
		if (!(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker
				|| !isMelee(source)) {
			return amount;
		}
		double bonus = ((ShieldState) attacker).enchanting2$useRiposte(level.getGameTime());
		return bonus > 0.0 ? ShieldMath.withBonus(amount, bonus) : amount;
	}

	private static boolean isMelee(DamageSource source) {
		return source.is(DamageTypeTags.IS_PLAYER_ATTACK) || source.is(DamageTypes.PLAYER_ATTACK)
				|| source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)
				|| source.is(DamageTypes.SPEAR) || source.is(DamageTypes.MACE_SMASH);
	}

	/** Steadfast: an axe's {@code seconds} of disabling for {@code shield}, less its summed reduction (III: none). */
	public static float disableSeconds(ItemStack shield, float seconds) {
		double sum = sumOnItem(shield, ModEffects.SHIELD_DISABLE_REDUCTION);
		if (sum == 0.0) {
			return seconds;
		}
		if (!Double.isFinite(sum)) {
			Enchanting2.LOGGER.warn("shield_disable_reduction sums to {} on {}; ignoring it (a datapack's value?)", sum,
					BuiltInRegistries.ITEM.getKey(shield.getItem()));
			return seconds;
		}
		return (float) (seconds * WornMath.remaining(sum));
	}

	private static double sumOnItem(ItemStack stack, DataComponentType<LevelBasedValue> type) {
		double[] total = {0.0};
		EnchantmentHelper.runIterationOnItem(stack, (enchantment, level) -> {
			LevelBasedValue value = enchantment.value().effects().get(type);
			if (value != null) {
				total[0] += value.calculate(level);
			}
		});
		return total[0];
	}
}
