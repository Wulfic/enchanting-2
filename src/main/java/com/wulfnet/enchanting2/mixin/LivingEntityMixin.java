package com.wulfnet.enchanting2.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.effect.Lifesteal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Phase 10a, Health (MC-17876): {@code readAdditionalSaveData} sets the saved health before any equipment attribute
 * is back (they're transient and return on the first tick's {@code detectEquipmentUpdates}), so it clamps to the base
 * max and a player at 34/40 rejoins at 20/40. Keep the saved value and set it again, clamped to the live max, once
 * the first equipment update has run. Only a value the load actually cut is kept.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
	/** The saved health the load clamped, until the first equipment update; NaN when there's nothing to restore. */
	@Unique
	private float enchanting2$clampedSavedHealth = Float.NaN;

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void enchanting2$keepClampedHealth(ValueInput input, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		float saved = input.getFloatOr("Health", Float.NaN);
		enchanting2$clampedSavedHealth = saved > self.getHealth() ? saved : Float.NaN;
	}

	@Inject(method = "detectEquipmentUpdates", at = @At("TAIL"))
	private void enchanting2$restoreClampedHealth(CallbackInfo ci) {
		float saved = enchanting2$clampedSavedHealth;
		if (Float.isNaN(saved)) {
			return;
		}
		enchanting2$clampedSavedHealth = Float.NaN;
		LivingEntity self = (LivingEntity) (Object) this;
		float restored = Math.min(saved, self.getMaxHealth());
		if (restored > self.getHealth()) {
			Enchanting2.LOGGER.debug("Restored {}'s saved health {} (load clamped it to {}, max now {})",
					self.getStringUUID(), restored, self.getHealth(), self.getMaxHealth());
			self.setHealth(restored);
		}
	}

	/**
	 * Phase 10b, Vampirism: measures the health (+ absorption) the hit actually took. Fabric's {@code AFTER_DAMAGE}
	 * can't serve: it skips killing blows and passes the post-armor amount, overkill included. The hit itself and the
	 * return value are untouched.
	 */
	@WrapMethod(method = "hurtServer")
	private boolean enchanting2$lifesteal(ServerLevel level, DamageSource source, float amount,
			Operation<Boolean> original) {
		LivingEntity self = (LivingEntity) (Object) this;
		float before = self.getHealth() + self.getAbsorptionAmount();
		boolean hurt = original.call(level, source, amount);
		if (hurt) {
			Lifesteal.afterHurt(self, source, before - (self.getHealth() + self.getAbsorptionAmount()));
		}
		return hurt;
	}
}
