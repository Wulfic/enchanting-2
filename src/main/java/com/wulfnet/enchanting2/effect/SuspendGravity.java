package com.wulfnet.enchanting2.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.wulfnet.enchanting2.Enchanting2;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.minecraft.world.phys.Vec3;

/**
 * DESIGN §9 TrueShot Aura (owner, 2026-10-09): an arrow from the bow flies without gravity for {@code ticks}, then
 * falls and despawns like any arrow. Runs from vanilla's {@code projectile_spawned}. {@code NoGravity} is synced entity
 * data, so the client draws the straight path; the countdown is a persistent attachment, so an arrow saved mid-flight
 * resumes it on load instead of floating forever. An arrow that is already {@code NoGravity} (a command, another mod)
 * is left alone, and only gravity we took away is given back.
 */
public record SuspendGravity(LevelBasedValue ticks) implements EnchantmentEntityEffect {
	public static final MapCodec<SuspendGravity> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
			.group(LevelBasedValue.CODEC.fieldOf("ticks").forGetter(SuspendGravity::ticks))
			.apply(instance, SuspendGravity::new));

	/** Ticks left without gravity. Only on arrows whose gravity we took away. */
	public static final AttachmentType<Integer> TICKS_LEFT = AttachmentRegistry.create(
			Identifier.fromNamespaceAndPath(Enchanting2.MOD_ID, "gravity_ticks"),
			builder -> builder.persistent(Codec.INT));

	/** Loads the class, which registers the attachment, during mod init. */
	static void register() {
		Enchanting2.LOGGER.debug("Gravity countdown attachment: {}", TICKS_LEFT.identifier());
	}

	@Override
	public void apply(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 origin) {
		suspend(entity, Math.round(ticks.calculate(enchantmentLevel)));
	}

	@Override
	public MapCodec<SuspendGravity> codec() {
		return CODEC;
	}

	/** Takes {@code arrow}'s gravity away for {@code ticks}. Not an arrow, no ticks, or already gravity-free: nothing. */
	public static void suspend(Entity entity, int ticks) {
		if (!(entity instanceof AbstractArrow) || ticks <= 0 || entity.isNoGravity()) {
			Enchanting2.LOGGER.debug("Not suspending gravity of {} for {} ticks (not an arrow, or already NoGravity)",
					entity, ticks);
			return;
		}
		entity.setNoGravity(true);
		entity.setAttached(TICKS_LEFT, ticks);
	}

	/** One server tick of the countdown; at 0 the arrow gets its gravity back. Called by {@code AbstractArrowMixin}. */
	public static void tick(AbstractArrow arrow) {
		Integer left = arrow.getAttached(TICKS_LEFT);
		if (left == null) {
			return;
		}
		if (left > 1) {
			arrow.setAttached(TICKS_LEFT, left - 1);
			return;
		}
		arrow.removeAttached(TICKS_LEFT);
		arrow.setNoGravity(false);
	}
}
