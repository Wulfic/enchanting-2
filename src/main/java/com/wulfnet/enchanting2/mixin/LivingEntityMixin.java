package com.wulfnet.enchanting2.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.curse.CurseEffects;
import com.wulfnet.enchanting2.effect.Footing;
import com.wulfnet.enchanting2.effect.Lifesteal;
import com.wulfnet.enchanting2.effect.WornEffects;
import com.wulfnet.enchanting2.shield.ShieldEffects;
import com.wulfnet.enchanting2.shield.ShieldState;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Phase 10a, Health (MC-17876): {@code readAdditionalSaveData} sets the saved health before any equipment attribute
 * is back (they're transient and return on the first tick's {@code detectEquipmentUpdates}), so it clamps to the base
 * max and a player at 34/40 rejoins at 20/40. Keep the saved value and set it again, clamped to the live max, once
 * the first equipment update has run. Only a value the load actually cut is kept.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin implements ShieldState {
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
	 * Phase 10e, Curse of Weight: vanilla calls this (server only) when any equipment slot changed, so Weight's one
	 * summed and capped modifier is recomputed exactly when the worn pieces change, and on the first tick after a load.
	 */
	@Inject(method = "handleEquipmentChanges", at = @At("TAIL"))
	private void enchanting2$weight(Map<EquipmentSlot, ItemStack> changes, CallbackInfo ci) {
		CurseEffects.updateWeight((LivingEntity) (Object) this);
	}

	/**
	 * Phase 10cA, Clarity: the listed effects land shorter, or not at all. Server only: the client receives the already
	 * shortened instance through {@code forceAddEffect}, and shortening it again there would show the wrong time.
	 */
	@WrapMethod(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z")
	private boolean enchanting2$clarity(MobEffectInstance instance, Entity source, Operation<Boolean> original) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!(self.level() instanceof ServerLevel)) {
			return original.call(instance, source);
		}
		MobEffectInstance shortened = WornEffects.shorten(self, instance);
		return shortened != null && original.call(shortened, source);
	}

	/** As {@link #enchanting2$clarity}, for the path that skips {@code canBeAffected}. */
	@WrapMethod(method = "forceAddEffect")
	private void enchanting2$clarityForced(MobEffectInstance instance, Entity source, Operation<Void> original) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!(self.level() instanceof ServerLevel)) {
			original.call(instance, source);
			return;
		}
		MobEffectInstance shortened = WornEffects.shorten(self, instance);
		if (shortened != null) {
			original.call(shortened, source);
		}
	}

	/**
	 * Phase 10cB, Lava Walking: vanilla asks this both for the lava block's collision ({@code LiquidBlock}'s half-block
	 * stable shape, a strider's footing) and in {@code shouldTravelInFluid}, so true is standing on lava and moving at
	 * ground speed on it (owner, 2026-10-12). Strider overrides it without calling here, so striders are untouched.
	 */
	@ModifyReturnValue(method = "canStandOnFluid", at = @At("RETURN"))
	private boolean enchanting2$lavaWalking(boolean stands, FluidState fluid) {
		return stands || Footing.standsOnLava((LivingEntity) (Object) this, fluid);
	}

	/** Phase 10cB, Snow Walker: no freezing, as vanilla's {@code #freeze_immune_wearables} (leather) gives. */
	@ModifyReturnValue(method = "canFreeze", at = @At("RETURN"))
	private boolean enchanting2$snowWalker(boolean freezes) {
		return freezes && !Footing.walksOnPowderSnow((LivingEntity) (Object) this);
	}

	/** Riposte's bonus for the next melee hit, live until {@link #enchanting2$riposteUntil} (Phase 10cC). */
	@Unique
	private double enchanting2$riposteBonus;
	@Unique
	private long enchanting2$riposteUntil;
	/** The game time Bulwark can grant absorption again (Phase 10cC). */
	@Unique
	private long enchanting2$bulwarkReadyAt;

	@Override
	public void enchanting2$setRiposte(double bonus, long until) {
		enchanting2$riposteBonus = bonus;
		enchanting2$riposteUntil = until;
	}

	@Override
	public double enchanting2$useRiposte(long now) {
		double bonus = now < enchanting2$riposteUntil ? enchanting2$riposteBonus : 0.0;
		enchanting2$riposteBonus = 0.0;
		return bonus;
	}

	@Override
	public long enchanting2$bulwarkReadyAt() {
		return enchanting2$bulwarkReadyAt;
	}

	@Override
	public void enchanting2$setBulwarkReadyAt(long tick) {
		enchanting2$bulwarkReadyAt = tick;
	}

	/**
	 * Phase 10cC, Spiked, Bulwark and Riposte: vanilla damages the shield here whether or not anything was blocked, and
	 * the hit can break it, so its enchantments are read first and acted on after.
	 */
	@WrapOperation(method = "applyItemBlocking", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/component/BlocksAttacks;hurtBlockingItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/InteractionHand;F)V"))
	private void enchanting2$shieldBlocked(BlocksAttacks blocksAttacks, Level level, ItemStack shield,
			LivingEntity blocker, InteractionHand hand, float blocked, Operation<Void> original,
			@Local(argsOnly = true) ServerLevel serverLevel, @Local(argsOnly = true) DamageSource source) {
		ShieldEffects.Snapshot effects = ShieldEffects.read(shield);
		original.call(blocksAttacks, level, shield, blocker, hand, blocked);
		ShieldEffects.afterBlock(blocker, serverLevel, source, blocked, effects);
	}

	/** Phase 10cC, Riposte: the attacker's next melee hit after a block deals more, and uses the bonus up. */
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float enchanting2$riposte(float amount, @Local(argsOnly = true) ServerLevel level,
			@Local(argsOnly = true) DamageSource source) {
		return ShieldEffects.riposte(level, source, amount);
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
