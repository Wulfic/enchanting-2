package com.wulfnet.enchanting2.curse;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.config.ServerConfigs;
import com.wulfnet.enchanting2.effect.ModEffects;
import com.wulfnet.enchanting2.effect.WornEffects;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Phase 10e: Curse of Weight and Curse of Hunger, which vanilla's effect components can't express. Both read our
 * components off every equipped item whose slot the enchantment names ({@code runIterationOnEquipment}), so the JSON
 * decides where a curse counts and how much each level adds.
 */
public final class CurseEffects {
	/** Weight's one movement speed modifier, whatever the number of pieces (they're summed, then capped). */
	public static final Identifier WEIGHT_MODIFIER = Identifier.fromNamespaceAndPath(Enchanting2.MOD_ID,
			"curse.weight");

	private CurseEffects() {
	}

	/**
	 * Sets or removes Weight's modifier on {@code entity}. Called after vanilla handles an equipment change (server
	 * only), so the transient modifier comes back on load with the first tick's change, like enchantment attributes.
	 */
	public static void updateWeight(LivingEntity entity) {
		AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		double sum = WornEffects.sum(entity, ModEffects.MOVEMENT_PENALTY);
		if (!Double.isFinite(sum)) {
			Enchanting2.LOGGER.warn("Curse of Weight on {} sums to {}; ignoring it (a datapack's movement_penalty?)",
					entity.getStringUUID(), sum);
			speed.removeModifier(WEIGHT_MODIFIER);
			return;
		}
		double amount = CurseMath.weightModifier(sum,
				ServerConfigs.get(level.getServer()).curses().weightMaxSlowdownPercent());
		if (amount == 0.0) {
			speed.removeModifier(WEIGHT_MODIFIER);
		} else {
			speed.addOrUpdateTransientModifier(new AttributeModifier(WEIGHT_MODIFIER, amount,
					AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	/** {@code amount} scaled by Hunger on the armor {@code player} wears; unchanged without it. */
	public static float scaleExhaustion(Player player, float amount) {
		double sum = WornEffects.sum(player, ModEffects.EXHAUSTION);
		if (sum == 0.0) {
			return amount;
		}
		if (!Double.isFinite(sum)) {
			Enchanting2.LOGGER.warn("Curse of Hunger on {} sums to {}; ignoring it (a datapack's exhaustion?)",
					player.getStringUUID(), sum);
			return amount;
		}
		return amount * CurseMath.exhaustionFactor(sum);
	}
}
