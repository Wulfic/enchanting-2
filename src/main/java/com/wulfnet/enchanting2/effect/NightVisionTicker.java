package com.wulfnet.enchanting2.effect;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.platform.Services;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * DESIGN §9 Vision (owner, 2026-10-09): Night Vision while a {@link ModEffects#NIGHT_VISION} item is worn, players
 * only. Checked once a second. Ours lasts {@value #DURATION} ticks and is refreshed below {@value #REFRESH_BELOW}, so it
 * never reaches vanilla's last-10-seconds flicker, and costs one packet a few seconds rather than one a tick. Ours is
 * marked by being ambient (potions never are). A Night Vision with {@value #REFRESH_BELOW}+ ticks left (a potion) or an
 * infinite one is never touched; a shorter one becomes ours while the item is worn. Taking the item off removes
 * only ours.
 */
public final class NightVisionTicker {
	public static final int INTERVAL = 20;
	public static final int REFRESH_BELOW = 220;
	public static final int DURATION = 300;

	private NightVisionTicker() {
	}

	public static void register() {
		Services.EVENTS.onServerTickEnd(server -> {
			if (server.getTickCount() % INTERVAL != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				try {
					tick(player);
				} catch (RuntimeException e) {
					Enchanting2.LOGGER.error("Vision failed for {}; their Night Vision is left as it was",
							player.getStringUUID(), e);
				}
			}
		});
	}

	/** One check for {@code entity}: grant, refresh or remove our Night Vision. */
	public static void tick(LivingEntity entity) {
		MobEffectInstance current = entity.getEffect(MobEffects.NIGHT_VISION);
		if (!wearing(entity)) {
			if (current != null && isOurs(current)) {
				entity.removeEffect(MobEffects.NIGHT_VISION);
			}
			return;
		}
		if (current != null && (current.isInfiniteDuration() || current.getDuration() >= REFRESH_BELOW)) {
			return;
		}
		// A potion about to run out merges into ours: vanilla's update takes the longer duration and our ambient flag.
		entity.addEffect(ours());
	}

	public static MobEffectInstance ours() {
		return new MobEffectInstance(MobEffects.NIGHT_VISION, DURATION, 0, true, false, true);
	}

	public static boolean isOurs(MobEffectInstance effect) {
		return effect.isAmbient() && effect.getAmplifier() == 0 && !effect.isInfiniteDuration()
				&& effect.getDuration() <= DURATION;
	}

	private static boolean wearing(LivingEntity entity) {
		boolean[] found = { false };
		EnchantmentHelper.runIterationOnEquipment(entity, (holder, level, item) -> {
			if (holder.value().effects().has(ModEffects.NIGHT_VISION)) {
				found[0] = true;
			}
		});
		return found[0];
	}
}
