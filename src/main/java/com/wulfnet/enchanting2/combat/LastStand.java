package com.wulfnet.enchanting2.combat;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.config.ServerConfigs;
import com.wulfnet.enchanting2.effect.LastStandEffect;
import com.wulfnet.enchanting2.effect.ModEffects;
import com.wulfnet.enchanting2.platform.Attachment;
import com.wulfnet.enchanting2.platform.Attachments;
import com.wulfnet.enchanting2.platform.Services;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.jspecify.annotations.Nullable;

/**
 * Last Stand (Phase 10cFB, owner 2026-10-10 and 2026-10-12): once per cooldown, a lethal hit leaves the chestplate's
 * wearer at 1 health with brief absorption. A Totem of Undying fires first and leaves the cooldown alone; void and
 * /kill go through it; player-on-player hits only while {@code last_stand.pvp} is on. Any living entity can wear it.
 */
public final class LastStand {
	/**
	 * The game tick Last Stand can save its wearer again. Saved, kept through death (dying another way doesn't reset
	 * it), and synced to its own player for the HUD.
	 */
	public static final Attachment<Long> READY_AT = Services.ATTACHMENTS.register(new Attachments.Spec<>(
			Identifier.fromNamespaceAndPath(Enchanting2.MOD_ID, "last_stand_ready_at"), Codec.LONG, true,
			ByteBufCodecs.VAR_LONG));

	private LastStand() {
	}

	/** Loads the class, which registers the attachment. Call once from mod init. */
	public static void register() {
		Enchanting2.LOGGER.debug("Last Stand cooldown attachment: {}", READY_AT.id());
	}

	/** The worn chestplate's Last Stand and level, or null. */
	public static @Nullable Pair<LastStandEffect, Integer> worn(LivingEntity entity) {
		ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
		return chest.isEmpty() ? null : EnchantmentHelper.getHighestLevel(chest, ModEffects.LAST_STAND);
	}

	/**
	 * {@code entity} is about to die from {@code source} and no totem saved it: saves it if Last Stand can. Returns
	 * whether it did.
	 */
	public static boolean trySave(LivingEntity entity, DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		Pair<LastStandEffect, Integer> lastStand = worn(entity);
		if (lastStand == null) {
			return false;
		}
		if (entity instanceof Player && source.getEntity() instanceof Player
				&& !ServerConfigs.get(level.getServer()).lastStand().pvp()) {
			return false;
		}
		long now = level.getGameTime();
		if (now < READY_AT.getOrElse(entity, 0L)) {
			return false;
		}
		float formula = lastStand.getFirst().cooldown().calculate(lastStand.getSecond());
		if (!Float.isFinite(formula)) {
			Enchanting2.LOGGER.warn("Last Stand cooldown is {} at level {}; it doesn't save (a datapack's value?)",
					formula, lastStand.getSecond());
			return false;
		}
		entity.setHealth(1.0F);
		entity.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, lastStand.getFirst().absorptionTicks(),
				lastStand.getFirst().absorptionAmplifier()));
		READY_AT.set(entity, now + LastStandClock.cooldown(formula));
		level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ANVIL_LAND,
				entity.getSoundSource(), 0.6F, 1.6F);
		Enchanting2.LOGGER.debug("Last Stand saved {} from {}; ready again at tick {}", entity.getStringUUID(),
				source.getMsgId(), READY_AT.get(entity));
		return true;
	}

	/** Ticks until Last Stand can save {@code entity} again at game time {@code now}; 0 when ready. */
	public static long remaining(LivingEntity entity, long now) {
		return Math.max(0L, READY_AT.getOrElse(entity, 0L) - now);
	}
}
