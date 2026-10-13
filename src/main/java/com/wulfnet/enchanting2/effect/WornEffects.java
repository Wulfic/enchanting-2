package com.wulfnet.enchanting2.effect;

import com.wulfnet.enchanting2.Enchanting2;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.LevelBasedValue;

/**
 * Effect components read off what an entity wears (Phase 10e curses, Phase 10cA helmet and leggings enchantments).
 * Every read goes through {@code runIterationOnEquipment}, which only visits items in a slot their enchantment names,
 * so the JSON decides where each one counts (a Clarity helmet held in a hand does nothing).
 */
public final class WornEffects {
	private WornEffects() {
	}

	/** Whether any worn enchantment carries the unit component {@code type}. */
	public static boolean has(LivingEntity entity, DataComponentType<?> type) {
		boolean[] found = {false};
		EnchantmentHelper.runIterationOnEquipment(entity, (enchantment, level, item) -> {
			if (enchantment.value().effects().has(type)) {
				found[0] = true;
			}
		});
		return found[0];
	}

	/** {@code type}'s value at each worn enchantment's level, summed; 0 without any. */
	public static double sum(LivingEntity entity, DataComponentType<LevelBasedValue> type) {
		double[] total = {0.0};
		EnchantmentHelper.runIterationOnEquipment(entity, (enchantment, level, item) -> {
			LevelBasedValue value = enchantment.value().effects().get(type);
			if (value != null) {
				total[0] += value.calculate(level);
			}
		});
		return total[0];
	}

	/**
	 * Clarity: {@code instance} as it lands on {@code entity}, shortened by every worn {@code shorten_effects} that
	 * lists its effect; {@code null} when the fractions reach 1 (refused, infinite ones too). An infinite instance below
	 * that stays infinite. Unlisted effects come back unchanged.
	 */
	public static MobEffectInstance shorten(LivingEntity entity, MobEffectInstance instance) {
		Holder<MobEffect> effect = instance.getEffect();
		double[] fraction = {0.0};
		EnchantmentHelper.runIterationOnEquipment(entity, (enchantment, level, item) -> {
			ShortenEffects shorten = enchantment.value().effects().get(ModEffects.SHORTEN_EFFECTS);
			if (shorten != null && shorten.effects().contains(effect)) {
				fraction[0] += shorten.fraction().calculate(level);
			}
		});
		if (fraction[0] == 0.0) {
			return instance;
		}
		if (!Double.isFinite(fraction[0])) {
			Enchanting2.LOGGER.warn("shorten_effects on {} sums to {} for {}; ignoring it (a datapack's fraction?)",
					entity.getStringUUID(), fraction[0], effect.getRegisteredName());
			return instance;
		}
		double remaining = WornMath.remaining(fraction[0]);
		if (remaining == 0.0) {
			return null;
		}
		int duration = instance.getDuration();
		if (remaining == 1.0 || instance.isInfiniteDuration() || duration <= 0) {
			return instance;
		}
		// Vanilla floors duration × scale, so aim half a tick above the rounded target; float error stays far below it.
		int ticks = WornMath.scaledTicks(duration, remaining);
		return instance.withScaledDuration((float) ((ticks + 0.5) / duration));
	}

	/** Sustenance, or Endurance with {@code type} = the sprint one: {@code amount} less the worn reduction. */
	public static float reduceExhaustion(Player player, DataComponentType<LevelBasedValue> type, float amount) {
		double sum = sum(player, type);
		if (sum == 0.0) {
			return amount;
		}
		if (!Double.isFinite(sum)) {
			Enchanting2.LOGGER.warn("{} on {} sums to {}; ignoring it (a datapack's value?)",
					BuiltInRegistries.ENCHANTMENT_EFFECT_COMPONENT_TYPE.getKey(type), player.getStringUUID(), sum);
			return amount;
		}
		return (float) (amount * WornMath.remaining(sum));
	}
}
